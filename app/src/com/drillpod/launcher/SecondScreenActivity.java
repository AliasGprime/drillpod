package com.drillpod.launcher;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;

/** Fallback for secondary screens that refuse a Presentation: a plain activity showing the
 *  dashboard. It never takes the focus, and forwards any controller input to the game. */
public class SecondScreenActivity extends Activity {
    static SecondScreenActivity current;
    private WebView web;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        MainActivity main = MainActivity.instance;
        if (main == null) { finish(); return; }
        current = this;
        web = main.makeSecondWebView(this);
        setContentView(web);
        main.secondReady(web);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        MainActivity m = MainActivity.instance;
        return m != null ? m.dispatchKeyEvent(e) : super.dispatchKeyEvent(e);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        MainActivity m = MainActivity.instance;
        return m != null ? m.dispatchGenericMotionEvent(e) : super.dispatchGenericMotionEvent(e);
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        MainActivity m = MainActivity.instance;
        if (m != null) m.secondGone(web);
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
