package com.venice.cfpanel;

/** Inyecta toques reales con sendevent (multi-touch slot 1). Requiere root. */
public class Touch {

    private final Shell sh;
    private String dev = null;
    private int maxX, maxY;

    private static final int EV_SYN = 0, EV_KEY = 1, EV_ABS = 3;
    private static final int ABS_MT_SLOT = 47, ABS_MT_TOUCH_MAJOR = 48;
    private static final int ABS_MT_POSITION_X = 53, ABS_MT_POSITION_Y = 54;
    private static final int ABS_MT_TRACKING_ID = 57, ABS_MT_PRESSURE = 58;
    private static final int BTN_TOUCH = 330;

    public Touch(Shell sh, int maxX, int maxY) {
        this.sh = sh;
        this.maxX = maxX;
        this.maxY = maxY;
    }

    public boolean ready() { return dev != null; }
    public String device() { return dev; }
    public void setMax(int x, int y) { maxX = x; maxY = y; }

    /** Busca el dispositivo táctil automáticamente. */
    public String autoDetect() {
        String out = sh.exec("cat /proc/bus/input/devices");
        String best = null;
        for (String block : out.split("\n\n")) {
            String name = null, handlers = null;
            for (String line : block.split("\n")) {
                if (line.startsWith("N: Name=")) name = line.substring(8).replace("\"", "");
                if (line.startsWith("H: Handlers=")) handlers = line.substring(12);
            }
            if (name == null || handlers == null) continue;
            String ev = null;
            for (String t : handlers.split("\\s+")) if (t.startsWith("event")) ev = "/dev/input/" + t;
            if (ev == null) continue;
            String n = name.toLowerCase();
            if (n.contains("touchscreen") || n.contains("touch") || n.contains("ts")
                    || n.contains("goodix") || n.contains("focaltech") || n.contains("synaptics")) {
                best = ev;
                if (n.contains("touch")) break;
            }
        }
        dev = best;
        if (dev != null) sh.exec("chmod 666 " + dev);
        return dev;
    }

    private StringBuilder sb;

    private void begin() { sb = new StringBuilder(); }

    private void raw(int type, int code, int value) {
        sb.append("sendevent ").append(dev).append(' ')
          .append(type).append(' ').append(code).append(' ').append(value).append('\n');
    }

    private void flush() { if (sb.length() > 0) sh.exec(sb.toString()); }

    private int cx(int x) { return Math.max(0, Math.min(maxX - 1, x)); }
    private int cy(int y) { return Math.max(0, Math.min(maxY - 1, y)); }

    public void down(int x, int y, int slot) {
        begin();
        raw(EV_ABS, ABS_MT_SLOT, slot);
        raw(EV_ABS, ABS_MT_TRACKING_ID, 100 + slot);
        raw(EV_ABS, ABS_MT_POSITION_X, cx(x));
        raw(EV_ABS, ABS_MT_POSITION_Y, cy(y));
        raw(EV_ABS, ABS_MT_PRESSURE, 60);
        raw(EV_ABS, ABS_MT_TOUCH_MAJOR, 8);
        raw(EV_KEY, BTN_TOUCH, 1);
        raw(EV_SYN, 0, 0);
        flush();
    }

    public void move(int x, int y, int slot) {
        begin();
        raw(EV_ABS, ABS_MT_SLOT, slot);
        raw(EV_ABS, ABS_MT_POSITION_X, cx(x));
        raw(EV_ABS, ABS_MT_POSITION_Y, cy(y));
        raw(EV_SYN, 0, 0);
        flush();
    }

    public void up(int slot) {
        begin();
        raw(EV_ABS, ABS_MT_SLOT, slot);
        raw(EV_ABS, ABS_MT_TRACKING_ID, -1);
        raw(EV_KEY, BTN_TOUCH, 0);
        raw(EV_SYN, 0, 0);
        flush();
    }

    /** Deslizamiento completo (down -> moves -> up) en un solo comando. */
    public void swipe(int x1, int y1, int x2, int y2, int steps) {
        if (dev == null) return;
        if (steps < 1) steps = 1;
        begin();
        raw(EV_ABS, ABS_MT_SLOT, 1);
        raw(EV_ABS, ABS_MT_TRACKING_ID, 101);
        raw(EV_ABS, ABS_MT_POSITION_X, cx(x1));
        raw(EV_ABS, ABS_MT_POSITION_Y, cy(y1));
        raw(EV_ABS, ABS_MT_PRESSURE, 60);
        raw(EV_ABS, ABS_MT_TOUCH_MAJOR, 8);
        raw(EV_KEY, BTN_TOUCH, 1);
        raw(EV_SYN, 0, 0);
        for (int i = 1; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            raw(EV_ABS, ABS_MT_SLOT, 1);
            raw(EV_ABS, ABS_MT_POSITION_X, cx(x));
            raw(EV_ABS, ABS_MT_POSITION_Y, cy(y));
            raw(EV_SYN, 0, 0);
        }
        raw(EV_ABS, ABS_MT_SLOT, 1);
        raw(EV_ABS, ABS_MT_TRACKING_ID, -1);
        raw(EV_KEY, BTN_TOUCH, 0);
        raw(EV_SYN, 0, 0);
        flush();
    }
                }
