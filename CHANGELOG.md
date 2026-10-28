# Unreleased
- **New interface, built from scratch ("Aurora").** New colour system (midnight indigo/cyan dark theme, lavender light theme), rounder shapes, one shared set of components (`ui/Kit.kt`).
  - Library: logo header, a *Continue* card for the last machine you started, machines as rows with version badge, tags and a play button; the ⋮ opens a bottom sheet (settings, rename, new machine, reset, delete). *Add system* button; new-machine form is a bottom sheet.
  - Machine settings: grouped cards, pinned *Save* bar.
  - App settings: hero card, grouped sections.
  - VM screen: the ⋮ drop-down is replaced by a draggable orb that opens a tile panel (tools, hardware keys, power); new boot card with progress; the log and terminal follow the theme.
- Android 13+: system_server no longer dies in `WifiDisplayAdapter` with "WiFi display was requested, but there is no WiFi Direct feature". The engine declares the feature for that check only; nothing is wired to WiFi Direct, so WiFi display (cast) stays idle and the boot continues.

# QuVm 1.0.4
- **Task manager** in the VM menu (⋮ → *Task manager*), available from the first seconds of the boot: every guest process with its memory (RSS) and CPU, the phone's free RAM, the RAM-friendly state, refreshed every 2 s. Tap ✕ to kill a process; core system processes (bold: zygote, system_server, surfaceflinger, binder daemons, servicemanager) ask for confirmation first.

# QuVm 1.0.3
- **RAM-friendly mode** (Settings → *RAM-friendly mode*, on by default). When the phone's free memory falls - slowly, or suddenly while the guest boots - QuVm slows the VM down instead of letting Android kill the whole app: the next guest service waits until memory recovers, the guest runs at a lower CPU priority, and when memory is critical the guest is paused in turns (about half speed) and one cached background app of the guest is killed. Everything is resumed as soon as memory is back, the VM stops or the boot fails. The thresholds follow the phone's RAM (low below 14 %, critical below 7 %, at least 1100 / 650 MB) and the Linux memory-pressure counter where the phone lets an app read it. The log says what it does (`memory: low ... slowing the guest down`).
- Android 16: system_server no longer dies in `HintManagerService` with `NumberFormatException: For input string: ""` - `ro.vendor.api_level` is set as it already was for Android 17.

# QuVm 1.0.2
- Fixed the truncated "Abort message: 'Binder driver" in the "Failed to start" dialog: the message itself holds quotes (`'/dev/binder'`) and was cut at the first one. The full text ("Binder driver '/dev/binder' could not be opened. Terminating.") is shown now.
- When a binder daemon (binderd / hwbinderd / vndbinderd - the guest's binder driver) dies, QuVm reports that directly (exit code, last log lines, and for code 137 the Android "phantom process killer" and how to lift it) instead of the later zygote symptom, and stops at once instead of starting the rest of the system on a dead binder.
- Boot now checks that the binder daemons stay alive while services start, and that their socket paths fit a unix socket.
- A binder-related zygote death now includes the state of the daemons and sockets and the file-descriptor limit.

# QuVm 1.0.1
- A zygote killed with code 137 now reports what the guest log says (system_server death, OutOfMemoryError, watchdog, fatal signal) and the phone's free RAM, instead of always guessing "out of memory".
- Android 16/17: zygote no longer aborts with "relocation delta must be Elf segment aligned" — address-space reservations are placed on a 16 KB boundary by the engine kernel (`native/guest64/36`, `37`; rebuilt by the GitHub workflow).
- When Android's phantom process killer ends a batch of guest services (what made installd/netd/mediaserver crash-loop), QuVm now says so and how to lift the limit, instead of restarting dependants 12 times.

# QuVm 1.0.0
- Engine (kernels, binder, GL bridge, HALs) taken from AEmulator 0.0.0.3 — GSI 8–17 boot natively on arm64.
- Auto-boot: boots the GSI right after import; reopens the last system on launch (toggle in Settings).
- New QuVm name, launcher icon (adaptive + themed/monochrome) and in-app logo.
- Application id `com.quvm.app`; upstream update checker off by default.
