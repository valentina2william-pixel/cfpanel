package com.venice.cfpanel;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class OverlayService extends Service {

    private static final String CH = "cf_panel";
    private static final int NOTI = 333;

    private WindowManager wm;
    private Prefs prefs;

    private View bubble;
    private WindowManager.LayoutParams lpBubble;

    private ScrollView panel;
    private WindowManager.LayoutParams lpPanel;
    private boolean panelOpen = false;

    private View cross;
    private WindowManager.LayoutParams lpCross;

    private TextView status;

    private Shell sh;
    private Mem mem;
    private Touch touch;

    private volatile boolean run = false;
    private Thread worker;
    private long lastFire = 0, lastFlags = 0;

    private final Handler ui = new Handler(Looper.getMainLooper());

    // =========================================================== ciclo de vida

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        startFg();
        buildBubble();
        buildCrosshair();
        applyCross();
        askKey();

        new Thread(() -> {
            try {
                sh = new Shell();
                mem = new Mem(sh);
                touch = new Touch(sh, screenW(), screenH());
                touch.autoDetect();
                ui.post(() -> log("Root OK. Tactil: " + touch.device()));
            } catch (Exception e) {
                ui.post(() -> log("Sin root: " + e.getMessage()));
            }
        }).start();
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        if (!run) {
            run = true;
            worker = new Thread(this::loop);
            worker.start();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        run = false;
        try { if (worker != null) worker.interrupt(); } catch (Exception ignored) {}
        try { if (panel != null && panelOpen) wm.removeView(panel); } catch (Exception ignored) {}
        try { wm.removeView(bubble); } catch (Exception ignored) {}
        try { if (cross != null) wm.removeView(cross); } catch (Exception ignored) {}
        if (mem != null) mem.close();
        if (sh != null) sh.close();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    // =========================================================== foreground

    private void startFg() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CH, "CF Panel",
                    NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
        Notification n = new Notification.Builder(this, CH)
                .setContentTitle("CF Panel activo")
                .setContentText("Panel flotante en ejecucion")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTI, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTI, n);
        }
    }

    // =========================================================== key 333

    private void askKey() {
        final EditText et = new EditText(this);
        et.setHint("Key");
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(0xFF8FA3B8);

        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(18), dp(10), dp(18), 0);
        box.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("CF PANEL")
                .setMessage("Escribe la key para entrar")
                .setView(box)
                .setCancelable(false)
                .setPositiveButton("ENTRAR", null)
                .setNegativeButton("SALIR", null)
                .create();

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        d.getWindow().setType(type);

        d.setOnShowListener(x -> {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String key = prefs.getS("key", "333");
                if (et.getText().toString().trim().equals(key)) {
                    prefs.setB("unlocked", true);
                    d.dismiss();
                    toast("Key correcta");
                    log("Key OK. Panel listo.");
                } else {
                    et.setError("Key incorrecta");
                    toast("Key incorrecta (por defecto: 333)");
                }
            });
            d.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                d.dismiss();
                stopSelf();
            });
        });
        d.show();
    }

    // =========================================================== UI flotante

    private WindowManager.LayoutParams params() {
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        return lp;
    }

    private void buildBubble() {
        TextView tv = new TextView(this);
        tv.setText("CF");
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(Typeface.DEFAULT_BOLD);

        GradientDrawable g = new GradientDrawable();
        g.setColor(0xE60B1220);
        g.setStroke(dp(2), 0xFF25E07A);
        g.setCornerRadius(dp(28));
        tv.setBackground(g);

        lpBubble = params();
        lpBubble.width = dp(56);
        lpBubble.height = dp(56);
        lpBubble.x = dp(6);
        lpBubble.y = dp(160);
        bubble = tv;

        drag(bubble, lpBubble, this::togglePanel);
        wm.addView(bubble, lpBubble);
    }

    private void buildCrosshair() {
        cross = new Cross(this);
        lpCross = params();
        lpCross.width = dp(70);
        lpCross.height = dp(70);
        lpCross.gravity = Gravity.TOP | Gravity.START;
        lpCross.x = (screenW() - dp(70)) / 2;
        lpCross.y = (screenH() - dp(70)) / 2;
        lpCross.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    }

    private void applyCross() {
        try {
            if (prefs.getB("cross", true)) {
                if (cross.getParent() == null) wm.addView(cross, lpCross);
            } else if (cross.getParent() != null) {
                wm.removeView(cross);
            }
        } catch (Exception ignored) {}
    }

    class Cross extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Cross(Context c) { super(c); }

        @Override protected void onDraw(Canvas cv) {
            p.setColor(0xFF25E07A);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(2));
            float w = getWidth(), h = getHeight();
            cv.drawCircle(w / 2f, h / 2f, dp(9), p);
            cv.drawLine(w / 2f, 0, w / 2f, dp(6), p);
            cv.drawLine(w / 2f, h - dp(6), w / 2f, h, p);
            cv.drawLine(0, h / 2f, dp(6), h / 2f, p);
            cv.drawLine(w - dp(6), h / 2f, w, h / 2f, p);
            p.setStyle(Paint.Style.FILL);
            cv.drawCircle(w / 2f, h / 2f, dp(2), p);
        }
    }

    private void drag(View v, WindowManager.LayoutParams lp, Runnable click) {
        v.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy, sx, sy;
            long t0;
            boolean moved;

            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX() - lp.x;
                        dy = e.getRawY() - lp.y;
                        sx = e.getRawX(); sy = e.getRawY();
                        t0 = System.currentTimeMillis();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawX() - sx) > dp(6) || Math.abs(e.getRawY() - sy) > dp(6))
                            moved = true;
                        lp.x = (int) (e.getRawX() - dx);
                        lp.y = (int) (e.getRawY() - dy);
                        try { wm.updateViewLayout(v, lp); } catch (Exception ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved && System.currentTimeMillis() - t0 < 400 && click != null) click.run();
                        return true;
                }
                return false;
            }
        });
    }

    private void togglePanel() {
        try {
            if (panelOpen) {
                wm.removeView(panel);
                panelOpen = false;
                return;
            }
            if (panel == null) buildPanel();
            lpPanel.x = Math.min(lpBubble.x + dp(64), screenW() - dp(258));
            lpPanel.y = Math.min(lpBubble.y, Math.max(dp(10), screenH() - dp(460)));
            wm.addView(panel, lpPanel);
            panelOpen = true;
            applyCross();
        } catch (Exception e) {
            toast("Error al abrir panel: " + e.getMessage());
        }
    }

    // =========================================================== panel

    private TextView lbl(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(0xFFDCE6F2);
        v.setTextSize(12);
        return v;
    }

    private Switch sw(String text, String key, boolean def) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setTextColor(0xFFDCE6F2);
        s.setTextSize(13);
        s.setChecked(prefs.getB(key, def));
        s.setOnCheckedChangeListener((b, val) -> {
            prefs.setB(key, val);
            if ("cross".equals(key)) applyCross();
            if ("aimbot".equals(key)) log(val ? "Aimbot ON" : "Aimbot OFF");
            if ("wall".equals(key)) log(val ? "Muro ON" : "Muro OFF");
        });
        return s;
    }

    private LinearLayout slider(String text, String key, int min, int max, int def) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        final TextView t = lbl(text + ": " + prefs.getI(key, def));
        android.widget.SeekBar sb = new android.widget.SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(prefs.getI(key, def) - min);
        sb.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar s, int p, boolean u) {
                int v = p + min;
                prefs.setI(key, v);
                t.setText(text + ": " + v);
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar s) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar s) {}
        });
        box.addView(t);
        box.addView(sb);
        return box;
    }

    private Button btn(String text, Runnable r) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private void buildPanel() {
        panel = new ScrollView(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xF20A0F1A);
        g.setStroke(dp(2), 0xFF25E07A);
        g.setCornerRadius(dp(14));
        panel.setBackground(g);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(10), dp(6), dp(10), dp(12));
        panel.addView(col);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("  CF PANEL  ");
        title.setTextColor(0xFF25E07A);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(14);
        TextView mini = new TextView(this);
        mini.setText("-");
        mini.setTextColor(Color.WHITE);
        mini.setTextSize(18);
        mini.setPadding(dp(10), 0, dp(10), 0);
        mini.setOnClickListener(v -> togglePanel());
        TextView close = new TextView(this);
        close.setText("X");
        close.setTextColor(0xFFFF6B6B);
        close.setTextSize(16);
        close.setPadding(dp(6), 0, dp(4), 0);
        close.setOnClickListener(v -> stopSelf());
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(mini);
        head.addView(close);
        col.addView(head);

        status = lbl("Estado: iniciando...");
        status.setTextColor(0xFF8FA3B8);
        col.addView(status);

        col.addView(btn("CONECTAR AL JUEGO", this::connect));
        col.addView(btn("ELEGIR PROCESO A MANO", this::pickProcess));
        col.addView(btn("ESCANEAR VALOR EN MEMORIA", this::scanDialog));
        col.addView(btn("EDITAR DIRECCIONES / OFFSETS", this::editOffsets));

        col.addView(sep());
        col.addView(sw("Aimbot (apuntar a la cabeza)", "aimbot", true));
        col.addView(sw("Ajuste a la cabeza", "head", true));
        col.addView(sw("Ver enemigos tras paredes", "wall", false));
        col.addView(sw("Sin retroceso", "norecoil", false));
        col.addView(sw("Velocidad (speed)", "speed", false));
        col.addView(sw("Disparo automatico", "autofire", false));
        col.addView(sw("Crosshair", "cross", true));
        col.addView(sw("Invertir X", "invx", false));
        col.addView(sw("Invertir Y", "invy", false));
        col.addView(sw("Direcciones como offsets de libil2cpp", "useoffs", true));

        col.addView(sep());
        col.addView(slider("FOV del aimbot (grados)", "fov", 1, 60, 12));
        col.addView(slider("Velocidad de bucle (ms)", "rate", 5, 60, 12));
        col.addView(slider("Intervalo de disparo (ms)", "firems", 60, 600, 130));

        col.addView(sep());
        col.addView(btn("PROBAR LECTURA", this::testRead));
        col.addView(btn("REINICIAR KEY", this::resetKey));
        col.addView(btn("CERRAR PANEL", () -> stopSelf()));

        lpPanel = params();
        lpPanel.width = dp(250);
        lpPanel.height = dp(450);
        drag(head, lpPanel, null);
    }

    private View sep() {
        View v = new View(this);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        p.setMargins(0, dp(8), 0, dp(8));
        v.setLayoutParams(p);
        v.setBackgroundColor(0x33FFFFFF);
        return v;
    }

    // =========================================================== acciones

    private void connect() {
        new Thread(() -> {
            try {
                if (sh == null) sh = new Shell();
                if (mem == null) mem = new Mem(sh);
                if (touch == null) touch = new Touch(sh, screenW(), screenH());

                int pid = Mem.findPid(Prefs.GAME);
                if (pid < 0) {
                    ui.post(() -> log("No encuentro " + Prefs.GAME + ". Abriste el juego?"));
                    return;
                }
                boolean ok = mem.open(pid);
                ui.post(() -> log(ok
                        ? "Conectado. PID " + pid + " | libil2cpp 0x"
                          + Long.toHexString(mem.libil2cppBase())
                        : "Fallo al abrir memoria (root concedido?)"));
            } catch (Exception e) {
                ui.post(() -> log("Error: " + e.getMessage()));
            }
        }).start();
    }

    private void pickProcess() {
        new Thread(() -> {
            List<String> list = Mem.appProcesses();
            String[] items = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {
                String[] p = list.get(i).split("\\|");
                items[i] = p[1] + "   (PID " + p[0] + ")";
            }
            ui.post(() -> new AlertDialog.Builder(OverlayService.this,
                    android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Elige el proceso")
                    .setItems(items, (d, w) -> {
                        int pid = Integer.parseInt(list.get(w).split("\\|")[0]);
                        new Thread(() -> {
                            boolean ok = mem.open(pid);
                            ui.post(() -> log(ok ? "Conectado PID " + pid : "Fallo al abrir PID " + pid));
                        }).start();
                    })
                    .show());
        }).start();
    }

    private void scanDialog() {
        final EditText et = new EditText(this);
        et.setHint("ej: 100   o   f:1.5");
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Buscar valor (int o f:float)")
                .setView(et)
                .setPositiveButton("BUSCAR", (d, w) -> {
                    if (mem == null || !mem.isOpen()) { toast("Conecta primero"); return; }
                    final String q = et.getText().toString().trim();
                    toast("Buscando...");
                    new Thread(() -> {
                        String txt;
                        try {
                            if (q.startsWith("f:")) {
                                float f = Float.parseFloat(q.substring(2));
                                txt = list(mem.scanInt(Float.floatToIntBits(f), 40));
                            } else {
                                txt = list(mem.scanInt(Integer.parseInt(q), 40));
                            }
                        } catch (Exception e) { txt = "Error: " + e.getMessage(); }
                        final String out = txt;
    
