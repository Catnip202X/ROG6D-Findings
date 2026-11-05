# 06 · Tooling written for this work

Nothing here is a weapon. These are the instruments that made the investigation
possible, and they are reusable on any 5.10 Android target.

## Profile reader/writer (`glk1`)

A byte-exact parser/rebuilder for the binary profile format used by the
GhostLock family. Reverse-engineered from scratch:

```
16-byte header : magic 21 07 00 0d | version(2) | frontend(2) | backend(2)
                 route/middleware u16 | release_len(2) | 2 reserved
release string
u16 section count
per section    : [name_len u8][name][entry_count u32]
per entry      : [key_len u8][key][u64 LE]
```

Route enum: `Auto=0, TcpZerocopy=1, SelectStack=2, MulticastWaiter=3`.
Round-trip rebuild is byte-identical, so any constant can be swept systematically
(word tables, shifts, geometries, delays, target addresses).

## Progress marks that survive a panic

Kernel-side races kill the process before stdout flushes, and once a route
starts it `dup2()`s fds 0–319 over your own. Working design:

- write marks through a **high fd (≥600)** obtained via `F_DUPFD_CLOEXEC`
- `fsync()` every line — without it the page cache is dropped on panic and the
  marks vanish

This is what made `NO_RACE`, `NO_CHAIN`, `NO_WORDS`-style isolation runs
interpretable at all.

## Call-site fingerprint counter

Raw trace records carry the real return address, and KASLR is 2 MB aligned, so
`call_site & 0x1fffff` identifies a kernel function with no symbol table:

- `0x789f0` → the `k_itimer` allocation in `do_timer_create`
- `0x786ec` → the `k_itimer` free in `k_itimer_rcu_free`

Counting matching records answers "were the objects allocated / freed?" exactly.

## Page-frame histogram solver

Dumps slab pfns and observed order-1 `mm_page_free` pfns, then histograms
`S − F` over all pairs. The correct linear-map base appears as a sharp peak
(here `−0x40000` pages, 304/310 slabs). Replaces guesswork about `PAGE_OFFSET`.

## Poison-page probe

When a write primitive silently does nothing, fill the whole reclaimed page with
a non-canonical pointer pattern. If the kernel ever walks that object, it
guarantees a panic. Device survives ⇒ the path is not executed, independent of
geometry. This is what closed the timer route.

## Minimal reproductions

| Tool | Purpose |
|---|---|
| `killpath` | child creates 4096 per-thread CPU timers, gets SIGKILLed; count order-1 page frees |
| `execfail` | non-leader thread arms a timer and execs; killer thread polls the tid and `tgkill`s the group; optional locker threads to slow `exec_mmap` |
| `timerslab` | create N timers then delete; measures how many slabs SLUB actually returns |
| `basecal` | create 31 timers (one slab), delete, correlate alloc pointers with freed pfns |
| `pagetest` | socket 8 KB transfer to observe `mm_page_alloc/free` field layout in text form |

Measured with `timerslab`: freeing 3100 timers (100 slabs) returns ~92 order-1
pages — i.e. SLUB does discard empty slabs here, retention is on the order of
`min_partial`.

## Profile inventory used

Baseline, compact waiter (`shift=-2, compact=1`), stock 5.10.236 reference,
release-patched, multicast variants, tcp_zerocopy variants, shift −4…+4 sweeps,
`wait100`/`wait2000`/`delay500` timing builds. Every one of them was a device run.
