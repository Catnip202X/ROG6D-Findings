package com.rog6d.onetap;

import java.io.*;

/**
 * Runs inside the process Shizuku starts as the shell user (uid 2000,
 * u:r:shell:s0) — the domain the exploit requires.
 */
public class UserService extends IUserService.Stub {
    private static final String BIN = "/data/local/tmp/gh510";
    private String apk;

    public UserService() { }
    public UserService(android.content.Context context) { this.apk = context.getPackageCodePath(); }

    @Override public void destroy() { System.exit(0); }
    @Override public void exit() { System.exit(0); }

    @Override public String run(String args) {
        StringBuilder log = new StringBuilder();
        try {
            if (apk == null) apk = resolveApk();
            extract(log);
            Process p = Runtime.getRuntime().exec(new String[]{
                    "/system/bin/sh", "-c", BIN + " " + args});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) log.append(line).append('\n');
            log.append("[exit] ").append(p.waitFor()).append('\n');
        } catch (Throwable t) {
            log.append("[error] ").append(t).append('\n');
        }
        return log.toString();
    }

    private String resolveApk() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "/system/bin/sh", "-c", "pm path com.rog6d.onetap | head -1 | cut -d: -f2"});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String s = r.readLine();
            if (s != null && s.startsWith("/")) return s.trim();
        } catch (Throwable ignore) { }
        return null;
    }

    private void extract(StringBuilder log) throws IOException {
        File f = new File(BIN);
        if (f.exists() && f.length() > 1000000) return;
        if (apk == null) { log.append("[!] apk path unknown\n"); return; }
        java.util.zip.ZipFile zf = new java.util.zip.ZipFile(apk);
        try {
            java.util.zip.ZipEntry e = zf.getEntry("assets/gh510");
            if (e == null) { log.append("[!] asset missing\n"); return; }
            InputStream in = zf.getInputStream(e);
            FileOutputStream out = new FileOutputStream(BIN);
            byte[] buf = new byte[1 << 16];
            int n; while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close(); in.close();
            new File(BIN).setExecutable(true, false);
            log.append("[i] extracted ").append(f.length()).append(" bytes\n");
        } finally { zf.close(); }
    }
}
