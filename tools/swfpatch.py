"""Add notification hooks to a Flash (AS2) game without changing its logic.

  * after selected root frame labels: a new DoAction tag  ->  fscommand("ml", "frame:<label>")
  * at the start of selected named functions               ->  fscommand("ml", "fn:<name>")

The function hooks are inserted inside existing bytecode, so every size / branch offset that
spans an insertion point is fixed up. Usage: swfpatch.py in.swf out.swf
"""
import struct, sys, zlib

FRAMES = {'titleFrame','mainFrame','fuelFrame','sellFrame','upgradeFrame','itemFrame','repairFrame',
          'saveFrame','inventoryFrame','helpFrame','transmissionFrame','deathFrame'}
FUNCS = {'showFuelDialogue','buyFuelDone','showSellDialogue','sellDone','showRepairDialogue','hideRepairDialogue',
         'showUpgradeDialogue','hideUpgradeDialogue','purchaseItem','purchaseItemFail','preSaveGame','beginSave',
         'exitSave','preLoadGame','cancelLoadGame','exitInventory','loadGame','saveGame','retrySave','initializeOptions'}

def fs_action(arg):
    url = b'FSCommand:ml\0'; tgt = arg.encode() + b'\0'
    body = url + tgt
    return bytes([0x83]) + struct.pack('<H', len(body)) + body

# ---- tiny AVM1 code builder for the HUD hook ----
def A_push_str(t): b = b'\0' + t.encode('utf-8') + b'\0'; return bytes([0x96]) + struct.pack('<H', len(b)) + b
def A_push_int(n): b = b'\7' + struct.pack('<i', n); return bytes([0x96]) + struct.pack('<H', len(b)) + b
GETVAR, GETMEM, ADD2, CALLM = b'\x1c', b'\x4e', b'\x47', b'\x52'
def E_var(name): return A_push_str(name) + GETVAR
def E_mem(obj, name): return obj + A_push_str(name) + GETMEM
def E_idx(obj, arr, idx): return E_mem(E_var(obj), arr) + E_mem(E_var(obj), idx) + GETMEM   # obj.arr[obj.idx]
def E_call(objcode, method, args=()):
    code = b''.join(args) + A_push_int(len(args)) + objcode + A_push_str(method) + CALLM
    return code
def hud_hook():
    atv = E_var('atv')
    fields = [
        E_mem(E_var('p1'), 'cash'), E_mem(atv, 'fuel'), E_idx('atv', 'fuelTankCapacity', 'fuelTank'),
        E_mem(atv, 'hp'), E_idx('atv', 'hullHp', 'hull'), E_var('depth'),
        E_idx('atv', 'baySize', 'bay'), E_call(atv, 'getBaySpace'), E_call(atv, 'getMass'),
        E_call(E_mem(atv, 'bayContents'), 'join', [A_push_str(',')]),
        E_call(E_mem(atv, 'usableItem'), 'join', [A_push_str(',')]),
        E_idx('atv', 'drillNames', 'drill'), E_idx('atv', 'hullNames', 'hull'), E_idx('atv', 'engineNames', 'engine'),
        E_idx('atv', 'fuelTankNames', 'fuelTank'), E_idx('atv', 'radiatorNames', 'radiator'), E_idx('atv', 'bayNames', 'bay'),
        E_mem(E_var('p1'), 'score'),
    ]
    code = A_push_str('FSCommand:ml') + A_push_str('hud:')
    for i, f in enumerate(fields):
        if i: code += A_push_str('|') + ADD2
        code += f + ADD2
    code += bytes([0x9A]) + struct.pack('<H', 1) + b'\0'      # getURL2 -> fscommand("ml", "hud:...")
    return code

def cstr(b, i):
    j = b.index(b'\0', i); return b[i:j], j + 1

def parse_actions(code):
    """Return list of (pos, op, data_start, data_len)."""
    acts = []; i = 0
    while i < len(code):
        op = code[i]; pos = i; i += 1
        if op == 0: acts.append((pos, 0, i, 0)); break
        ln = 0
        if op >= 0x80: ln = struct.unpack_from('<H', code, i)[0]; i += 2
        acts.append((pos, op, i, ln)); i += ln
    return acts

