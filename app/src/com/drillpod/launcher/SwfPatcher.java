package com.drillpod.launcher;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Adds small notification hooks to the user's own copy of the game, on the device, at import
 * time (the app ships no game content). Only additions are made; the game logic is unchanged:
 *
 *  - after selected root frame labels: a new DoAction tag -> fscommand("ml", "frame:<label>")
 *  - at the start of selected named functions           -> fscommand("ml", "fn:<name>")
 *  - at the start of updateHUD                            -> fscommand("ml", "hud:<live values>")
 *
 * Function hooks are inserted inside existing bytecode, so every function size, "with" block
 * size and branch offset spanning an insertion point is fixed up.
 */
public final class SwfPatcher {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    static final Set<String> FRAMES = new HashSet<>(Arrays.asList(
            "titleFrame", "mainFrame", "fuelFrame", "sellFrame", "upgradeFrame", "itemFrame", "repairFrame",
            "saveFrame", "inventoryFrame", "helpFrame", "transmissionFrame", "deathFrame"));
    static final Set<String> FUNCS = new HashSet<>(Arrays.asList(
            "showFuelDialogue", "buyFuelDone", "showSellDialogue", "sellDone", "showRepairDialogue", "hideRepairDialogue",
            "showUpgradeDialogue", "hideUpgradeDialogue", "purchaseItem", "purchaseItemFail", "preSaveGame", "beginSave",
            "exitSave", "preLoadGame", "cancelLoadGame", "exitInventory", "loadGame", "saveGame", "retrySave", "initializeOptions"));

    public static int hooks;

    // ------------------------------------------------------------------ byte helpers
    static final class Buf {
        byte[] b = new byte[1024]; int n = 0;
        void put(int v) { if (n == b.length) b = Arrays.copyOf(b, n * 2); b[n++] = (byte) v; }
        void put(byte[] a) { for (byte x : a) put(x); }
        void put(byte[] a, int off, int len) { for (int i = 0; i < len; i++) put(a[off + i]); }
        void u16(int v) { put(v & 0xFF); put((v >> 8) & 0xFF); }
        void u32(int v) { u16(v & 0xFFFF); u16((v >>> 16) & 0xFFFF); }
        byte[] bytes() { return Arrays.copyOf(b, n); }
    }
    static int u16(byte[] b, int i) { return (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8); }
    static int s16(byte[] b, int i) { return (short) u16(b, i); }
    static int u32(byte[] b, int i) { return u16(b, i) | (u16(b, i + 2) << 16); }
    static void w16(byte[] b, int i, int v) { b[i] = (byte) v; b[i + 1] = (byte) (v >> 8); }
    static int strEnd(byte[] b, int i) { while (b[i] != 0) i++; return i; }
    static String cstr(byte[] b, int i) { return new String(b, i, strEnd(b, i) - i, UTF8); }
    static byte[] cat(byte[]... parts) { Buf o = new Buf(); for (byte[] p : parts) o.put(p); return o.bytes(); }

