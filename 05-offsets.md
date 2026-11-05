# 05 · Offsets measured on this kernel

All values derived on this exact image (`_text = 0xffffffc008000000`) and,
where possible, cross-checked against a second independent build of the same
device family (`asus_ai2203`, 5.10.101).

## Slab objects (`/proc/slabinfo`)

| Cache | objsize | objs/slab | pages/slab |
|---|---|---|---|
| `posix_timers_cache` | 264 (`0x108`) | 31 | 2 (order-1) |
| `cred_jar` | 192 (`0xc0`) | 21 | 1 |
| `task_struct` | 4736 (`0x1280`) | 6 | 8 |
| `filp` | 320 | 25 | 2 |
| `signal_cache` | 1152 | 28 | 8 |
| `sighand_cache` | 2112 | 15 | 8 |
| `mm_struct` (from cache create) | `0x3c0` | — | — |

## `task_struct`

| Field | Offset | Source |
|---|---|---|
| `cred` | `0x780` | `__sys_setuid` disasm |
| `real_cred` | `0x778` | commit path |
| `prio` / `normal_prio` | `0x84` / `0x8c` | `task_blocks_on_rt_mutex`, `rt_mutex_setprio` |
| `pi_lock` | `0x86c` | `task_blocks_on_rt_mutex` |
| `pi_waiters` | `0x880` | rtmutex |
| `pi_top_task` | `0x890` | `rt_mutex_setprio` |
| `pi_blocked_on` | `0x898` | `task_blocks_on_rt_mutex` |
| `usage` | `0x40` | `__put_task_struct` |
| `posix_cputimers` | `0x728` (rb_root) / `0x730` (next) | `posix_cpu_timers_exit` |

## `cred` (5.10 layout, `sizeof = 0xa8`, slab rounds to `0xc0`)

```
usage 0x00 | uid 0x04 | gid 0x08 | suid 0x0c | sgid 0x10
euid 0x14 | egid 0x18 | fsuid 0x1c | fsgid 0x20
cap_inh 0x28 | cap_prm 0x30 | cap_eff 0x38 | cap_bnd 0x40 | cap_amb 0x48
security 0x78 | user_ns 0x80 | ucounts 0x88 | group_info 0x90
```

## `k_itimer`

| Field | Offset |
|---|---|
| `it.cpu.node` (rb node) | `0x78` |
| `it.cpu.pid` | `0xa0` |
| object size | `0x108` |

## `rt_mutex_waiter` (5.10 — no `wake_state`, no `ww_ctx`; 10 words)

```
tree_entry 0x00 | pi_tree_entry 0x18 | task 0x30 | lock 0x38 | prio 0x40 | deadline 0x48
```

## Stack frames

| Function | Frame | Notes |
|---|---|---|
| `__arm64_sys_pselect6` | `0xa0` | |
| `core_sys_select` | `0x1c0` | `stack_fds` at `sp+0x50` |
| `__arm64_sys_futex` | `0x90` | |
| `do_futex` | `0x70` | |
| `futex_wait_requeue_pi` | `0x1a0` | waiter at `sp+0x90` |
| ⇒ waiter − stack_fds | **0** | they coincide |

## Symbols (static, add per-boot KASLR slide)

| Symbol | Address | Offset |
|---|---|---|
| `_text` | `0xffffffc008000000` | 0 |
| `init_task` | `0xffffffc00a79be80` | `0x279be80` |
| `init_cred` | `0xffffffc00a7b0a60` | `0x27b0a60` |
| `selinux_state` | `0xffffffc00aa41b98` | `0x2a41b98` |
| `__bss_start` | `0xffffffc00a989000` | `0x2989000` |
| `__bss_stop` | `0xffffffc00aa80c4c` | `0x2a80c4c` |
| `__log_buf` | `0xffffffc00a993d50` | `0x2993d50` |
| `do_timer_create` | `0xffffffc00827895c` | `0x27895c` |
| k_itimer alloc site | `0xffffffc0082789f0` | low21 `0x789f0` |
| `k_itimer_rcu_free` free site | `0xffffffc0082786ec` | low21 `0x786ec` |
| `posix_cpu_timer_del` | `0xffffffc00827f3d0` | `0x27f3d0` |
| `posix_cpu_timers_exit` | `0xffffffc00827cc04` | `0x27cc04` |
| `panic_on_warn` | `0xffffffc00a789dc0` | `0x2789dc0` |
| `get_task_pid` | `0xffffffc008175548` | `0x175548` |
| `futex_wait_requeue_pi` | `0xffffffc008294140` | `0x294140` |
| `sel_read_enforce` | `0xffffffc0088cdfac` | `0x8cdfac` |
| `__sys_setuid` | `0xffffffc00815dbcc` | `0x15dbcc` |
| `remove_waiter` | `0xffffffc0081e9b98` | `0x1e9b98` |
| `rt_mutex_adjust_prio_chain` | `0xffffffc0081ea230` | `0x1ea230` |

## Linear map

| Item | Value |
|---|---|
| base (correct) | `0xffffff7fc0000000` |
| textbook arm64 39-bit value (wrong here) | `0xffffff8000000000` |
| reason | `PHYS_OFFSET = 0x40000000`, RAM starts at physical 0x40000000 |
| delta in pfns | `0x40000` pages = 1 GiB |
