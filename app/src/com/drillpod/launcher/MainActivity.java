package com.drillpod.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.app.Presentation;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.os.SystemClock;
import android.webkit.JavascriptInterface;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    /** A WebView that brings up Android's keyboard only when we explicitly allow it
     *  (typing a username in the game), never on its own. */
    static class NoImeWebView extends WebView {
        boolean allowIme = false;
        NoImeWebView(Context c) { super(c); }
        @Override public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            return allowIme ? super.onCreateInputConnection(outAttrs) : null;
        }
        @Override public boolean onCheckIsTextEditor() { return allowIme; }
    }

    // Where the game is served from (intercepted locally, never fetched from the network).
    // Replaced at build time: the 2nd game version only runs from its original web address.
    private static final String HOST = "@@HOST@@";
    private static final String ORIGIN = "@@SCHEME@@://" + HOST;
    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        web = new NoImeWebView(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setWebViewClient(new AssetClient());
        instance = this;
        displayListener = new DisplayManager.DisplayListener() {
            public void onDisplayAdded(int id) { updateSecondScreen(); }
            public void onDisplayRemoved(int id) { updateSecondScreen(); }
            public void onDisplayChanged(int id) { }
        };
        ((DisplayManager) getSystemService(Context.DISPLAY_SERVICE)).registerDisplayListener(displayListener, null);
        web.addJavascriptInterface(new PadBridge(), "AndroidPad");
        setContentView(web);
        hideSystemUi();
        if (state != null) web.restoreState(state);
        else web.loadUrl(ORIGIN + "/launcher.html");
    }

    /** Serves the bundled files at ORIGIN; nothing ever goes to the network. */
    class AssetClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            if (!HOST.equals(req.getUrl().getHost()))
                return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<String, String>(), null);
            String path = req.getUrl().getPath();
            if (path == null || path.equals("/")) path = "/launcher.html";
            try {
                // The game itself is never bundled: it is the user's own imported (and prepared) copy.
                InputStream in = path.equals("/game.swf") ? new java.io.FileInputStream(gameFile())
                               : path.equals("/game_hq.swf") ? new java.io.FileInputStream(hqGameFile())
                               : getAssets().open("www" + path);
                Map<String, String> h = new HashMap<>();
                h.put("Access-Control-Allow-Origin", "*");
                h.put("Cache-Control", "no-cache");
                return new WebResourceResponse(mime(path), mime(path).startsWith("text") || mime(path).endsWith("javascript") ? "utf-8" : null,
                        200, "OK", h, in);
            } catch (Exception e) {
                return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<String, String>(), null);
            }
        }
        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return !url.startsWith(ORIGIN + "/"); // block external navigation
        }
    }

    // ---------- Second screen (AYN Thor bottom screen, any secondary display) ----------
    // The game data (cash, fuel, cargo, items...) is shown there on a dashboard page.
    static MainActivity instance;
    private Presentation second;
    private WebView secondWeb;
    private String lastHud;
    private DisplayManager.DisplayListener displayListener;

    private Display findSecondDisplay() {
        DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        int mine = getWindowManager().getDefaultDisplay().getDisplayId();
        for (Display d : dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION))
            if (d.getDisplayId() != mine) return d;
        for (Display d : dm.getDisplays())
            if (d.getDisplayId() != mine) return d;
        return null;
    }

    @SuppressWarnings("deprecation")
    WebView makeSecondWebView(Context c) {
        WebView w = new NoImeWebView(c);
        w.setBackgroundColor(Color.rgb(11, 15, 10));
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        w.setWebViewClient(new AssetClient() {
            @Override public void onPageFinished(WebView view, String url) {
                if (view == secondWeb && lastHud != null) pushHud(lastHud);   // fill it as soon as it is ready
            }
        });
        w.setFocusable(false);
        w.loadUrl(ORIGIN + "/second.html");
        return w;
    }

    void secondReady(WebView w) {
        secondWeb = w;
        if (lastHud != null) pushHud(lastHud);
    }

    private boolean inGame = false, secondEnabled = true;

    private void updateSecondScreen() {
        Display d = (inGame && secondEnabled) ? findSecondDisplay() : null;
        if (d == null) { closeSecondScreen(); return; }
        if (second != null && second.getDisplay().getDisplayId() == d.getDisplayId()) return;
        if (SecondScreenActivity.current != null) return;   // already shown by the fallback activity
        closeSecondScreen();
        final Presentation p = new Presentation(this, d) {
            @Override protected void onCreate(Bundle b) {
                super.onCreate(b);
                WebView w = makeSecondWebView(getContext());
                setContentView(w);
                secondReady(w);
            }
        };
        try {
            p.show();
            second = p;
        } catch (Exception e) {
            // Not a presentation-capable display: open a (non-focusable) activity on it instead.
            second = null;
            try {
                android.content.Intent i = new android.content.Intent(this, SecondScreenActivity.class);
                i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                android.app.ActivityOptions o = android.app.ActivityOptions.makeBasic();
                o.getClass().getMethod("setLaunchDisplayId", int.class).invoke(o, d.getDisplayId());   // API 26+
                startActivity(i, o.toBundle());
            } catch (Exception ignored) { }
        }
    }

    private void closeSecondScreen() {
        if (second != null) { try { second.dismiss(); } catch (Exception ignored) { } }
        second = null;
        if (SecondScreenActivity.current != null) SecondScreenActivity.current.finish();
        secondWeb = null;
    }

    void secondGone(WebView w) { if (secondWeb == w) secondWeb = null; }

    void pushHud(String json) {
        lastHud = json;
        if (secondWeb != null) secondWeb.evaluateJavascript("window.update&&update(" + json + ")", null);
    }

    private static String mime(String p) {
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".wasm")) return "application/wasm";
        if (p.endsWith(".swf")) return "application/x-shockwave-flash";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (second != null) closeSecondScreen();
        web.onPause();
        web.pauseTimers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.resumeTimers();
        web.onResume();
        hideSystemUi();
        updateSecondScreen();
    }

    // ---------- Virtual mouse: real input events injected into the WebView ----------
    // The game (Ruffle) ignores synthetic DOM mouse events on some devices, so the cursor
    // drives it with genuine Android events: a mouse hover to move, a finger tap to click.
    class PadBridge {
        @JavascriptInterface public void page(final String name) {
            web.post(new Runnable() { public void run() { inGame = "game".equals(name); updateSecondScreen(); } });
        }
        @JavascriptInterface public void setSecond(final boolean on) {
            web.post(new Runnable() { public void run() { secondEnabled = on; updateSecondScreen(); } });
        }
        @JavascriptInterface public String gameStatus() {
            return "{\"installed\":" + gameFile().isFile() + "}";
        }
        @JavascriptInterface public void importGame() {
            web.post(new Runnable() { public void run() { pickGameFile(); } });
        }
        @JavascriptInterface public void downloadGame() {
            web.post(new Runnable() { public void run() { MainActivity.this.downloadGame(); } });
        }
        @JavascriptInterface public void removeGame() { gameFile().delete(); new java.io.File(getFilesDir(), "game_hq.swf").delete(); }
        @JavascriptInterface public void quit() { web.post(new Runnable() { public void run() { finish(); } }); }
        @JavascriptInterface public void hover(final float fx, final float fy) {
            web.post(new Runnable() { public void run() { injectHover(fx, fy); } });
        }
        @JavascriptInterface public void tap(final float fx, final float fy) {
            web.post(new Runnable() { public void run() { injectTap(fx, fy); } });
        }
        @JavascriptInterface public void second(final String json) {
            web.post(new Runnable() { public void run() { pushHud(json); } });
        }
        @JavascriptInterface public boolean hasSecondScreen() { return secondWeb != null; }
        @JavascriptInterface public void keyboard(final boolean on) {
            web.post(new Runnable() { public void run() { setKeyboard(on); } });
        }
        @JavascriptInterface public void scroll(final float fx, final float fy, final float v) {
            web.post(new Runnable() { public void run() { injectScroll(fx, fy, v); } });
        }
    }

    private MotionEvent pointerEvent(long down, long now, int action, float x, float y, int toolType, int source, int buttons) {
        MotionEvent.PointerProperties[] pp = { new MotionEvent.PointerProperties() };
        pp[0].id = 0; pp[0].toolType = toolType;
        MotionEvent.PointerCoords[] pc = { new MotionEvent.PointerCoords() };
        pc[0].x = x; pc[0].y = y; pc[0].pressure = action == MotionEvent.ACTION_UP ? 0f : 1f; pc[0].size = 1f;
        return MotionEvent.obtain(down, now, action, 1, pp, pc, 0, buttons, 1f, 1f, 0, 0, source, 0);
    }

    private void injectHover(float fx, float fy) {
        long t = SystemClock.uptimeMillis();
        MotionEvent e = pointerEvent(t, t, MotionEvent.ACTION_HOVER_MOVE, fx * web.getWidth(), fy * web.getHeight(),
                MotionEvent.TOOL_TYPE_MOUSE, InputDevice.SOURCE_MOUSE, 0);
        web.dispatchGenericMotionEvent(e);
        e.recycle();
    }

    private void injectTap(float fx, float fy) {
        final float x = fx * web.getWidth(), y = fy * web.getHeight();
        final long t = SystemClock.uptimeMillis();
        MotionEvent d = pointerEvent(t, t, MotionEvent.ACTION_DOWN, x, y,
                MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN, 0);
        web.dispatchTouchEvent(d);
        d.recycle();
        web.postDelayed(new Runnable() { public void run() {
            MotionEvent u = pointerEvent(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y,
                    MotionEvent.TOOL_TYPE_FINGER, InputDevice.SOURCE_TOUCHSCREEN, 0);
            web.dispatchTouchEvent(u);
            u.recycle();
            injectHover(x / web.getWidth(), y / web.getHeight());   // keep hovering where we clicked
        } }, 70);
    }

    private void setKeyboard(boolean on) {
        ((NoImeWebView) web).allowIme = on;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        getWindow().setSoftInputMode((on ? WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
                : WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN) | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        imm.restartInput(web);
        if (on) { web.requestFocus(); imm.showSoftInput(web, 0); }
        else { imm.hideSoftInputFromWindow(web.getWindowToken(), 0); hideSystemUi(); }
    }

    private void injectScroll(float fx, float fy, float v) {
        long t = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties[] pp = { new MotionEvent.PointerProperties() };
        pp[0].id = 0; pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] pc = { new MotionEvent.PointerCoords() };
        pc[0].x = fx * web.getWidth(); pc[0].y = fy * web.getHeight();
        pc[0].setAxisValue(MotionEvent.AXIS_VSCROLL, v);
        MotionEvent e = MotionEvent.obtain(t, t, MotionEvent.ACTION_SCROLL, 1, pp, pc, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        web.dispatchGenericMotionEvent(e);
        e.recycle();
    }

    // ---------- Gamepad support (AYN Thor & co.) ----------
    // Raw controller input is forwarded to the page (window.GP) which owns the mapping logic.
    private final float[] lastAxes = new float[8];

    private static String buttonName(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "L1";
            case KeyEvent.KEYCODE_BUTTON_R1: return "R1";
            case KeyEvent.KEYCODE_BUTTON_L2: return "L2";
            case KeyEvent.KEYCODE_BUTTON_R2: return "R2";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "L3";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "R3";
            case KeyEvent.KEYCODE_BUTTON_START: return "START";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "SELECT";
            case KeyEvent.KEYCODE_BUTTON_MODE: return "MODE";
            case KeyEvent.KEYCODE_DPAD_UP: return "UP";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "DOWN";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "LEFT";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "RIGHT";
            case KeyEvent.KEYCODE_DPAD_CENTER: return "A";
            default: return null;
        }
    }

    private static boolean fromController(InputEvent e) {
        int src = e.getSource();
        return (src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            || (src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            || (src & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
    }

    private void js(String code) {
        if (web != null) web.evaluateJavascript("window.GP&&" + code, null);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        String name = buttonName(e.getKeyCode());
        // Some handhelds report the B button as BACK coming from the gamepad.
        if (name == null && e.getKeyCode() == KeyEvent.KEYCODE_BACK
                && (e.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) name = "B";
        int kc = e.getKeyCode();
        boolean dpad = kc == KeyEvent.KEYCODE_DPAD_UP || kc == KeyEvent.KEYCODE_DPAD_DOWN
                || kc == KeyEvent.KEYCODE_DPAD_LEFT || kc == KeyEvent.KEYCODE_DPAD_RIGHT
                || kc == KeyEvent.KEYCODE_DPAD_CENTER;
        // D-pad keys are always ours, whatever device reports them (built-in pad, keyboard...).
        boolean pad = dpad || KeyEvent.isGamepadButton(kc) || fromController(e);
        if (name != null && pad) {
            if (e.getRepeatCount() == 0 && (e.getAction() == KeyEvent.ACTION_DOWN || e.getAction() == KeyEvent.ACTION_UP)) {
                js("GP.button('" + name + "'," + (e.getAction() == KeyEvent.ACTION_DOWN) + ")");
            }
            return true; // consumed: never reaches the WebView nor triggers "Back"
        }
        return super.dispatchKeyEvent(e);
    }

    private static float axis(MotionEvent e, int a) {
        return e.getAxisValue(a);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && e.getAction() == MotionEvent.ACTION_MOVE) {
            float[] v = new float[] {
                axis(e, MotionEvent.AXIS_X), axis(e, MotionEvent.AXIS_Y),
                axis(e, MotionEvent.AXIS_Z), axis(e, MotionEvent.AXIS_RZ),
                axis(e, MotionEvent.AXIS_HAT_X), axis(e, MotionEvent.AXIS_HAT_Y),
                Math.max(axis(e, MotionEvent.AXIS_LTRIGGER), axis(e, MotionEvent.AXIS_BRAKE)),
                Math.max(axis(e, MotionEvent.AXIS_RTRIGGER), axis(e, MotionEvent.AXIS_GAS)) };
            boolean changed = false;
            for (int i = 0; i < 8; i++) if (Math.abs(v[i] - lastAxes[i]) > 0.02f) changed = true;
            if (changed) {
                System.arraycopy(v, 0, lastAxes, 0, 8);
                StringBuilder sb = new StringBuilder("GP.axes([");
                for (int i = 0; i < 8; i++) { if (i > 0) sb.append(','); sb.append(Math.round(v[i] * 1000) / 1000f); }
                js(sb.append("])").toString());
            }
            return true;
        }
        return super.dispatchGenericMotionEvent(e);
    }

    @Override
    public void onBackPressed() {
        // Let the page handle it first (close a panel / dialog in the launcher).
        web.evaluateJavascript("(window.onBack ? !!onBack() : false)", new android.webkit.ValueCallback<String>() {
            public void onReceiveValue(String handled) {
                if ("true".equals(handled)) return;
                long now = SystemClock.uptimeMillis();
                if (now - lastBack < 2000) {
                    lastBack = 0;
                    if (inGame) { inGame = false; updateSecondScreen(); web.loadUrl(ORIGIN + "/launcher.html"); }
                    else finish();
                } else {
                    lastBack = now;
                    android.widget.Toast.makeText(MainActivity.this, inGame
                            ? "Appuyez encore sur Retour pour revenir au menu (pensez à sauvegarder)"
                            : "Appuyez encore sur Retour pour quitter", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
    private long lastBack = 0;

    // ---------- Importing the user's game file ----------
    static final String GAME_MD5 = "42469867aa46129edbab37894c344cb4";
    private static final int PICK_GAME = 42;

    java.io.File gameFile() { return new java.io.File(getFilesDir(), "game.swf"); }

    /** High-quality copy (textures smoothed), made on the device from the prepared game file. */
    synchronized java.io.File hqGameFile() throws Exception {
        java.io.File hq = new java.io.File(getFilesDir(), "game_hq.swf");
        if (hq.isFile() && hq.lastModified() >= gameFile().lastModified()) return hq;
        byte[] out = SwfPatcher.smooth(readAll(new java.io.FileInputStream(gameFile())));
        java.io.File tmp = new java.io.File(getFilesDir(), "game_hq.swf.tmp");
        java.io.FileOutputStream fo = new java.io.FileOutputStream(tmp); fo.write(out); fo.close();
        if (!tmp.renameTo(hq)) throw new Exception("écriture impossible");
        return hq;
    }

    private void pickGameFile() {
        android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try { startActivityForResult(i, PICK_GAME); }
        catch (Exception e) { importResult("{ok:false,reason:'error',detail:'Aucun sélecteur de fichiers disponible.'}"); }
    }

    static final String GAME_URL = "http://www.xgenstudios.com/motherload/8khsgfbehb.swf";

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536]; int n, total = 0;
        try {
            while ((n = in.read(buf)) > 0) { bo.write(buf, 0, n); total += n; if (total > 64 << 20) throw new Exception("fichier trop volumineux"); }
        } finally { in.close(); }
        return bo.toByteArray();
    }

    /** Checks the MD5, adds the hooks and stores the prepared copy. Returns the JS result. */
    private String installGame(byte[] file) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest(file)) hex.append(String.format("%02x", b));
        if (!GAME_MD5.equals(hex.toString())) return "{ok:false,reason:'md5',md5:'" + hex + "'}";
        byte[] prepared = SwfPatcher.patch(file);                 // add the hooks, on the device
        java.io.File tmp = new java.io.File(getFilesDir(), "game.swf.tmp");
        java.io.FileOutputStream fo = new java.io.FileOutputStream(tmp);
        fo.write(prepared); fo.close();
        if (!tmp.renameTo(gameFile())) throw new Exception("écriture impossible");
        return "{ok:true,hooks:" + SwfPatcher.hooks + "}";
    }

    /** Fetches the file from the publisher's own website (follows http->https redirects). */
    private void downloadGame() {
        web.evaluateJavascript("window.onImportStarted&&onImportStarted('Téléchargement depuis xgenstudios.com…')", null);
        new Thread(new Runnable() { public void run() {
            String js;
            try {
                String url = GAME_URL;
                java.net.HttpURLConnection c = null;
                for (int hop = 0; hop < 5; hop++) {
                    c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setConnectTimeout(15000); c.setReadTimeout(30000);
                    c.setInstanceFollowRedirects(false);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) DrillPod");
                    int code = c.getResponseCode();
                    if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                        url = new java.net.URL(new java.net.URL(url), c.getHeaderField("Location")).toString();
                        c.disconnect(); continue;
                    }
                    if (code != 200) throw new Exception("le site a répondu " + code);
                    break;
                }
                js = installGame(readAll(c.getInputStream()));
                c.disconnect();
            } catch (java.net.UnknownHostException e) {
                js = "{ok:false,reason:'net',detail:'Pas de connexion Internet, ou site introuvable.'}";
            } catch (Throwable t) {
                js = "{ok:false,reason:'net',detail:" + org.json.JSONObject.quote(String.valueOf(t.getMessage())) + "}";
            }
            final String result = js;
            web.post(new Runnable() { public void run() { importResult(result); } });
        } }).start();
    }

    private void importResult(String js) { web.evaluateJavascript("window.onImportResult&&onImportResult(" + js + ")", null); }

    @Override
    protected void onActivityResult(int req, int res, final android.content.Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PICK_GAME) return;
        if (res != RESULT_OK || data == null || data.getData() == null) { importResult("{ok:false,reason:'cancel'}"); return; }
        web.evaluateJavascript("window.onImportStarted&&onImportStarted()", null);
        final android.net.Uri uri = data.getData();
        new Thread(new Runnable() { public void run() {
            String js;
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                js = installGame(readAll(in));
            } catch (Throwable t) {
                js = "{ok:false,reason:'error',detail:" + org.json.JSONObject.quote(String.valueOf(t.getMessage())) + "}";
            }
            final String result = js;
            web.post(new Runnable() { public void run() { importResult(result); } });
        } }).start();
    }

    @Override
    protected void onDestroy() {
        try { ((DisplayManager) getSystemService(Context.DISPLAY_SERVICE)).unregisterDisplayListener(displayListener); } catch (Exception ignored) { }
        closeSecondScreen();
        if (SecondScreenActivity.current != null) SecondScreenActivity.current.finish();
        if (instance == this) instance = null;
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
