package com.venice.cfpanel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

/** Shell con root (su) reutilizable. Evita abrir 'su' en cada orden. */
public class Shell {

    private static final String END = "__CF_END__";

    private Process p;
    private OutputStream out;
    private BufferedReader in;

    public Shell() throws IOException {
        p = new ProcessBuilder("su").redirectErrorStream(true).start();
        out = p.getOutputStream();
        in = new BufferedReader(new InputStreamReader(p.getInputStream()));
        exec("id");
    }

    public boolean alive() {
        return p != null && p.isAlive();
    }

    /** Ejecuta y espera la salida. Devuelve "" si falla. */
    public synchronized String exec(String cmd) {
        if (p == null) return "";
        try {
            out.write((cmd + " ; echo " + END + "\n").getBytes("UTF-8"));
            out.flush();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.contains(END)) break;
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public void close() {
        try {
            if (out != null) { out.write("exit\n".getBytes()); out.flush(); }
        } catch (Exception ignored) {}
        try { if (p != null) p.destroy(); } catch (Exception ignored) {}
    }
}