def patch_code(code, log):
    code = bytearray(code)
    while True:   # one insertion per pass (re-parse after each)
        acts = parse_actions(bytes(code))
        target = None
        for pos, op, ds, ln in acts:
            if op in (0x9B, 0x8E):
                name, _ = cstr(code, ds)
                name = name.decode('utf-8', 'replace')
                body_start = ds + ln
                hook = hud_hook() if name == 'updateHUD' else fs_action('fn:' + name)
                if (name in FUNCS or name == 'updateHUD') and code[body_start:body_start + len(hook)] != hook:
                    target = (name, body_start, hook); break
        if not target: return bytes(code)
        name, I, ins = target; n = len(ins)
        if any(op in (0x8F, 0x8A, 0x8D) for _, op, _, _ in acts):
            raise SystemExit(f'unsupported construct near {name}')
        # fix sizes and branches
        for pos, op, ds, ln in acts:
            end = ds + ln
            if op == 0x9B or op == 0x8E:   # function definitions containing I
                fn, j = cstr(code, ds)
                size_off = ds + ln - 2
                size = struct.unpack_from('<H', code, size_off)[0]
                if end <= I <= end + size:  # body range (insertion at body start counts)
                    struct.pack_into('<H', code, size_off, size + n)
            elif op == 0x94:                # with { ... } block
                size = struct.unpack_from('<H', code, ds)[0]
                if end <= I < end + size:
                    struct.pack_into('<H', code, ds, size + n)
            elif op in (0x99, 0x9D):        # jump / if
                off = struct.unpack_from('<h', code, ds)[0]
                tgt = end + off
                if end <= I < tgt or (off > 0 and tgt == I and False):
                    struct.pack_into('<h', code, ds, off + n)
                elif tgt <= I < end:
                    struct.pack_into('<h', code, ds, off - n)
        code[I:I] = ins
        log.append(f'hook fn:{name}')

def rewrite_tags(buf, start, end, root, log):
    out = bytearray(); i = start; pending = None
    while i < end:
        h = struct.unpack_from('<H', buf, i)[0]; hs = i; i += 2
        code, ln = h >> 6, h & 0x3F
        if ln == 0x3F: ln = struct.unpack_from('<I', buf, i)[0]; i += 4
        body = bytes(buf[i:i+ln]); i += ln
        if code == 12:
            body = patch_code(body, log)
        elif code == 59:
            body = body[:2] + patch_code(body[2:], log)
        elif code == 39:
            sid, fc = struct.unpack_from('<HH', body)
            inner = rewrite_tags(body, 4, len(body), False, log)
            body = body[:4] + inner
        out += tag(code, body)
        if root and code == 43:
            label, _ = cstr(body, 0); label = label.decode('utf-8', 'replace')
            if label in FRAMES:
                out += tag(12, fs_action('frame:' + label) + b'\0')
                log.append(f'hook frame:{label}')
    return bytes(out)

def tag(code, body):
    if len(body) < 0x3F and code not in (39,):
        return struct.pack('<H', (code << 6) | len(body)) + body
    return struct.pack('<HI', (code << 6) | 0x3F, len(body)) + body

src = open(sys.argv[1], 'rb').read()
if src[:3] == b'CWS': data = src[:8] + zlib.decompress(src[8:])
else: data = src
nbits = data[8] >> 3; hdr = 8 + (5 + 4 * nbits + 7) // 8 + 4
log = []
tags_out = rewrite_tags(data, hdr, len(data), True, log)
out = bytearray(data[:hdr]) + tags_out
struct.pack_into('<I', out, 4, len(out))
open(sys.argv[2], 'wb').write(b'CWS' + out[3:8] + zlib.compress(bytes(out[8:]), 9))
print('\n'.join(sorted(set(log))), f'\n{len(log)} hooks')
