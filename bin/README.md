# bin/ — prebuilt exploit, EXACTLY ONE BUILD

**This binary is hard-coded for a single device build. It will panic the kernel
on anything else.**

## Target

| Field | Required value |
|---|---|
| Device | ASUS ROG Phone 6D, `ASUS_AI2203_B` |
| Kernel | `5.10.218-android12-9-00023-g7e657aa362f2-ab12171750` |
| Build | `CN-34.0804.1204.107` (Android 14, MT6983) |

Confirm on the device before running anything:

```sh
uname -r          # must equal the kernel string above, exactly
getprop ro.build.display.id
```

## Why it is build-locked

Twelve values are compiled in — link-time symbol offsets for `init_task`,
`init_cred`, `selinux_state`, `__log_buf`, `__bss_start/stop`, the
`__bss_start+0x1188` zero-run used as scratch, `get_task_pid`, `task_state`,
`futex_wait_requeue_pi`, `sel_read_enforce` — plus one measured stack-geometry
constant (`--stamp-off 0x100`). Any mismatch means the write primitive targets
the wrong kernel object, which as a rule means an immediate panic, not a failed
attempt.

The offsets for this build are listed in `../05-offsets.md`; how the stamp
offset was measured is in `../12-solution.md`. Porting to another build is a
matter of replacing those constants, not of changing the technique.

## Provenance

| | |
|---|---|
| size | 2179168 bytes |
| md5 | `1284133f9c23c8818a293997f4650344` |
| sha256 | `3938a9ab562621992dc914c1180ad095ef7499ea7775c8d00e2ffb38085058a3` |
| built | NDK 29 (clang 21), `aarch64-linux-android29-clang -O2 -static -pthread` |
| base | `pirosap/aquos-r7-ghostlock` with the per-build constants replaced |
| tested | this device only, first run of a boot |

## Usage

```sh
adb push ghostlock510-ai2203-5.10.218 /data/local/tmp/ghostlock510
adb shell chmod 755 /data/local/tmp/ghostlock510
adb shell /data/local/tmp/ghostlock510 \
    --use-setattr --stamp3 --perm-pc --cred-swap --install-su --stamp-off 0x100
```

The file name must be `ghostlock510`: the exploit re-execs itself by that
absolute path for its post-root shell and its `su` daemon.

Run it **once per boot, as the first thing after boot**. A second run in the same
boot panics the kernel. A reboot restores stock state.
