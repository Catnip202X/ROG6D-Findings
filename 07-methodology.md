# 07 · Methodology

Techniques that worked when there is no kernel read primitive and no kernel log.

## 1. Make every experiment binary

If you cannot observe the kernel, design so the *device surviving or dying* is
the answer. Examples used here:

- whole-page poison to prove a code path is never reached
- skipping a race step (`NO_RACE`) to prove the rest of the route is sound
- reaping vs. holding a zombie to separate two hypotheses

## 2. Fingerprint by invariant bits, not by symbols

KASLR is 2 MB aligned → `addr & 0x1fffff` is invariant. Raw trace records carry
real call sites, so a function can be identified without kallsyms.

## 3. Solve constants from your own data

Never import a "textbook" value. `PAGE_OFFSET` here is off by 1 GiB from the
textbook figure, and that one value poisoned several rounds of measurements
while still printing plausible numbers. Histogram against observed events and
let the data pick the constant.

## 4. Verify the toolchain before trusting a null result

`adb push` silently corrupted a same-size binary more than once (identical size,
different md5). The symptom is exactly "it ran and nothing happened". Always
compare md5 after every push, and re-push after every panic — `/data/local/tmp`
does not survive reliably.

Also: an offline device makes every adb command return instantly with exit 0, so
an empty `getprop` looks like success. Cross-check `uptime` and the mtime of the
mark file before drawing any conclusion.

## 5. Prefer static proof over dynamic guessing

When one panic costs 2–4 minutes plus a reboot, a half hour of disassembly is
cheaper. The decisive findings in this work were all static:

- `posix_cpu_timers_exit` bails on `head->next == NULL`
- `pid_task()` is inlined into `posix_cpu_timer_del` (visible as
  `ldr x8,[x0,#0xa0]` → dereference → `container_of`),
  which reversed an earlier wrong conclusion
- `rt_mutex_adjust_prio_chain` contains no indirect branches

## 6. Distinguish "wrong parameter" from "wrong model"

Before sweeping a constant, prove that changing it could matter. The poison probe
converted an open-ended "is my geometry wrong?" into a single fact, and stopped
a sweep that would have consumed dozens of device runs.
