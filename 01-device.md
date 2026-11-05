# 01 · Device and boot chain

## Hardware / software

| Item | Value |
|---|---|
| Model | ASUS ROG Phone 6D, board `ASUS_AI2203_B` |
| SoC | MediaTek Dimensity 9000+ (MT6983) |
| Kernel | `5.10.218-android12-9-00023-g7e657aa362f2-ab12171750` |
| Build | `CN-34.0804.1204.107`, Android 14 |
| Bootloader | locked, no OEM unlock path used |
| SELinux | `Enforcing`, `u:r:shell:s0` for adb |
| VA | 39-bit, 4 KB pages, KASLR 2 MB aligned |

## Kernel config facts that matter

```
CONFIG_PREEMPT=y
CONFIG_FUTEX_PI=y
CONFIG_RT_MUTEXES=y
# CONFIG_DEBUG_RT_MUTEXES       → rtmutex deadlock printing is a no-op
# CONFIG_PROVE_LOCKING
# CONFIG_DEBUG_INFO_BTF         → no /sys/kernel/btf/vmlinux
CONFIG_DEBUG_INFO=y
# CONFIG_USER_NS                → no user namespaces
# CONFIG_NF_TABLES              → no nftables attack surface
CONFIG_SECURITY_SELINUX_DEVELOP=y
# CONFIG_SECURITY_SELINUX_DISABLE is not set
   → struct selinux_state starts with `enforcing` at offset 0
```

## Image layout facts derived here

| Item | Value |
|---|---|
| Link base `_text` | `0xffffffc008000000` |
| `__bss_start` | `0xffffffc00a989000` |
| `__bss_stop` | `0xffffffc00aa80c4c` |
| `__log_buf` | `0xffffffc00a993d50` |
| `init_task` | `0xffffffc00a79be80` |
| `init_cred` | `0xffffffc00a7b0a60` |
| `selinux_state` | `0xffffffc00aa41b98` |
| linear map base | `0xffffff7fc0000000` (see `04-timer-route.md`) |

## Physical memory does not start at 0

The linear-map base is **not** the textbook `0xffffff8000000000`. This device
(like other MT6983 / SM8450 boards) has `PHYS_OFFSET = 0x40000000`: RAM begins
at physical 0x40000000, so

```
pfn = (virt - 0xffffff8000000000) >> 12  is WRONG
pfn = (virt - 0xffffff7fc0000000) >> 12  is correct   (= +0x40000 pages)
```

This was found empirically here (histogram over observed page frees) and later
confirmed by an independent 5.10 port that documents `P0_PHYS_OFFSET 0x40000000`
for a different 5.10 device. Getting this wrong silently breaks every pfn
comparison while still producing believable numbers — it cost several rounds.