    // ------------------------------------------------------------------ AVM1 builders
    static byte[] fsAction(String arg) {
        byte[] url = "FSCommand:ml".getBytes(UTF8), tgt = arg.getBytes(UTF8);
        Buf o = new Buf(); o.put(0x83); o.u16(url.length + tgt.length + 2);
        o.put(url); o.put(0); o.put(tgt); o.put(0);
        return o.bytes();
    }
    static byte[] pushStr(String t) {
        byte[] s = t.getBytes(UTF8); Buf o = new Buf(); o.put(0x96); o.u16(s.length + 2); o.put(0); o.put(s); o.put(0); return o.bytes();
    }
    static byte[] pushInt(int v) { Buf o = new Buf(); o.put(0x96); o.u16(5); o.put(7); o.u32(v); return o.bytes(); }
    static final byte[] GETVAR = {0x1C}, GETMEM = {0x4E}, ADD2 = {0x47}, CALLM = {0x52};
    static byte[] var(String n) { return cat(pushStr(n), GETVAR); }
    static byte[] mem(byte[] obj, String n) { return cat(obj, pushStr(n), GETMEM); }
    static byte[] idx(String obj, String arr, String index) { return cat(mem(var(obj), arr), mem(var(obj), index), GETMEM); }
    static byte[] call(byte[] obj, String method, byte[]... args) {
        Buf o = new Buf(); for (byte[] a : args) o.put(a);
        o.put(pushInt(args.length)); o.put(obj); o.put(pushStr(method)); o.put(CALLM); return o.bytes();
    }
    static byte[] hudHook() {
        byte[] atv = var("atv");
        byte[][] f = {
            mem(var("p1"), "cash"), mem(atv, "fuel"), idx("atv", "fuelTankCapacity", "fuelTank"),
            mem(atv, "hp"), idx("atv", "hullHp", "hull"), var("depth"),
            idx("atv", "baySize", "bay"), call(atv, "getBaySpace"), call(atv, "getMass"),
            call(mem(atv, "bayContents"), "join", pushStr(",")),
            call(mem(atv, "usableItem"), "join", pushStr(",")),
            idx("atv", "drillNames", "drill"), idx("atv", "hullNames", "hull"), idx("atv", "engineNames", "engine"),
            idx("atv", "fuelTankNames", "fuelTank"), idx("atv", "radiatorNames", "radiator"), idx("atv", "bayNames", "bay"),
            mem(var("p1"), "score")
        };
        Buf o = new Buf(); o.put(pushStr("FSCommand:ml")); o.put(pushStr("hud:"));
        for (int i = 0; i < f.length; i++) {
            if (i > 0) { o.put(pushStr("|")); o.put(ADD2); }
            o.put(f[i]); o.put(ADD2);
        }
        o.put(0x9A); o.u16(1); o.put(0);            // getURL2 -> fscommand("ml", "hud:...")
        return o.bytes();
    }

    // ------------------------------------------------------------------ bytecode patching
    /** Returns {pos, op, dataStart, dataLen} for each action. */
    static int[][] parse(byte[] code, int len) {
        int[][] out = new int[len / 1 + 1][]; int k = 0, i = 0;
        while (i < len) {
            int op = code[i] & 0xFF, pos = i; i++;
            if (op == 0) { out[k++] = new int[]{pos, 0, i, 0}; break; }
            int ln = 0;
            if (op >= 0x80) { ln = u16(code, i); i += 2; }
            out[k++] = new int[]{pos, op, i, ln}; i += ln;
        }
        return Arrays.copyOf(out, k);
    }

    static byte[] patchCode(byte[] src) {
        byte[] code = src.clone();
        while (true) {
            int[][] acts = parse(code, code.length);
            String name = null; int at = -1; byte[] ins = null;
            for (int[] a : acts) {
                int op = a[1];
                if (op == 0x9B || op == 0x8E) {
                    String fn = cstr(code, a[2]);
                    int bodyStart = a[2] + a[3];
                    if (!(FUNCS.contains(fn) || fn.equals("updateHUD"))) continue;
                    byte[] hook = fn.equals("updateHUD") ? hudHook() : fsAction("fn:" + fn);
                    boolean already = bodyStart + hook.length <= code.length
                            && Arrays.equals(Arrays.copyOfRange(code, bodyStart, bodyStart + hook.length), hook);
                    if (!already) { name = fn; at = bodyStart; ins = hook; break; }
                }
            }
            if (name == null) return code;
            for (int[] a : acts)
                if (a[1] == 0x8F || a[1] == 0x8A || a[1] == 0x8D)
                    throw new IllegalStateException("unsupported construct near " + name);
            int n = ins.length, I = at;
            for (int[] a : acts) {
                int op = a[1], ds = a[2], ln = a[3], end = ds + ln;
                if (op == 0x9B || op == 0x8E) {
                    int sizeOff = ds + ln - 2, size = u16(code, sizeOff);
                    if (end <= I && I <= end + size) w16(code, sizeOff, size + n);
                } else if (op == 0x94) {
                    int size = u16(code, ds);
                    if (end <= I && I < end + size) w16(code, ds, size + n);
                } else if (op == 0x99 || op == 0x9D) {
                    int off = s16(code, ds), tgt = end + off;
                    if (end <= I && I < tgt) w16(code, ds, off + n);
                    else if (tgt <= I && I < end) w16(code, ds, off - n);
                }
            }
            Buf o = new Buf(); o.put(code, 0, I); o.put(ins); o.put(code, I, code.length - I);
            code = o.bytes();
            hooks++;
        }
    }

