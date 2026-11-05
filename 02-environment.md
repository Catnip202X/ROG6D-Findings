# 02 · What a shell context can actually do

Measured from `adb shell` (uid 2000, `u:r:shell:s0`). Not assumed.

## Available

| Capability | Detail |
|---|---|
| `/proc/slabinfo` | readable ✅ |
| tracefs | rw at `/sys/kernel/tracing`, gid `readtracefs` (3012), shell is in that group |
| `trace`, `tracing_on`, `buffer_size_kb` | writable ✅ |
| event `trigger` files | writable ✅ |
| `io_setup` (AIO) | works — `io_setup(128)` returns 0 ✅ |
| `mmap`/`mprotect`/`clone`/`futex`/`timer_create`/`memfd_create` | all normal |
| `mincore`, `madvise`, `prctl` | normal |
| **`perf_event_open` kernel sampling with `PERF_SAMPLE_REGS_INTR`** | **works — `perf_event_paranoid = -1`** ✅ |

## Denied

| Capability | Result |
|---|---|
| `/proc/kallsyms` | permission denied |
| `dmesg` | permission denied |
| `/proc/sys/kernel/panic_on_oops` | unreadable |
| `/proc/iomem` | denied |
| `/sys/fs/pstore`, `last_kmsg` | absent |
| `kprobe_events` / `uprobe_events` | permission denied |
| `events/*/format` | permission denied |
| `events/*/enable` (direct write) | permission denied |
| `events/*/filter` | permission denied |
| `/sys/kernel/btf/vmlinux` | absent |
| `/sys/kernel/slab/<cache>/*` | permission denied |

## The one bypass that opens tracing

`enable` is refused but **`trigger` is writable**, and a trigger can enable an
unrelated event:

```sh
echo 'enable_event:kmem:kmem_cache_alloc' > /sys/kernel/tracing/events/kmem/rss_stat/trigger
echo 'enable_event:kmem:mm_page_free'     > /sys/kernel/tracing/events/kmem/rss_stat/trigger
```

From C, the string must end with `\n`.

`buffer_size_kb` is also writable and matters: at the default size the records
of interest were overwritten by system activity before the reader reached them
(628k records scanned, zero matches). 64 MB per CPU fixed it.

## Consequence

There is **no kernel read primitive and no kernel log**. Every technique whose
feedback loop is "look at what the kernel did" is unavailable. Observability is
limited to:

- `kmem_cache_alloc` / `kmem_cache_free` (call site, pointer, sizes)
- `mm_page_alloc` / `mm_page_free` (pfn, order)
- userspace side effects (`/sys/fs/selinux/enforce`, `getuid()`)

So static reasoning carries the weight, and each experiment must be designed so
that *the device surviving or dying* is itself the signal.


## perf_event is a full kernel-observation channel

This was missed for most of the engagement and is the single most important
capability on this build:

```
/proc/sys/kernel/perf_event_paranoid = -1
perf_event_open(..., exclude_user=1, sample_type = IP|TID|CALLCHAIN|REGS_INTR)
  → fd=4, errno=0
```

Consequences:

- **KASLR leaks from userspace, no exploit required.** Sampling kernel IPs during
  a syscall storm and taking the minimum, then rounding down to 2 MB, yields
  runtime `_text` directly. Measured here: `_text = 0xffffffef80c00000`,
  slide `0x2f78c00000` — identical to the slide the timer exploit had derived
  independently from a trace call site, confirming both.
- **Kernel register state is readable at sample points**, which allows
  "at instruction X, register Y stably holds pointer Z" mapping — exactly the
  measurement needed for per-build stack geometry.
- **Call chains** give the kernel's actual control flow, replacing the missing
  `dmesg` for many debugging questions.

A reference implementation of these modes exists in the public 5.10 GhostLock
port (`tools/r7probe.c`): `kaslr`, `hist`, `dump`, `crest`, `map` modes, all
read-only (perf sampling + file reads), and it runs unmodified on this device.
