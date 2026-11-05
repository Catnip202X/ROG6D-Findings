# 04 · CVE-2026-98260 — exec / POSIX CPU timer UAF

A 6.6-targeted exploit was ported to `5.10.218` and carried all the way to a
proven negative. This file records both halves: what worked, and the evidence
that closed it.

## The bug as documented upstream

A non-leader thread arms a per-thread CPU timer, then `execve()`. If the exec
fails after `bprm->point_of_no_return`, `exit_itimers()` frees the `k_itimer`
while its timerqueue node is still queued, and reaping the task later erases
that freed node → slab-use-after-free.

## Does this kernel even have it?

`posix_cpu_timer_del` **is** PID-indexed here — `pid_task()` is inlined:

```
ldr  x8, [x0, #0xa0]        ; timer->it.cpu.pid
add  x8, x8, x9, lsl #3
ldar x8, [x8]               ; pid->tasks[type].first
sub  x21, x8, #0x638        ; container_of → task_struct
cbz  x21, bail              ; lookup failed → node left queued
```

So the shape the CVE needs exists. (An earlier conclusion in this work — that
5.10 uses a raw task pointer and is therefore immune — was wrong; it came from
reading only the `bl` list and missing the inlined lookup. Corrected.)

`begin_new_exec` ordering also matches the vulnerable version:

```
release_task            (de_thread internals)
fput_many
inode_permission ×2
mm_trace_rss_stat
exec_mmap               ← the failure point
posix_cpu_timers_exit   ← cleanup still after the failure point
```

## Port work — all verified correct

| Item | Value | How |
|---|---|---|
| `k_itimer` node offset | `0x78` | `posix_cpu_timer_del` disasm |
| `k_itimer` object size | `0x108` | slabinfo + trace `bytes_req/bytes_alloc` |
| `task_struct.cred` | `0x780` | `__sys_setuid` disasm |
| `cred.uid` | `0x4` | `__sys_setuid` disasm |
| `sizeof(task_struct)` | `0x1280` | slabinfo |
| timer alloc call site | `do_timer_create+0x94` | trace raw record + disasm |
| its low-21 mask | `0x789f0` | invariant under 2 MB-aligned KASLR |
| `k_itimer_rcu_free` free site | `0x82786ec` | same fingerprint technique |
| linear-map base | `0xffffff7fc0000000` | histogram solve (below) |

### Solving the linear-map base

Dump your own slab pfns `S` (from `kmem_cache_alloc` pointers) and the observed
order-1 `mm_page_free` pfns `F`, then histogram `S − F` over all pairs. The
correct base is a sharp peak:

```
delta = −0x40000 pages  →  304 / 310 slabs matched
⇒ base = 0xffffff8000000000 + 0x40000000 = 0xffffff7fc0000000
```

This is `PHYS_OFFSET = 0x40000000` (RAM does not start at physical 0). Using the
textbook base shifts every pfn by 1 GiB and makes every reclaim check miss
silently.

## What was made to work

```
[+] plant1 free_seen=1 burst hit=1 after 33ms
    (pf_total=131 pf_o1=61 nearest=0x10bc0c d=0 want=0x10bc0c)
[dbg] k_itimer frees = 4096
```

- trigger path confirmed: `zombie code=2 (CLD_KILLED) status=9`
- the victim slab page reaches the buddy allocator and is re-taken by the spray
  at the **exact same physical frame** (`d=0`), reproduced across many runs
- all 4096 `k_itimer` objects are freed (counted by call-site fingerprint,
  4096/4097 every run)

That is a complete, working "free → reclaim" primitive on this kernel.

## Why it is nevertheless not exploitable here

Three facts, the third decisive:

1. `posix_cpu_timers_exit()` enters its loop with
   `ldr x20,[x19,#0x730]` / `cbz x20, exit` — it walks `head->next` and does
   nothing at all if that is NULL.
2. `posix_cpu_timer_del()` runs during `do_exit()`, resolves the owner and
   unlinks the node, updating `head->next`.
3. **Poison-page probe**: the reclaimed page was filled end-to-end with
   `0xdeaddeaddeaddead`. If the reap path ever walked that node it would fault
   on a non-canonical pointer. With a confirmed real hit
   (`free_seen=1`, `d=0`, `hit=1`) the device **stayed alive for the whole run**.

Conclusion: the cleanup completes during task exit, so the reap-time second
stage the exploit depends on never happens. The write primitive cannot be built
— not because a parameter is wrong, but because the code path does not execute
at that moment.

Cross-check: the same external 5.10 port that documents `PHYS_OFFSET 0x40000000`
also reports `selinux_state` at `+0x2a41b98` on its build — the value this work
derived independently for this device.