    static void tag(Buf out, int code, byte[] body) {
        if (body.length < 0x3F && code != 39) { out.u16((code << 6) | body.length); }
        else { out.u16((code << 6) | 0x3F); out.u32(body.length); }
        out.put(body);
    }

    static byte[] rewriteTags(byte[] buf, int start, int end, boolean root) {
        Buf out = new Buf(); int i = start;
        while (i < end) {
            int h = u16(buf, i); i += 2;
            int code = h >> 6, ln = h & 0x3F;
            if (ln == 0x3F) { ln = u32(buf, i); i += 4; }
            byte[] body = Arrays.copyOfRange(buf, i, i + ln); i += ln;
            if (code == 12) body = patchCode(body);
            else if (code == 59) body = cat(Arrays.copyOfRange(body, 0, 2), patchCode(Arrays.copyOfRange(body, 2, body.length)));
            else if (code == 39) body = cat(Arrays.copyOfRange(body, 0, 4), rewriteTags(body, 4, body.length, false));
            tag(out, code, body);
            if (root && code == 43) {
                String label = cstr(body, 0);
                if (FRAMES.contains(label)) { tag(out, 12, cat(fsAction("frame:" + label), new byte[]{0})); hooks++; }
            }
        }
        return out.bytes();
    }

    /** Takes the original game file, returns the hooked file (zlib-compressed SWF). */
    public static byte[] patch(byte[] src) throws Exception {
        hooks = 0;
        byte[] data;
        if (src[0] == 'C' && src[1] == 'W' && src[2] == 'S') {
            int total = u32(src, 4);
            data = new byte[total];
            System.arraycopy(src, 0, data, 0, 8);
            Inflater inf = new Inflater();
            inf.setInput(src, 8, src.length - 8);
            int got = 8;
            while (got < total && !inf.finished()) got += inf.inflate(data, got, total - got);
            inf.end();
        } else if (src[0] == 'F' && src[1] == 'W' && src[2] == 'S') {
            data = src;
        } else throw new IllegalArgumentException("not a SWF file");
        int nbits = (data[8] & 0xFF) >> 3;
        int hdr = 8 + (5 + 4 * nbits + 7) / 8 + 4;
        byte[] tags = rewriteTags(data, hdr, data.length, true);
        byte[] out = new byte[hdr + tags.length];
        System.arraycopy(data, 0, out, 0, hdr);
        System.arraycopy(tags, 0, out, hdr, tags.length);
        int len = out.length;
        out[4] = (byte) len; out[5] = (byte) (len >> 8); out[6] = (byte) (len >> 16); out[7] = (byte) (len >> 24);
        // recompress
        Deflater def = new Deflater(9);
        def.setInput(out, 8, out.length - 8); def.finish();
        ByteArrayOutputStream bo = new ByteArrayOutputStream(out.length / 2);
        bo.write('C'); bo.write('W'); bo.write('S'); bo.write(out, 3, 5);
        byte[] tmp = new byte[65536];
        while (!def.finished()) { int k = def.deflate(tmp); bo.write(tmp, 0, k); }
        def.end();
        return bo.toByteArray();
    }

