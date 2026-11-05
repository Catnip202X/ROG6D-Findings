# 09 · Next leads

Where a future attempt should start, and why.

## 1. A working 5.10 GhostLock port exists — port *that*, not the 6.x one

`pirosap/aquos-r7-ghostlock` is a device-specific CVE-2026-43499 port for a
**Linux 5.10** Android device, and one of its validated builds is
`5.10.218-android12-9-00041-…` — the same 5.10.218 base as this device.

Why it matters: it does not use heap reclaim at all.

- it builds a **ghost task and a fake lock inside a zero run in BSS**, found by
  scanning the symbol table for an unused region ≥ 0x400 (their region is
  `__bss_start+0x1188`), so there is no slab grooming and no page reclaim to win
- it stamps the waiter through a **sigreturn** path, not `pselect`
- it ships a rich diagnostic set (`--all-poison` firing test, `--equal-ctl`
  control shot, `--task-y`, `--stamp-off`) designed exactly for a target with no
  kernel log — the same constraint as here

Their constants for 5.10: `MM_STRUCT_SZ 0x3c0`, waiter `prio 0x40 /
deadline 0x48`, task `task 0x30 / lock 0x38`, `selinux_state +0x2a41b98`,
`PHYS_OFFSET 0x40000000`, `enforcing` at offset 0 — every one of which agrees
with what was derived independently here.

To port it to this device, re-derive only the per-build numbers; the ones
already known for this kernel are in `05-offsets.md`:

```
OFF_INIT_TASK             0x279be80
OFF_INIT_CRED             0x27b0a60
OFF_SELINUX_STATE         0x2a41b98
OFF_LOG_BUF               0x2993d50
OFF_BSS_START             0x2989000
OFF_BSS_STOP              0x2a80c4c
OFF_GET_TASK_PID          0x175548
OFF_FUTEX_WAIT_REQUEUE_PI 0x294140
OFF_SEL_READ_ENFORCE      0x8cdfac
```

Still to determine for this build: the zero run inside BSS large enough for the
ghost task, and the sigreturn/`futex_wait_requeue_pi` frame geometry (both are
per-build and must be measured, not copied).

## 2. Get a kernel log

Any one of these would reopen the GhostLock route immediately:

- a `userdebug`/`eng` build of this firmware (adb root)
- a working pstore/ramoops path
- `/proc/kallsyms` readable (`kptr_restrict=0`)
- any kprobe access

Without it, every iteration costs a panic plus a reboot.

## 3. Kernel source

The ASUS GPL drop for this build would settle the remaining unknowns instantly
(real struct layouts, MTK deltas, the BSS map). The public mirror that was being
polled stayed at HTTP 502 throughout and is not a viable source.

## 4. A different bug class

Both routes tried here are UAF/write-primitive constructions that need precise
object-graph control. On a device with no read primitive, a bug whose success
criterion is directly observable from userspace (e.g. a permission check that
can be flipped and read back) would be far cheaper to iterate on.
