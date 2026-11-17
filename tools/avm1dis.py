"""Minimal AVM1 disassembler: dumps DoAction / DoInitAction blocks (incl. inside sprites)."""
import struct, sys

OPS = {0x04:'nextFrame',0x05:'prevFrame',0x06:'play',0x07:'stop',0x0A:'add',0x0B:'subtract',0x0C:'multiply',0x0D:'divide',
0x0E:'equals',0x0F:'less',0x10:'and',0x11:'or',0x12:'not',0x13:'stringEquals',0x14:'stringLength',0x17:'pop',0x18:'toInteger',
0x1C:'getVariable',0x1D:'setVariable',0x20:'setTarget2',0x21:'stringAdd',0x22:'getProperty',0x23:'setProperty',0x24:'cloneSprite',
0x26:'trace',0x34:'getTime',0x3A:'delete',0x3B:'delete2',0x3C:'defineLocal',0x3D:'callFunction',0x3E:'return',0x3F:'modulo',
0x40:'newObject',0x41:'defineLocal2',0x42:'initArray',0x43:'initObject',0x44:'typeof',0x46:'enumerate',0x47:'add2',0x48:'less2',
0x49:'equals2',0x4A:'toNumber',0x4B:'toString',0x4C:'pushDuplicate',0x4D:'stackSwap',0x4E:'getMember',0x4F:'setMember',
0x50:'increment',0x51:'decrement',0x52:'callMethod',0x53:'newMethod',0x54:'instanceOf',0x55:'enumerate2',0x60:'bitAnd',0x61:'bitOr',
0x62:'bitXor',0x63:'bitLShift',0x64:'bitRShift',0x65:'bitURShift',0x66:'strictEquals',0x67:'greater',0x68:'stringGreater',0x69:'extends',
0x81:'gotoFrame',0x83:'getURL',0x87:'storeRegister',0x88:'constantPool',0x8A:'waitForFrame',0x8B:'setTarget',0x8C:'gotoLabel',
0x8D:'waitForFrame2',0x8E:'defineFunction2',0x8F:'try',0x94:'with',0x96:'push',0x99:'jump',0x9A:'getURL2',0x9B:'defineFunction',
0x9D:'if',0x9E:'call',0x9F:'gotoFrame2',0x2A:'throw',0x2B:'castOp',0x2C:'implementsOp',0x45:'targetPath'}

def cstr(b, i):
    j = b.index(b'\0', i); return b[i:j].decode('latin1'), j + 1

def dis(code, out, base=''):
    pool = []; i = 0
    while i < len(code):
        op = code[i]; pos = i; i += 1
        if op == 0: out.append(f'{base}{pos:6d}: end'); break
        data = b''
        if op >= 0x80:
            ln = struct.unpack_from('<H', code, i)[0]; i += 2; data = code[i:i+ln]; i += ln
        name = OPS.get(op, f'op{op:02x}'); arg = ''
        if op == 0x88:
            n = struct.unpack_from('<H', data)[0]; j = 2; pool = []
            for _ in range(n): s, j = cstr(data, j); pool.append(s)
            arg = f'[{n} strings]'
        elif op == 0x96:
            j = 0; vals = []
            while j < len(data):
                t = data[j]; j += 1
                if t == 0: s, j = cstr(data, j); vals.append(repr(s))
                elif t == 1: vals.append(str(struct.unpack_from('<f', data, j)[0])); j += 4
                elif t == 2: vals.append('null')
                elif t == 3: vals.append('undefined')
                elif t == 4: vals.append(f'r{data[j]}'); j += 1
                elif t == 5: vals.append('true' if data[j] else 'false'); j += 1
                elif t == 6: hi, lo = struct.unpack_from('<II', data, j); vals.append(str(struct.unpack('<d', struct.pack('<II', lo, hi))[0])); j += 8
                elif t == 7: vals.append(str(struct.unpack_from('<i', data, j)[0])); j += 4
                elif t == 8: vals.append(repr(pool[data[j]]) if data[j] < len(pool) else f'c{data[j]}'); j += 1
                elif t == 9: k = struct.unpack_from('<H', data, j)[0]; vals.append(repr(pool[k]) if k < len(pool) else f'c{k}'); j += 2
                else: vals.append('?'); break
            arg = ', '.join(vals)
        elif op in (0x99, 0x9D):
            off = struct.unpack_from('<h', data)[0]; arg = f'-> {i + off}'
        elif op == 0x83:
            u, j = cstr(data, 0); t, j = cstr(data, j); arg = f'{u!r} {t!r}'
        elif op == 0x8C:
            arg = repr(cstr(data, 0)[0])
        elif op == 0x81:
            arg = str(struct.unpack_from('<H', data)[0])
        elif op == 0x9B:
            fn, j = cstr(data, 0); n = struct.unpack_from('<H', data, j)[0]; j += 2
            params = []
            for _ in range(n): s, j = cstr(data, j); params.append(s)
            arg = f'{fn}({", ".join(params)}) size={struct.unpack_from("<H", data, j)[0]}'
        elif op == 0x8E:
            fn, j = cstr(data, 0); n = struct.unpack_from('<H', data, j)[0]; j += 2; j += 1 + 2
            params = []
            for _ in range(n): j += 1; s, j = cstr(data, j); params.append(s)
            arg = f'{fn}({", ".join(params)}) size={struct.unpack_from("<H", data, j)[0]}'
        elif op == 0x87:
            arg = f'r{data[0]}'
        out.append(f'{base}{pos:6d}: {name} {arg}')

def tags(buf, start, end, out, where):
    i = start
    while i < end:
        h = struct.unpack_from('<H', buf, i)[0]; i += 2
        code, ln = h >> 6, h & 0x3F
        if ln == 0x3F: ln = struct.unpack_from('<I', buf, i)[0]; i += 4
        body = buf[i:i+ln]
        if code == 12: out.append(f'=== DoAction in {where}'); dis(body, out)
        elif code == 59:
            sid = struct.unpack_from('<H', body)[0]; out.append(f'=== DoInitAction sprite {sid}'); dis(body[2:], out)
        elif code == 39:
            sid = struct.unpack_from('<H', body)[0]; tags(buf, i + 4, i + ln, out, f'sprite {sid}')
        i += ln
        if code == 0 and where == 'root': pass

data = open(sys.argv[1], 'rb').read()
nbits = data[8] >> 3; hdr = 8 + (5 + 4 * nbits + 7) // 8 + 4
out = []; tags(data, hdr, len(data), out, 'root')
open(sys.argv[2], 'w').write('\n'.join(out))
print(len(out), 'lines')
