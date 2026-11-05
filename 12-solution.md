# 12 · Solution: temporary kernel root on ASUS ROG Phone 6D (5.10.218)

**Status: obtained and verified.** `uid=0` with SELinux set to permissive, from an
`adb shell` context on a locked-bootloader device.

```
[*] first-touch write_trigger=1 enforce=1->0
[+] SELinux permissive verified
[*] uid=0 euid=0 gid=1926268800 egid=0 enforce=0
[+] ROOT + permissive verified; entering direct shell
```

`getenforce` afterwards reads `Permissive`, and that is the post-process state of
the device, not a self-report. The change is **not persistent**: a reboot
restores the stock kernel state.

## Route

CVE-2026-43499 (rtmutex PI race, "GhostLock"), using the public Linux 5.10
implementation `pirosap/aquos-r7-ghostlock` as the base — not the 6.x lineage.
That implementation builds a ghost task and fake lock inside a zero run in BSS
and stamps the waiter through a sigreturn path, so there is no heap grooming and
no page reclaim to win. This is what made it viable on a device with no kernel
read primitive.

## Working configuration

```
--use-setattr --stamp3 --perm-pc --cred-swap --install-su --stamp-off 0x100
```

| Flag | Why |
|---|---|
| `--use-setattr` | this device's shell has `RLIMIT_RTPRIO soft=0 hard=0`, so the `SCHED_FIFO` trigger path is unavailable; `sched_setattr` staying in CFS is the unprivileged equivalent |
| `--stamp3` | the Amazon 5.10 geometry: `pc = write-value, rb_right = 0, rb_left = target`, so `rb_erase`'s `rb_set_parent_color(child, …)` performs the write |
| `--perm-pc` | page-aligned pointer as `pc` |
| `--cred-swap` | `task->real_cred/cred = init_cred`, which is the shortest path to UID 0 |
| `--install-su` | start the root `su` daemon afterwards |
| `--stamp-off 0x100` | **the per-build value; see below** |

## The only per-build unknown: the stamp offset

The waiter is stamped by overwriting the sigreturn frame's FPSIMD `vregs`
region. The offset from `vregs` to the `rt_mutex_waiter` depends on how the
signal frame is placed on the stack, which differs between builds. The reference
uses `0xe0` (and `0xf0` in its own validated command line). Both are wrong here —
every one of them crashes inside the first `__sched_setscheduler`.

Two independent measurements settled it.

### 1. Confirm the waiter formula with perf

`perf_event_paranoid = -1` on this build, so kernel register sampling works from
the shell context. Dumping samples inside `futex_wait_requeue_pi`
(`text+0x2941c0 … text+0x2944c0`) shows a consistent frame pointer and the
waiter in a register:

```
ip=...294274 sp=ffffffc03f333ba0 x2=ffffffc03f333c10 x27=ffffffc03f333c30
```

`x27 = sp + 0x90` — exactly the `add x1, sp, #0x90` the disassembly of our own
`futex_wait_requeue_pi` shows (`sub sp, sp, #0x1a0`, waiter at `sp+0x90`, at
`function+0x300`). So the geometry the port assumes is correct for this build;
only the sigreturn side was unknown.

### 2. Sweep the offset with a survivable criterion

`--equal-ctl` stamps a prio that is equal to the trigger's post-change prio, so
`rt_mutex_adjust_pi` returns before any chain walk. **Survival therefore means
the stamp landed where the kernel reads it**, and a crash means it did not:

```
--stamp-off 0xd0  -> panic
--stamp-off 0xf0  -> panic
--stamp-off 0x100 -> survives, stage reaches A, trigger returns r=0
```

The `0x100` result was then reproduced by a second, different run (the full
option set), which reached stage `A` with `uid=0`:

```
[T] stage=t tid=10260 r=0 errno=0 nice_seen=0
[T] stage=t tid=11631 r=0 errno=0 nice_seen=1
```

Two independent configurations agreeing on the same offset is what makes this a
measurement rather than a lucky guess.

## Notes for reproducing

- Run it **as the first thing after a boot**. The reference reports 10/10 success
  on a first run and kernel panics on a second run within the same boot; that
  matches what was observed here while iterating.
- Re-push the binary after every panic. `adb push` corrupted same-size binaries
  more than once here, and `/data/local/tmp` does not survive reliably.
- Verify md5 after every push, and never judge success or failure by "the device
  is alive" alone — an offline adb returns instantly with exit 0.
- The binary needs its per-build constants replaced (see `10-port-target.md`);
  the twelve values for this device are listed there.
