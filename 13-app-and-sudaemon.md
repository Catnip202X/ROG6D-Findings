# 13 · One-tap app, and the root `su` daemon

Two pieces: an Android app that runs the exploit from a home-screen icon, and the
root `su` daemon the exploit leaves behind, which is what makes the result
actually usable.

## Why an app needs Shizuku

The exploit must run as **shell** (uid 2000, `u:r:shell:s0`) — `perf_event_open`
with kernel sampling, trace access and the sched/futex paths all depend on that
domain. An ordinary app is `untrusted_app` and cannot get there.

So the app starts a process through **Shizuku**, whose server already runs as
uid 2000, and executes the exploit inside it. Shizuku needs no root: it can be
started from Android's wireless-debugging flow.

Measured on the target: `Shizuku.newProcess` is what actually works. A Shizuku
**user service** (bound via `bindUserService`) never delivered
`onServiceConnected` here, despite the service process starting as uid 2000; the
reflection call into the private `Shizuku.newProcess(String[], String[], String)`
did, and is what the app uses.

## What the app does

1. extracts `assets/gh510` out of its own APK to `/data/local/tmp/ghostlock510`
   (the exploit re-execs itself by that absolute path for its post-root shell and
   daemon, so the name must match exactly)
2. `chmod 755`
3. runs it with the verified option set
4. streams stdout to a scrolling view and reports `uid=0 / Permissive`

## The su daemon

With `--install-su`, once root is reached the exploit forks a daemon that:

```
[S] daemon start uid=0
[S] cred now uid=0 gid=2000
[S] su client installed: Success
[+] su daemon on 127.0.0.1:9999 (su=/data/local/tmp/su)
```

`/data/local/tmp/su` is copied from `/proc/self/exe` and owned by root, so an
unprivileged process cannot replace it. Verified:

```
$ /data/local/tmp/su -c id
uid=0(root) gid=2000(shell) groups=2000(shell) context=u:r:kernel:s0

$ /data/local/tmp/su -c getenforce
Permissive
```

Note the context: `u:r:kernel:s0`, i.e. the kernel domain, not just uid 0 in the
shell domain.

### The `tty` pitfall

The `su` client tries to locate the caller's controlling terminal, so an
interactive invocation from a context without a tty fails with
`can't find tty for …`. Always pass a command:

```sh
/data/local/tmp/su -c 'id'            # works
/data/local/tmp/su -c 'ls /data'      # works
/data/local/tmp/su                    # fails without a tty
```

Because the daemon persists for the life of the boot, the exploit does **not**
need to be re-run per command — only after a reboot.

## Operational rules

| Rule | Why |
|---|---|
| run **once per boot**, as the first thing after boot | the reference reports 10/10 on a first run and a kernel panic on a second run within the same boot; this was reproduced here |
| restart Shizuku after every reboot | it does not survive a reboot |
| re-install the app if it disappears after a panic | it was observed to be rolled back once after a kernel panic |
| expect `data/local/tmp` to be wiped on reboot | the app re-extracts the binary each run |

## Building

`android-app/` holds the project as shipped. It needs:

- JDK 21, Android SDK (compileSdk 34), Gradle 9.x
- `dev.rikka.shizuku:api` + `:provider` 13.1.5
- the exploit binary placed at `app/src/main/assets/gh510` before building
  (**not** included in this repository)

Two build notes that cost time here:

- AGP 8 needs `buildFeatures { aidl true }` or the AIDL interface is never compiled
- the Shizuku dependency pulls Kotlin stdlib artifacts that collide
  (`kotlin-stdlib-jdk7` / `-jdk8` vs `kotlin-stdlib`); exclude the two jdk
  artifacts and force a single `kotlin-stdlib` version

## Known limitations

- temporary: a reboot restores stock state
- no icon was designed; the launcher shows the default Android icon under the
  label `ROG6D 一键提权`
- the app has no fallback if Shizuku is not running — it reports the state and
  waits
