# 04 · Offsets measured on 5.10.218 (ASUS_AI2203_B, MT6983)

Every value below was derived on this exact kernel image and, where possible,
cross-checked against a second independent build of the same device family
(`asus_ai2203`, 5.10.101). Text base of the analysed image:
`0xffffffc008000000`.

## Slab objects (`/proc/slabinfo`)

| Cache | objsize | objs/slab | pages/slab |
|---|---|---|---|
| `posix_timers_cache` | 264 (`0x108`) | 31 | 2 (order-1) |
| `cred_jar` | 192 (`0xc0`) | 21 | 1 |
| `task_struct` | 4736 (`0x1280`) | 6 | 8 |
| `filp` | 320 | 25 | 2 |
| `signal_cache` | 1152 | 28 | 8 |
| `sighand_cache` | 2112 | 15 | 8 |

## `task_struct`

| Field | Offset | Source |
|---|---|---|
| `cred` | `0x780` | `__sys_setuid` disasm |
| `posix_cputimers` head | `0x728` (rb_root) / `0x730` (next) | `posix_cpu_timers_exit` disasm |
| `pi_lock` | `0x86c` | `rtmutex` disasm |
| `pi_waiters` | `0x880` | `rtmutex` disasm |
| `pi_blocked_on` | `0x898` | `rtmutex` disasm |

## `cred` (5.10 layout)

```
usage 0x00 | uid 0x04 | gid 0x08 | suid 0x0c | sgid 0x10
euid 0x14 | egid 0x18 | fsuid 0x1c | fsgid 0x20
cap_inh 0x28 | cap_prm 0x30 | cap_eff 0x38 | cap_bnd 0x40 | cap_amb 0x48
security 0x78 | user_ns 0x80 | ucounts 0x88 | group_info 0x90
sizeof 0xa8 (slab rounds to 0xc0)
```

## `k_itimer`

| Field | Offset | Source |
|---|---|---|
| `it.cpu.node` (rb node) | `0x78` | `posix_cpu_timer_del`, `posix_cpu_timer_set` |
| `it.cpu.pid` | `0xa0` | `posix_cpu_timer_del` (`pid_task` inlined) |
| object size | `0x108` | slabinfo |

## Linear map

| Item | Value | Source |
|---|---|---|
| base | `0xffffff7fc0000000` | histogram solve over observed frees |
| textbook value (WRONG here) | `0xffffff8000000000` | — |
| delta | `0x40000000` (1 GiB) | |

## Useful kernel symbols (static image addresses)

| Symbol | Address |
|---|---|
| `do_timer_create` | `0xffffffc00827895c` |
| k_itimer alloc call site | `0xffffffc0082789f0` (low21 `0x789f0`) |
| `k_itimer_rcu_free` → `kmem_cache_free` | `0xffffffc0082786ec` (low21 `0x786ec`) |
| `posix_cpu_timer_del` | `0xffffffc00827f3d0` |
| `posix_cpu_timers_exit` | `0xffffffc00827cc04` |
| `posix_cpu_timer_set` | `0xffffffc00827ed04` |
| `__sys_setuid` | `0xffffffc00815dbcc` |
| `panic_on_warn` | `0xffffffc00a789dc0` |
| `selinux_state` | `0xffffffc00aa41b98` |

Static addresses above are offsets from the image base; add the per-boot KASLR
slide, which the running exploit derives from the timer call site.
