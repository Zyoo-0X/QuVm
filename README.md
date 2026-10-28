<div align="center">
<img src="docs/assets/logo.png" width="128" alt="QuVm logo"/>

# QuVm
**Android GSI Virtual Machine — pick a GSI ROM, it boots straight in.**
</div>

QuVm imports a GSI (`system.img`, `.img.xz`, `.zip`, `super.img`, factory images, recovery ZIP, Odin `.tar.md5` …) and boots it directly:

1. Open QuVm → **+ → Import** → choose your GSI file.
2. When the import finishes, QuVm **boots the ROM immediately** (Settings → *Auto-boot* turns this off).
3. Next time you tap the QuVm icon, it reopens the last system directly.

## What is included ("kernel and the rest")
Everything the guest needs is already bundled, no separate kernel/initrd/vendor to hunt for:

- `libaemu64_21 … 37.so` — one engine kernel per Android version (5.0 → 17), so GSIs 8–17 are covered
- `libbinderd64_*.so` — userspace binder (replaces the kernel binder driver)
- GL bridge + SwiftShader (GLES 1/2/3), audio HAL, camera, sensors, lights, gralloc shims (`assets/engines/`)
- QEMU user-mode (`libqemu_*.so`) for 32-bit programs and Android 2.3–4.4

Requirements: arm64-v8a phone, Android 8.0+, 1–3 GB free per ROM. No root.

## Build
```bash
./gradlew assembleDebug          # JDK 17, Android SDK 36
```
or run **Actions → QuVm Build** on GitHub; the APK is uploaded as an artifact.
The native engine binaries are pre-built (`app/src/main/jniLibs`, `assets/engines`); their sources are in `native/`.

## Credits & licence
QuVm's engine is a fork of **AEmulator** (https://github.com/uxazu/AEmulator), itself based on SENSES by Istratii-tech. Licensed **GPL-3.0** — see `LICENSE`. Android and all firmware belong to their owners.
The QuVm name, logo and auto-boot flow are new; the application id is `com.quvm.app`, so it installs next to AEmulator.

## Troubleshooting
- **"Android killed N guest processes at once"** — Android 12+ limits an app to 32 child processes. Turn on *Developer options → Disable child process restrictions*, or once over adb:
  `adb shell device_config put activity_manager max_phantom_processes 2147483647` and
  `adb shell settings put global settings_enable_monitor_phantom_procs false`.
- **"Binder driver '/dev/binder' could not be opened"** (zygote exited, code 137) — the guest lost its binder daemon. QuVm 1.0.2 names the daemon and how it ended. Code 137 means Android killed it: use the child-process setting above. Otherwise send the lines `binderd` / `hwbinderd` / `vndbinderd` print under ⋮ → Log.
- **Phone short of RAM / boot very slow** — *RAM-friendly mode* is on by default: when free memory falls QuVm waits and slows the guest down instead of crashing. The log shows `memory: ...` lines while it does. Turn it off in Settings only if the VM boots too slowly on a phone with plenty of RAM.
