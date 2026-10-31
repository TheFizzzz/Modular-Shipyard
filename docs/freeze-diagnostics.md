# Freeze diagnostics — 0.32.22

The supplied Windows RTX 3060 log (Mindustry 160.7, Java 25.0.1, NVIDIA 610.88) contains only startup messages. The exported logs.txt contains the same text. Neither establishes the cause of the subsequent freeze. Reported display flicker suggests a Windows graphics reset; the corresponding Display/nvlddmkm System event is needed. This is not evidence by itself of defective hardware or of the mod being unrelated.

The local Proton logs.txt export also contains startup messages only. Two older Proton reports fail in Java networking initialization before mod initialization; those are separate from the previously captured Linux native SDL/NVIDIA/Steam-overlay presentation stall.

The client-only FreezeDiagnostics recorder starts after ClientLoadEvent. A daemon checks the application heartbeat every two seconds. A gap of at least 20 seconds saves one UTF-8 report per stalled heartbeat under the current game data directory's shipyard-diagnostics folder. Reports include thread stacks, the application thread identity, cached rendering stage, game/network state, counts, game/mod/Java/OS/graphics versions. There are no normal-frame file writes, uploads, changes to game mechanics, GPU resets or automatic restarts. Dispose interrupts the daemon; headless servers do not start it.

Slow loading, operating-system suspension or a debugger pause can also cause reports; reports label themselves possible freezes/long frames, not crash verdicts. A process/JVM/system that terminates or stops all threads before the threshold may leave no report. For Windows driver resets, collect the existing Windows System event as well.

Use: install 0.32.22, restart, and play normally. If it remains frozen, leave it open for around 30 seconds before closing. Send the newest freeze-...-UTC.txt under saves/shipyard-diagnostics for a Steam installation. Nothing is sent automatically.

Validation: full 160.2 native gameplay suite passes. An isolated native 160.7/NVIDIA RTX 4050/JDK 17 session starts a new world with a vanilla Alpha and no Shipyard placed: 22 healthy seconds produce no report; a deliberate 24-second blocked application thread generates exactly one report containing the actual blocked stack, versions and context; three seconds of recovery produce no extra reports. This tests the recorder, not the real intermittent failure. Windows execution has not been tested here. Fixture: build/tooling/FreezeRecorderDesktopCheck.java. Log: build/freeze-recorder-03222-preview.log.
