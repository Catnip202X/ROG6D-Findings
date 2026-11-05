# 06 · Negative results — things that did NOT work, and why

Recorded so they are not repeated.

## CVE-2026-98260 port

| Attempt | Outcome |
|---|---|
| textbook `PAGE_OFFSET` for pfn math | silently wrong by 1 GiB; every reclaim check missed |
| node offset other than `0x78` | wrong — `0x78` confirmed by disassembly |
| `compact_waiter` word tables (both variants) | irrelevant; the erase is never reached |
| target-address bias ±0x78 / corrected zero-write geometry | irrelevant; same reason |
| fake-cred path | fails earlier: on 5.10 the fake-cred blob placement spills the page (`off=0x1620`, `blob=0x1f00`) |
| default trace buffer size | records of interest overwritten before reading (628k scanned, 0 matching) |
| event `filter` / `enable` writes | denied by SELinux; only `trigger` works |
| spray disabled | changes nothing — pages still not touched at reap |
| clean boot / empty slab cache (9 slabs) | changes nothing |

## CVE-2026-43499 (GhostLock)

Ruled out, each with a device run: word tables (compact 0/1), `waiter_shift`
−2/−4, three multicast geometries, `tcp_zerocopy` (a standalone probe shows
`TCP_ZEROCOPY_RECEIVE` returns `EINVAL` for even a textbook call on this kernel —
feature unusable), `enter_delay` 5/50/500 ms, `chain_delay` 2/100 ms,
route wait timeouts, both candidate `selinux` targets, six data symbols that had
been silently taken from a built-in profile for a different kernel, `SKB_DATA_DELTA`
0/−0x1000, prio layout, and three values of `waiter->task`.

## CVE-2026-99203 (pipe/FIFO fasync)

`fasync_alloc()` uses `GFP_KERNEL` without `__GFP_ACCOUNT`, so it is not
memcg-charged. There is no per-process lever to make it fail. Global memory
pressure was tried down to ~40 MB and Android panicked first
(`reboot reason: kernel_panic, system_is_dead_on_oom`) — the OOM killer fires
before the allocation failure that the bug needs.

## Environment

| Assumption | Reality |
|---|---|
| `adb push` delivers the binary faithfully | it silently corrupted a same-size binary repeatedly; md5 must be checked every push |
| `/data/local/tmp` persists across a panic | files get clobbered; re-push and verify after every crash |
| "device is alive" | offline adb returns instantly with exit 0; always cross-check `uptime` |
| stdout logging during a race | hijacked once the route starts; use high-fd marks with `fsync` |
