# 01 · Environment: what a shell context can actually do

Everything below was measured on the target device from `adb shell`
(uid 2000, `u:r:shell:s0`), not assumed.

## Available

| Capability | Detail |
|---|---|
| `/proc/slabinfo` | readable ✅ |
| tracefs | mounted rw at `/sys/kernel/tracing`, group `readtracefs` (3012) |
| `trace`, `tracing_on`, `buffer_size_kb` | writable ✅ |
| event `trigger` files | writable ✅ |
| `io_setup` (AIO) | works ✅ (`io_setup(128)` returns 0) |
| `mmap`, `mprotect`, `clone`, `futex`, `timer_create` | all normal |

## Blocked

| Capability | Result |
|---|---|
| `/proc/kallsyms` | permission denied |
| `dmesg` | permission denied |
| `/proc/sys/kernel/panic_on_oops` | unreadable |
| `/sys/fs/pstore`, `last_kmsg` | absent |
| `kprobe_events` | permission denied |
| `events/*/format` | permission denied |
| `events/*/enable` (direct) | permission denied |
| `events/*/filter` | permission denied |
| `/sys/kernel/btf/vmlinux` | absent |
| `/sys/kernel/slab/<cache>/*` attributes | permission denied |

## The one trick that unlocks tracing

`enable` files are refused, but **event trigger** files are writable. Writing an
`enable_event:` trigger onto an unrelated event enables the target event anyway:

```sh
echo 'enable_event:kmem:kmem_cache_alloc' > /sys/kernel/tracing/events/kmem/rss_stat/trigger
echo 'enable_event:kmem:mm_page_free'     > /sys/kernel/tracing/events/kmem/rss_stat/trigger
```

Note the strings need a trailing newline when written from C.

`buffer_size_kb` is also writable and matters a lot — with the default ring
size the records of interest were overwritten by system activity before the
reader got to them (628k records scanned, none matching). 64 MB/CPU fixed it.

## Consequence for exploitation

There is **no kernel read primitive and no kernel log**. Any technique whose
feedback loop is "observe what the kernel did" is unavailable. The only
observability is:

- allocation/free tracepoints (call site + returned pointer + sizes)
- page alloc/free tracepoints (pfn + order)
- userspace side effects (e.g. `/sys/fs/selinux/enforce`, `getuid`)

Static reasoning therefore has to carry most of the weight, and every dynamic
experiment must be designed so the *device surviving or dying* is itself the
signal (see `05-methodology.md`, poison-page probe).
