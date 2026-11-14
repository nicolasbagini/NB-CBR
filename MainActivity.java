package com.visorcbr.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    // La página activa esto mientras lees, para pasar de página con las teclas de volumen
    static volatile boolean captureVolume = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(VisorPlugin.class);
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#1a1a1a"));
        getWindow().setNavigationBarColor(Color.parseColor("#1a1a1a"));
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(false);
        c.setAppearanceLightNavigationBars(false);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (captureVolume && (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                final String d = (k == KeyEvent.KEYCODE_VOLUME_UP) ? "up" : "down";
                getBridge().getWebView().post(() -> getBridge().getWebView().evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('volkey',{detail:'" + d + "'}))", null));
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
}
