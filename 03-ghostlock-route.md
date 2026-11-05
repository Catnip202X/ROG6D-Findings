# 03 · CVE-2026-43499 — rtmutex PI-race (GhostLock)

Route: KernelSnitch `mm_struct` leak → heap spray → fake kernel page →
PI-race write primitive. Three carrier variants exist upstream:
`select_stack`, `multicast_waiter`, `tcp_zerocopy`.

## Stack geometry derived from this kernel

Measured by disassembling the target image, not copied from upstream:

| Function | Frame |
|---|---|
| `__arm64_sys_pselect6` | `0xa0` |
| `core_sys_select` | `0x1c0`, `stack_fds` at `sp+0x50` |
| `__arm64_sys_futex` | `0x90` |
| `do_futex` | `0x70` |
| `futex_wait_requeue_pi` | `0x1a0`, `rt_mutex_waiter` at `sp+0x90` |

⇒ `D = waiter_abs − stack_fds_abs = 0`; the two objects coincide.

`rt_mutex_waiter` layout, confirmed from `task_blocks_on_rt_mutex`
(`[x22,#0x40]` = prio, `[x22,#0x48]` = deadline):

```
tree_entry 0x00 .. 0x17
pi_tree_entry 0x18 .. 0x2f
task 0x30 | lock 0x38 | prio 0x40 | deadline 0x48
```

5.10 has **no `wake_state` / `ww_ctx`** fields — the waiter is 10 words, not 13.
The compact fd-set table (`compact_waiter=1`, `waiter_shift=-2`) maps
task→0x30, lock→0x38 and the tree fields to 0x00/0x10/0x18/0x28, matching the
kernel exactly.

## What was proven to work

- Trigger chain executes: requeue returns `EDEADLK (35)`, so `remove_waiter()`
  runs with `current != waiter->task`
- With the requeue step skipped (`NO_RACE`), `select()` returns 0 and the device
  survives — the route machinery itself is sound
- Binary profiles can be edited byte-exactly (16-byte header + sections of
  `key → u64` entries), so any constant can be swept systematically

## The crash

Every variant that keeps the real owner block dies the same way:

```
Unable to handle kernel NULL pointer dereference at virtual address 0000000000000008
ESR=0x96000005   WnR=0
Internal error: Oops: 96000005 [#1] PREEMPT SMP
[<4604091028253616088>] invalid lr
```

Control-flow corruption, no call trace, no RCU stall, no watchdog line.
`rt_mutex_adjust_prio_chain` contains **no indirect branches**, so the bad LR is
not produced there directly.

## Not an offset problem — cross-checked against a second kernel

A second, independently built kernel of the same device family was obtained
(`asus_ai2203`, 5.10.101, from a public device dump) and compared:

- `remove_waiter` — instruction-for-instruction identical to this device
- `rt_mutex_adjust_prio_chain` — identical field-offset usage histogram
  (`0x30`×3, `0x38`×6, `0x86c`×3, `0x880`×5, `0x898`×3)

So MediaTek did not modify this code between those versions, and the offsets fed
to the exploit are correct.

## Why it stopped

There is no way to learn *which field* was walked. `/proc/kallsyms`, `dmesg`,
pstore and kprobes are all denied, and `logcat -b kernel` loses the tail when
the device goes down. Blind iteration on the fake object graph costs one panic +
reboot (2–4 min) per guess, and several "no change" results turned out to be
artefacts of a corrupted binary push (see `07-methodology.md`).

A working 5.10 port of this same CVE on another device exists publicly and is
recorded in `09-next-leads.md` — that is where a future attempt should resume.
