# 10 · Porting target: working 5.10 GhostLock implementation

The public port `pirosap/aquos-r7-ghostlock` implements CVE-2026-43499 for a
Linux 5.10 Android device, and one of its validated builds is
`5.10.218-android12-9-00041-…` — the same 5.10.218 base as this device.

## Why this one, not the 6.x lineage

It does not use heap reclaim at all:

- ghost task and fake lock are built inside an unused **zero run in BSS**
- the waiter is stamped through a **sigreturn** path rather than `pselect`
- it ships probes designed for a target with no kernel log

## Constant mapping for this device

| Symbol | Ours | Theirs (different build) |
|---|---|---|
| `init_task` | `0x279be80` | `0x27abe80` |
| `init_cred` | `0x27b0a60` | `0x27c0a60` |
| `selinux_state` | `0x2a41b98` | `0x2a52b90` / `0x2a41b98` |
| `__log_buf` | `0x2993d50` | `0x29a4d50` |
| `__bss_start` | `0x2989000` | `0x299a000` |
| `__bss_stop` | `0x2a80c4c` | `0x2a91c0c` |
| `get_task_pid` | `0x175548` | `0x1757e8` |
| `futex_wait_requeue_pi` | `0x294140` | `0x296bb8` |
| `sel_read_enforce` | `0x8cdfac` | `0x8d68e8` |
| linear map base | `0xffffff7fc0000000` | `0xffffff8000000000` + `PHYS_OFFSET 0x40000000` |

Waiter field offsets agree exactly (`task 0x30 / lock 0x38 / prio 0x40 /
deadline 0x48`), as do `task.cred 0x780`, `cred.uid 0x4`, `MM_STRUCT_SZ 0x3c0`,
and `selinux enforcing` at offset 0.

## BSS scratch region

The port uses one symbol-free zero run in BSS (`__bss_start+0x1188`, 0xe78 B).
For this kernel the same probe was run: the whole image was disassembled and
every `adrp`/`add` pair targeting BSS was collected (1509 addresses), then the
untouched gaps between them were computed.

Result: `__bss_start+0x1188` also lands inside a gap here, and that gap is
**0xe90 bytes** — almost exactly the same size as on the reference build:

```
gap: 0xffffffc00a98a170 .. 0xffffffc00a98b000   (0xe90)
```

Larger gaps also exist (188 KB, 124 KB, 65 KB, 61 KB) if more room is needed.

## Remaining unknowns for this build

1. whether that BSS run is actually zero at runtime (cannot be read back from
   userspace; will be validated by the exploit itself)
2. the sigreturn / `futex_wait_requeue_pi` frame geometry — must be measured
   on-device, not copied
3. the KASLR anchor: `perf_event_open` sampling gives kernel IPs, and a
   trace-call-site method gives the slide independently; both agree at
   slide `0x2f78c00000` on the current boot

## Measurement tooling available

`perf_event_paranoid = -1` here, so kernel IP + register sampling works from the
shell context (`kaslr`, `hist`, `dump`, `map` style probes all run). This is the
channel the earlier attempts lacked.
