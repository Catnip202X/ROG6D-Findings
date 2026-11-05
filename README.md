# ROG6D Findings

Kernel exploitation research notes for the **ASUS ROG Phone 6D (ASUS_AI2203_B)**.

| Item | Value |
|---|---|
| SoC | MediaTek MT6983 |
| Kernel | `5.10.218-android12-9-00023-g7e657aa362f2-ab12171750` |
| Build | `CN-34.0804.1204.107` (Android 14) |
| Bootloader | locked |
| SELinux | Enforcing |
| Mitigations | KASLR, SLAB_FREELIST_HARDENED, HARDENED_USERCOPY |
| Config notes | `USER_NS` off, `NF_TABLES` off, `FUTEX_PI` on, `IO_URING` on, `PREEMPT=y`, `RT_MUTEXES=y`, no `DEBUG_RT_MUTEXES` |

## TL;DR

Two public Android LPE candidates were ported and evaluated on this exact kernel.
**Both were closed with concrete evidence**, not with "it didn't work":

| CVE | Verdict | Basis |
|---|---|---|
| CVE-2026-99203 (pipe/FIFO fasync UAF) | not reachable | allocation uses `GFP_KERNEL` without `__GFP_ACCOUNT`, so no memcg lever; global memory pressure panics Android (`system_is_dead_on_oom`) before any allocation can fail |
| CVE-2026-98260 (exec / POSIX CPU timer UAF) | not exploitable here | the reap-time cleanup never touches the stale node on this kernel — proven with a whole-page poison probe |

The port of CVE-2026-98260 was otherwise **fully working**: trigger confirmed,
heap reclaim confirmed (`free_seen=1`, target page reclaimed, frame distance `d=0`),
all structure offsets and the linear-map base derived and cross-checked.

Documents:

- `01-environment.md` — what is and is not reachable from a shell context
- `02-cve-2026-43499-ghostlock.md` — rtmutex PI-race route
- `03-cve-2026-98260-posix-timer.md` — timer UAF route (the main port)
- `04-kernel-offsets.md` — every offset measured on this kernel
- `05-methodology.md` — techniques worth reusing
- `06-negative-results.md` — the discard list, so nobody repeats it
