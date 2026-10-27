package com.venice.cfpanel;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    /** Paquete del juego (Counter Flame). */
    public static final String GAME = "com.ScarletWork.CounterFlame";

    private static final String NAME = "cf_panel";
    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public String getS(String k, String d) { return sp.getString(k, d); }
    public void setS(String k, String v) { sp.edit().putString(k, v).apply(); }

    public int getI(String k, int d) { return sp.getInt(k, d); }
    public void setI(String k, int v) { sp.edit().putInt(k, v).apply(); }

    public long getL(String k, long d) { return sp.getLong(k, d); }
    public void setL(String k, long v) { sp.edit().putLong(k, v).apply(); }

    public boolean getB(String k, boolean d) { return sp.getBoolean(k, d); }
    public void setB(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
}
