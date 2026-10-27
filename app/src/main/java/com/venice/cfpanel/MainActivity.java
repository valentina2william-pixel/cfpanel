package com.venice.cfpanel;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends android.app.Activity {

    private Prefs prefs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(40, 80, 40, 40);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView t = new TextView(this);
        t.setText("CF PANEL\nCounter Flame Overlay");
        t.setTextSize(22);
        t.setGravity(Gravity.CENTER);
        col.addView(t);

        TextView info = new TextView(this);
        info.setTextSize(13);
        info.setPadding(0, 30, 0, 30);
        info.setText("1) Concede el permiso de superposicion\n"
                + "2) Escribe la key (333)\n"
                + "3) Activa el panel y abre el juego");
        col.addView(info);

        Button perm = new Button(this);
        perm.setText("1. Dar permiso de superposicion");
        perm.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        col.addView(perm);

        TextView kl = new TextView(this);
        kl.setText("Key:");
        col.addView(kl);

        EditText key = new EditText(this);
        key.setText(prefs.getS("key", "333"));
        key.setGravity(Gravity.CENTER);
        col.addView(key, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button saveKey = new Button(this);
        saveKey.setText("Guardar key");
        saveKey.setOnClickListener(v -> {
            String k = key.getText().toString().trim();
            prefs.setS("key", k.isEmpty() ? "333" : k);
            prefs.setB("unlocked", false);
        });
        col.addView(saveKey);

        Button start = new Button(this);
        start.setText("2. ACTIVAR PANEL");
        start.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                perm.performClick();
                return;
            }
            startService(new Intent(this, OverlayService.class));
            finish();
        });
        col.addView(start);

        Button stop = new Button(this);
        stop.setText("DETENER PANEL");
        stop.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
        col.addView(stop);

        setContentView(col);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }
  }
