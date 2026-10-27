package com.venice.cfpanel;

import android.os.Process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/** Lee y escribe la memoria de otro proceso usando root (/proc/pid/mem). */
public class Mem {

    private final Shell sh;
    private int pid = -1;
    private RandomAccessFile raf;
    private long libBase = 0;

    public Mem(Shell sh) { this.sh = sh; }

    public int getPid() { return pid; }
    public boolean isOpen() { return raf != null && pid > 0; }

    // ---------- procesos ----------

    public static List<Integer> pids() {
        List<Integer> list = new ArrayList<>();
        File[] fs = new File("/proc").listFiles();
        if (fs == null) return list;
        for (File f : fs) {
            String n = f.getName();
            if (n.isEmpty() || !Character.isDigit(n.charAt(0))) continue;
            try { list.add(Integer.parseInt(n)); } catch (Exception ignored) {}
        }
        return list;
    }

    public static String cmdline(int p) {
        try {
            FileInputStream in = new FileInputStream("/proc/" + p + "/cmdline");
            byte[] b = new byte[256];
            int n = in.read(b);
            in.close();
            if (n <= 0) return "";
            return new String(b, 0, n, "UTF-8").replace("\0", " ").trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static int findPid(String name) {
        int me = Process.myPid();
        for (int p : pids()) {
            if (p == me) continue;
            String c = cmdline(p);
            if (c.equals(name) || c.startsWith(name + " ")) return p;
        }
        return -1;
    }

    /** Lista de procesos "de app" para elegir a mano. */
    public static List<String> appProcesses() {
        List<String> out = new ArrayList<>();
        int me = Process.myPid();
        for (int p : pids()) {
            if (p == me) continue;
            String c = cmdline(p);
            if (c.isEmpty() || c.contains(" ")) continue;
            if (c.contains(".") && !c.startsWith("android.") && !c.startsWith("com.android"))
                out.add(p + "|" + c);
        }
        return out;
    }

    // ---------- abrir / cerrar ----------

    public boolean open(int p) {
        close();
        pid = p;
        sh.exec("chmod 666 /proc/" + p + "/mem /proc/" + p + "/maps");
        try {
            raf = new RandomAccessFile("/proc/" + p + "/mem", "rw");
            return true;
        } catch (Exception e) {
            raf = null;
            return false;
        }
    }

    public void close() {
        try { if (raf != null) raf.close(); } catch (Exception ignored) {}
        raf = null;
        libBase = 0;
    }

    // ---------- lectura ----------

    public byte[] read(long addr, int len) {
        if (raf == null) return null;
        byte[] b = new byte[len];
        try {
            raf.seek(addr);
            raf.readFully(b);
            return b;
        } catch (Exception e) {
            try {
                for (int i = 0; i < len; i++) {
                    raf.seek(addr + i);
                    b[i] = raf.readByte();
                }
                return b;
            } catch (Exception e2) {
                return null;
            }
        }
    }

    public int readInt(long a) {
        byte[] b = read(a, 4);
        return b == null ? 0 : le32(b, 0);
    }

    public long readLong(long a) {
        byte[] b = read(a, 8);
        return b == null ? 0 : le64(b, 0);
    }

    public float readFloat(long a) {
        return Float.intBitsToFloat(readInt(a));
    }

    // ---------- escritura ----------

    public boolean write(long a, byte[] b) {
        if (raf == null) return false;
        try {
            raf.seek(a);
            raf.write(b);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean writeInt(long a, int v) { return write(a, leBytes(v, 4)); }
    public boolean writeFloat(long a, float v) { return writeInt(a, Float.floatToIntBits(v)); }
    public boolean writeLong(long a, long v) { return write(a, leBytes(v, 8)); }

    // ---------- utilidades ----------

    public static int le32(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8)
                | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
    }

    public static long le64(byte[] b, int o) {
        long v = 0;
        for (int i = 7; i >= 0; i--) v = (v << 8) | (b[o + i] & 0xFFL);
        return v;
    }

    public static byte[] leBytes(long v, int len) {
        byte[] b = new byte[len];
        for (int i = 0; i < len; i++) b[i] = (byte) ((v >>> (8 * i)) & 0xFF);
        return b;
    }

    /** Base de libil2cpp.so (para usar offsets estables). */
    public long libil2cppBase() {
        if (libBase != 0) return libBase;
        String[] names = {"libil2cpp.so", "libunity.so", "libUE4.so"};
        try {
            for (String name : names) {
                BufferedReader br = new BufferedReader(new FileReader("/proc/" + pid + "/maps"));
                String l;
                while ((l = br.readLine()) != null) {
                    if (l.contains(name)) {
                        libBase = Long.parseLong(l.split("-")[0], 16);
                        break;
                    }
                }
                br.close();
                if (libBase != 0) return libBase;
            }
        } catch (Exception ignored) {}
        return libBase;
    }

    /** Rangos de memoria legibles+escribibles. */
    public List<long[]> rwRanges() {
        List<long[]> out = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/" + pid + "/maps"));
            String l;
            while ((l = br.readLine()) != null) {
                String[] p = l.split("\\s+");
                if (p.length < 2 || !p[1].startsWith("rw")) continue;
                String[] se = p[0].split("-");
                long s = Long.parseLong(se[0], 16);
                long e = Long.parseLong(se[1], 16);
                long len = e - s;
                if (len > 0 && len <= 64L * 1024 * 1024) out.add(new long[]{s, e});
            }
            br.close();
        } catch (Exception ignored) {}
        return out;
    }

    /** Busca un entero (4 bytes) en los rangos rw. */
    public List<Long> scanInt(int value, int max) {
        List<Long> hits = new ArrayList<>();
        byte[] pat = leBytes(value, 4);
        for (long[] r : rwRanges()) {
            long a = r[0];
            while (a < r[1] - 4 && hits.size() < max) {
                int chunk = (int) Math.min(1024 * 1024, r[1] - a);
                if (chunk < 8) break;
                byte[] buf = read(a, chunk);
                if (buf == null) { a += chunk; continue; }
                for (int i = 0; i + 4 <= buf.length; i += 4) {
                    if (buf[i] == pat[0] && buf[i + 1] == pat[1]
                            && buf[i + 2] == pat[2] && buf[i + 3] == pat[3]) {
                        hits.add(a + i);
                        if (hits.size() >= max) break;
                    }
                }
                a += chunk - 4;
            }
        }
        return hits;
    }
              }