    // ------------------------------------------------------------------ high quality: texture smoothing
    /** Bit reader used to walk shape headers. */
    static final class Bits {
        final byte[] b; int p, bit;
        Bits(byte[] b, int p) { this.b = b; this.p = p; }
        int ub(int n) { int v = 0; for (int i = 0; i < n; i++) { v = (v << 1) | ((b[p] >> (7 - bit)) & 1); if (++bit == 8) { bit = 0; p++; } } return v; }
        void align() { if (bit != 0) { bit = 0; p++; } }
        void rect() { int n = ub(5); ub(n); ub(n); ub(n); ub(n); align(); }
        void matrix() { if (ub(1) == 1) { int n = ub(5); ub(n); ub(n); } if (ub(1) == 1) { int n = ub(5); ub(n); ub(n); } int n = ub(5); ub(n); ub(n); align(); }
    }
    public static int smoothed;

    static void smoothTags(byte[] u, int start, int end) {
        int i = start;
        while (i < end) {
            int h = u16(u, i); i += 2;
            int code = h >> 6, ln = h & 0x3F;
            if (ln == 0x3F) { ln = u32(u, i); i += 4; }
            if (code == 2 || code == 22 || code == 32 || code == 83) {
                try {
                    Bits r = new Bits(u, i + 2); r.rect();
                    if (code == 83) { r.rect(); r.p++; }
                    int c = u[r.p++] & 0xFF;
                    if (c == 0xFF && code != 2) { c = u16(u, r.p); r.p += 2; }
                    boolean rgba = code == 32 || code == 83;
                    for (int k = 0; k < c; k++) {
                        int t = u[r.p] & 0xFF;
                        if (t == 0x42 || t == 0x43) { u[r.p] = (byte) (t - 2); smoothed++; }   // bitmap fill: not smoothed -> smoothed
                        r.p++;
                        if (t == 0) r.p += rgba ? 4 : 3;
                        else if (t == 0x10 || t == 0x12 || t == 0x13) {
                            r.matrix(); int g = u[r.p++] & 0xFF; r.p += (g & 15) * (rgba ? 5 : 4);
                            if (t == 0x13) r.p += 2;
                        } else if (t >= 0x40 && t <= 0x43) { r.p += 2; r.matrix(); }
                        else break;
                    }
                } catch (RuntimeException ignored) { }
            }
            if (code == 39) smoothTags(u, i + 4, i + ln);
            i += ln;
        }
    }

    /** High-quality variant of a (prepared) game file: every bitmap is drawn smoothed when scaled. */
    public static byte[] smooth(byte[] src) throws Exception {
        smoothed = 0;
        byte[] data = inflate(src);
        int nbits = (data[8] & 0xFF) >> 3, hdr = 8 + (5 + 4 * nbits + 7) / 8 + 4;
        smoothTags(data, hdr, data.length);
        return deflate(data);
    }

    static byte[] inflate(byte[] src) throws Exception {
        if (src[0] == 'F') return src.clone();
        int total = u32(src, 4);
        byte[] data = new byte[total];
        System.arraycopy(src, 0, data, 0, 8);
        Inflater inf = new Inflater(); inf.setInput(src, 8, src.length - 8);
        int got = 8; while (got < total && !inf.finished()) got += inf.inflate(data, got, total - got);
        inf.end();
        return data;
    }
    static byte[] deflate(byte[] out) {
        Deflater def = new Deflater(9); def.setInput(out, 8, out.length - 8); def.finish();
        ByteArrayOutputStream bo = new ByteArrayOutputStream(out.length / 2);
        bo.write('C'); bo.write('W'); bo.write('S'); bo.write(out, 3, 5);
        byte[] tmp = new byte[65536];
        while (!def.finished()) { int k = def.deflate(tmp); bo.write(tmp, 0, k); }
        def.end();
        return bo.toByteArray();
    }
}
