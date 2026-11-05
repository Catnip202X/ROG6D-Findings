# ROG6D Findings

Complete research record: kernel exploitation attempts against the
**ASUS ROG Phone 6D (`ASUS_AI2203_B`)**, locked bootloader, no root obtained.

Everything here was produced on the real device. Negative results are documented
in the same detail as the positive ones — the discard list is the most valuable
part of this repository.

| Item | Value |
|---|---|
| Device | ASUS ROG Phone 6D, `ASUS_AI2203_B` |
| SoC | MediaTek MT6983 |
| Kernel | `5.10.218-android12-9-00023-g7e657aa362f2-ab12171750` |
| Build | `CN-34.0804.1204.107` (Android 14) |
| Bootloader | locked |
| SELinux | Enforcing |
| Mitigations | KASLR (2 MB aligned), SLAB_FREELIST_HARDENED, HARDENED_USERCOPY |
| Config | `USER_NS` off, `NF_TABLES` off, `FUTEX_PI` on, `IO_URING` on, `PREEMPT=y`, `RT_MUTEXES=y`, no `DEBUG_RT_MUTEXES`, `SELINUX_DEVELOP=y`, `SELINUX_DISABLE` off |

## Outcome summary

| Route | State | Closed by |
|---|---|---|
| CVE-2026-99203 — pipe/FIFO `fasync` UAF | not reachable | allocation is `GFP_KERNEL` without `__GFP_ACCOUNT` (no memcg lever); global memory pressure makes Android panic (`system_is_dead_on_oom`) before any allocation can fail |
| CVE-2026-43499 — rtmutex PI-race (GhostLock) | port attempted, blocked on observability | no kernel log of any kind; every variant that keeps the real owner block corrupts control flow with no trace |
| CVE-2026-98260 — exec / POSIX CPU timer UAF | **fully ported, proven not exploitable here** | the reap-time cleanup never touches the stale node — proven with a whole-page poison probe |

## What was actually achieved

- Rebuilt kernel exploits from source and drove them from an `adb shell` context
- Full decompilation-derived structure map of this exact kernel (see `05-offsets.md`)
- Working heap-reclaim primitive: freed slab page re-taken with frame distance `d=0`
  (the exact same physical frame), reproduced across many runs
- Byte-exact profile editor for the GhostLock binary format
- A set of diagnostic techniques that work without any kernel introspection
  (see `07-methodology.md`)
- **Discovery that `perf_event_paranoid = -1` on this build**: kernel IP and
  register sampling are available from the shell context, giving a userspace
  KASLR leak (`_text = 0xffffffef80c00000` measured) and per-build geometry
  measurement — the observation channel the earlier work lacked

## Documents

| File | Contents |
|---|---|
| `01-device.md` | device facts, kernel config, what the boot chain allows |
| `02-environment.md` | what a `u:r:shell:s0` context can and cannot do on this build |
| `03-ghostlock-route.md` | CVE-2026-43499: geometry, profiles, isolation experiments, crash signature |
| `04-timer-route.md` | CVE-2026-98260: the full port, constants, base calibration, final verdict |
| `05-offsets.md` | every offset measured on this kernel |
| `06-tooling.md` | tools written for this work |
| `07-methodology.md` | reusable techniques (trace triggering, base solving, poison probes) |
| `08-negative-results.md` | everything ruled out, with the evidence that ruled it out |
| `09-next-leads.md` | open leads and what a future attempt should start from |
