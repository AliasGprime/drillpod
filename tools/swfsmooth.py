"""Marks every non-smoothed bitmap fill (0x42/0x43) of the initial fill style arrays as smoothed (0x40/0x41)."""
import struct, sys, zlib
src = open(sys.argv[1], 'rb').read()
u = bytearray(src[:8] + zlib.decompress(src[8:]) if src[:1] == b'C' else src)
nb = u[8] >> 3; hdr = 8 + (5 + 4 * nb + 7) // 8 + 4
n = 0
class BR:
    def __init__(s, b, p): s.b, s.p, s.bit = b, p, 0
    def ub(s, k):
        v = 0
        for _ in range(k):
            v = (v << 1) | ((s.b[s.p] >> (7 - s.bit)) & 1); s.bit += 1
            if s.bit == 8: s.bit = 0; s.p += 1
        return v
    def align(s):
        if s.bit: s.bit = 0; s.p += 1
def mat(r):
    if r.ub(1): k = r.ub(5); r.ub(k); r.ub(k)
    if r.ub(1): k = r.ub(5); r.ub(k); r.ub(k)
    k = r.ub(5); r.ub(k); r.ub(k); r.align()
def walk(a, b):
    global n
    i = a
    while i < b:
        h = struct.unpack_from('<H', u, i)[0]; i += 2; code, ln = h >> 6, h & 63
        if ln == 63: ln = struct.unpack_from('<I', u, i)[0]; i += 4
        if code in (2, 22, 32, 83):
            r = BR(u, i + 2); k = r.ub(5); [r.ub(k) for _ in range(4)]; r.align()
            if code == 83: k = r.ub(5); [r.ub(k) for _ in range(4)]; r.align(); r.p += 1
            c = u[r.p]; r.p += 1
            if c == 0xFF and code != 2: c = struct.unpack_from('<H', u, r.p)[0]; r.p += 2
            for _ in range(c):
                t = u[r.p]
                if t in (0x42, 0x43): u[r.p] = t - 2; n += 1
                r.p += 1
                if t == 0: r.p += 4 if code in (32, 83) else 3
                elif t in (0x10, 0x12, 0x13):
                    mat(r); g = u[r.p]; r.p += 1; r.p += (g & 15) * (5 if code in (32, 83) else 4)
                    if t == 0x13: r.p += 2
                elif t in (0x40, 0x41, 0x42, 0x43): r.p += 2; mat(r)
                else: break
        if code == 39: walk(i + 4, i + ln)
        i += ln
walk(hdr, len(u))
open(sys.argv[2], 'wb').write(b'CWS' + bytes(u[3:8]) + zlib.compress(bytes(u[8:]), 9))
print(n, 'fills smoothed')
