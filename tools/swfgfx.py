import struct, sys, zlib, collections
d = open(sys.argv[1], 'rb').read()
u = d[:8] + zlib.decompress(d[8:]) if d[:1] == b'C' else d
nb = u[8] >> 3; hdr = 8 + (5 + 4 * nb + 7) // 8 + 4
names = {6:'DefineBits',21:'DefineBitsJPEG2',35:'DefineBitsJPEG3',20:'DefineBitsLossless',36:'DefineBitsLossless2',
         2:'DefineShape',22:'DefineShape2',32:'DefineShape3',83:'DefineShape4'}
cnt = collections.Counter(); sizes = []; fills = collections.Counter(); bmpfill_ids = collections.Counter()
class BR:
    def __init__(s, b, p): s.b, s.p, s.bit = b, p, 0
    def ub(s, n):
        v = 0
        for _ in range(n):
            v = (v << 1) | ((s.b[s.p] >> (7 - s.bit)) & 1); s.bit += 1
            if s.bit == 8: s.bit = 0; s.p += 1
        return v
    def align(s):
        if s.bit: s.bit = 0; s.p += 1
def skip_matrix(r):
    if r.ub(1): n = r.ub(5); r.ub(n); r.ub(n)
    if r.ub(1): n = r.ub(5); r.ub(n); r.ub(n)
    n = r.ub(5); r.ub(n); r.ub(n); r.align()
def walk(buf, a, b):
    i = a
    while i < b:
        h = struct.unpack_from('<H', buf, i)[0]; i += 2; code, ln = h >> 6, h & 63
        if ln == 63: ln = struct.unpack_from('<I', buf, i)[0]; i += 4
        body = buf[i:i+ln]
        if code in names: cnt[names[code]] += 1
        if code in (20, 36):
            fmt, w, hh = body[2], *struct.unpack_from('<HH', body, 3); sizes.append((w, hh))
        if code in (6, 21, 35):
            # find SOF0 for dimensions
            j = body.find(b'\xff\xc0'); 
            if j > 0: hh, w = struct.unpack_from('>HH', body, j + 5); sizes.append((w, hh))
        if code in (2, 22, 32, 83):
            try:
                r = BR(body, 2); n = r.ub(5); r.ub(n); r.ub(n); r.ub(n); r.ub(n); r.align()
                if code == 83:
                    n = r.ub(5); r.ub(n); r.ub(n); r.ub(n); r.ub(n); r.align(); r.p += 1
                c = body[r.p]; r.p += 1
                if c == 0xFF and code != 2: c = struct.unpack_from('<H', body, r.p)[0]; r.p += 2
                for _ in range(c):
                    t = body[r.p]; r.p += 1; fills[hex(t)] += 1
                    if t == 0: r.p += 4 if code == 32 or code == 83 else 3
                    elif t in (0x10, 0x12, 0x13):
                        skip_matrix(r); g = body[r.p]; r.p += 1
                        r.p += (g & 15) * (5 if code in (32, 83) else 4)
                        if t == 0x13: r.p += 2
                    elif t in (0x40, 0x41, 0x42, 0x43):
                        bmpfill_ids[struct.unpack_from('<H', body, r.p)[0]] += 1; r.p += 2; skip_matrix(r)
                    else: break
            except Exception as e: pass
        if code == 39: walk(buf, i + 4, i + ln)
        i += ln
walk(u, hdr, len(u))
print(dict(cnt))
print('bitmaps:', len(sizes), 'largest:', sorted(sizes, key=lambda s: -s[0]*s[1])[:8])
print('fill style types:', dict(fills), '(0x40/41 = smoothed bitmap, 0x42/43 = NOT smoothed)')
