# 08 · Negative results

Everything ruled out, with the evidence that ruled it out. This is the part
worth reading before starting anything new.

## CVE-2026-99203 — pipe/FIFO `fasync` UAF

| Attempt | Result |
|---|---|
| reach the double `F_SETFL O_ASYNC` registration | works — preconditions all confirmed (mkfifo, `open O_RDWR`, `F_SETOWN`, SIGIO delivered) |
| make the second `fasync_helper` allocation fail | impossible from userspace: `fasync_alloc()` is `kmem_cache_alloc(fasync_cache, GFP_KERNEL)` with **no `__GFP_ACCOUNT`**, so there is no memcg lever |
| global memory pressure down to ~40 MB | Android panics first — `reboot reason: kernel_panic, system_is_dead_on_oom` — the OOM killer fires before the allocation failure the bug needs |

## CVE-2026-98260 — timer UAF port

| Attempt | Result |
|---|---|
| textbook `PAGE_OFFSET` for pfn math | silently wrong by 1 GiB; every reclaim check missed while printing believable numbers |
| node offset other than `0x78` | wrong — `0x78` confirmed twice by disassembly |
| both `compact_waiter` word tables | irrelevant; the erase is never reached |
| target bias `+0x78` | irrelevant; same reason |
| corrected zero-write geometry (`parent = target−8`, children 0) | irrelevant; same reason |
| fake-cred family (`task->cred` swap) | fails earlier on 5.10: the blob placement spills the page (`off=0x1620`, `blob=0x1f00`) |
| default trace ring size | records overwritten before reading (628k scanned, 0 matching) |
| event `filter` / `enable` writes | denied; only `trigger` works |
| spray disabled | no change — pages still not touched at reap |
| clean boot, empty slab cache (9 slabs) | no change |
| disabled `tag-sweep` in the leak stage | kept, but unrelated to the verdict |

## CVE-2026-43499 — GhostLock route

Each of these was a real device run, not a guess:

word tables (compact 0/1) · `waiter_shift` −2/−4 · three multicast geometries ·
`tcp_zerocopy` (a standalone probe shows `TCP_ZEROCOPY_RECEIVE` returns `EINVAL`
even for a textbook call on this kernel — the feature is unusable here) ·
`enter_delay` 5/50/500 ms · `chain_delay` 2/100 ms · route wait timeouts ·
both candidate `selinux` targets · six data symbols that had silently come from
a built-in profile for a different kernel · `SKB_DATA_DELTA` 0/−0x1000 ·
prio layout · three values of `waiter->task` · `NO_WORDS` (no fake words at all).

`NO_WORDS` and the default profile both die at the same `EXEC: pre-select
attempt=1`, which proves the crash is independent of the planted data and
requires a real owner block on the chain lock.

## Environment traps

| Assumption | Reality |
|---|---|
| `adb push` is faithful | silently corrupted a same-size binary repeatedly |
| `/data/local/tmp` persists across a panic | files get clobbered; re-push and verify after every crash |
| "device is alive" | offline adb returns instantly with exit 0 |
| stdout logging during a race | hijacked once the route starts; use high-fd marks with `fsync` |
| `slabinfo` active counts | per-CPU cached and RCU-deferred; unusable for precise orchestration |
| `/sys/kernel/slab/*` attributes | denied, so SLUB tuning parameters cannot be read directly |
