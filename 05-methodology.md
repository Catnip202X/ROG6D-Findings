# 05 · Methodology worth reusing

## 1. Fingerprint a kernel function by its call-site low 21 bits

KASLR on this kernel is 2 MB aligned, so `call_site & 0x1fffff` is invariant.
The kmem tracepoints record the real return address, so a raw record can be
attributed to a function without any symbol table:

```
k_itimer_rcu_free -> kmem_cache_alloc return address & 0x1fffff == 0x789f0 (timer alloc)
                                                    ... free call site == 0x786ec
```

Counting records whose call site matches that mask gives an exact answer to
"were the objects freed?" without reading any kernel memory.

## 2. Solve the linear-map base from your own data

Do **not** trust the textbook `PAGE_OFFSET` (0xffffff8000000000 on arm64 39-bit VA).
On this device it is off by 1 GiB, and that error silently breaks every pfn
comparison while still producing plausible-looking numbers.

Procedure:

1. dump your own object slab pfns `S` (from `kmem_cache_alloc` pointers)
2. dump the observed order-1 `mm_page_free` pfns `F`
3. histogram `S - F` over all pairs

The correct base shows up as a sharp peak. Here it was `-0x40000` pages,
matching **304/310** slabs, giving base `0xffffff7fc0000000`.

## 3. Poison-page probe: prove a code path is *not* reached

When a write primitive silently does nothing, you cannot tell "wrong target"
from "code path never executed". Fill the whole reclaimed page with a
non-canonical pointer pattern (`0xdeaddeaddeaddead`) and arrange for the object
to be reclaimed first.

- If the kernel ever walks that node → guaranteed panic.
- Device survives → the path is not executed, regardless of geometry.

This turned an open-ended "is my payload wrong?" question into a single
binary fact, and it is what closed CVE-2026-98260.

## 4. Marks that survive a panic

`printf` is useless during a kernel-side race: once the route starts, stdout is
hijacked and buffered output dies with the process.

What worked: write progress marks to a file through a **high-numbered fd**
(≥ 600, via `F_DUPFD_CLOEXEC`, so a route that dup2's fds 0–319 cannot clobber
it) and `fsync()` every line — without fsync the page cache is dropped on panic
and the marks vanish.

## 5. Never trust "the device is alive"

Offline adb returns instantly and every command still exits 0, so an empty
`getprop` looks like success. Always cross-check `uptime` and the mtime of your
mark file. Also re-push binaries after every panic: `adb push` corrupted the
same-size binary more than once (matching size, different md5), which produced
several false "it ran and nothing happened" conclusions. Compare md5 after
every push.
