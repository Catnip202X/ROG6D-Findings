# ROG6D Findings

Research record for kernel exploitation on the **ASUS ROG Phone 6D
(`ASUS_AI2203_B`)** — locked bootloader, Android 14, MediaTek MT6983.

**Outcome: temporary kernel root obtained.** CVE-2026-43499 (rtmutex PI race,
"GhostLock") works on this build: `uid=0` in the kernel domain with SELinux
`Permissive`, driven either from `adb shell` or from a one-tap app, and leaving a
root `su` daemon behind until the next reboot.

Everything here was produced on the real device. Negative results are documented
in the same detail as the positive ones — the discard list is the most valuable
part of the repository.

| Item | Value |
|---|---|
| Device | ASUS ROG Phone 6D, `ASUS_AI2203_B` |
| SoC | MediaTek MT6983 |
| Kernel | `5.10.218-android12-9-00023-g7e657aa362f2-ab12171750` |
| Build | `CN-34.0804.1204.107` (Android 14) |
| Bootloader | locked |
| Mitigations | KASLR (2 MB aligned), SLAB_FREELIST_HARDENED, HARDENED_USERCOPY |
| Config | `USER_NS` off, `NF_TABLES` off, `FUTEX_PI` on, `IO_URING` on, `PREEMPT=y`, `RT_MUTEXES=y`, no `DEBUG_RT_MUTEXES`, `SELINUX_DEVELOP=y`, `SELINUX_DISABLE` off |

## What you get

```
$ /data/local/tmp/su -c id
uid=0(root) gid=2000(shell) groups=2000(shell) context=u:r:kernel:s0

$ /data/local/tmp/su -c getenforce
Permissive
```

The `su` daemon persists for the life of the boot, so the exploit is run **once
per boot**, not once per command. A reboot restores stock state.

## Quick start

**From a computer**

1. push the exploit binary and run it with the verified option set
   (see `12-solution.md`)
2. it prints the leaks, then `ROOT + permissive verified`
3. use `/data/local/tmp/su -c '…'` from then on

**From the phone (one tap)**

1. start Shizuku (wireless debugging; no root needed)
2. open the app in `android-app/`, tap the single button
3. same result, same `su` helper

Rules that matter: run **once per boot** (a second run in the same boot panics
the kernel), restart Shizuku after every reboot, and always call the helper as
`su -c '…'` — a bare `su` wants a tty and fails without one.

## Route summary

| Route | State | Why |
|---|---|---|
| CVE-2026-43499 — rtmutex PI-race (GhostLock) | **SUCCESS** | ported the public 5.10 implementation, replaced 11 per-build constants, and measured the sigreturn stamp offset (`0x100`) with perf sampling plus a survivable-offset sweep |
| CVE-2026-98260 — exec / POSIX CPU timer UAF | fully ported, **not exploitable here** | the reap-time cleanup never touches the stale node — proven with a whole-page poison probe |
| CVE-2026-99203 — pipe/FIFO `fasync` UAF | not reachable | the allocation is `GFP_KERNEL` without `__GFP_ACCOUNT`, so there is no memcg lever; global memory pressure makes Android panic (`system_is_dead_on_oom`) before any allocation can fail |

## What was learned

- **`perf_event_paranoid = -1` on this build.** Kernel IP and register sampling
  work from a plain shell context. That is a userspace KASLR leak
  (`_text` measured directly) and, more importantly, the per-build geometry
  measurement the earlier attempts had no way to do.
- **The linear map does not start where the textbook says.** `PHYS_OFFSET` is
  `0x40000000` here, so the base is `0xffffff7fc0000000`. Using the textbook
  `PAGE_OFFSET` shifts every pfn by 1 GiB and silently breaks every reclaim
  check while still printing plausible numbers.
- **A code path that "should" run may not.** A whole-page poison probe turned
  "is my payload wrong?" into a single binary fact and closed the timer route.
- **Full decompilation still beats guessing** when a panic costs minutes: the
  decisive facts in both routes came from disassembly, not from runs.

## Documents

| File | Contents |
|---|---|
| `01-device.md` | device facts, kernel config, image layout, the `PHYS_OFFSET` trap |
| `02-environment.md` | what a `u:r:shell:s0` context can and cannot do, incl. the trace trigger bypass and perf |
| `03-ghostlock-route.md` | CVE-2026-43499: stack geometry, waiter layout, isolation experiments, crash signature |
| `04-timer-route.md` | CVE-2026-98260: the port, constants, base calibration, final verdict |
| `05-offsets.md` | every offset measured on this kernel |
| `06-tooling.md` | tools written for this work |
| `07-methodology.md` | reusable techniques (call-site fingerprints, base solving, poison probes, panic-proof marks) |
| `08-negative-results.md` | everything ruled out, with the evidence that ruled it out |
| `09-next-leads.md` | open leads and where a future attempt should start |
| `10-port-target.md` | the working 5.10 reference, constant mapping, BSS scratch analysis |
| `11-port-log.md` | iteration log of the port, including the dead ends |
| `12-solution.md` | **the working configuration, verification output, and how `stamp-off 0x100` was measured** |
| `13-app-and-sudaemon.md` | the one-tap app, why it needs Shizuku, the `su` daemon, and the tty pitfall |
| `android-app/` | the app project, source only (the exploit binary is not included) |
