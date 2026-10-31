# Recurrence freeze, 2026-10-29

Installed base 0.32.15, addon 0.4.14; Steam Mindustry 160.7, Java 25.0.1, NVIDIA RTX 4050 driver 615.78.08. User reported freezing while firing Recurrence. No new Java crash report or exception was present.

Two live thread dumps at 09:17:20 and 09:17:37 show the main thread in SDL_GL_SwapWindow; its accumulated CPU time increased by only about 7ms. A native stack captured immediately afterwards shows ioctl -> drmIoctl -> drmSyncobjTimelineWait -> NVIDIA GL/GLX -> Steam overlay glXSwapBuffers -> SDL. No mod code appears on this thread stack. This locates the stall in native presentation, but does not establish the triggering cause or rule out earlier rendering work.

An isolated native desktop reproduction used the same installed runtime, GPU/driver and both mods, with Steam integration/overlay disabled and a headless gamescope presentation environment. Thirty Recurrence shots across Alpha/Beta/Gamma, moving/aiming and four targets exercised chain damage (890.125 damage on the fourth target). It completed without freezing. This is a different presentation environment and does not prove the Steam session is fixed.

Diagnostics are preserved under build/diagnostics/recurrence-freeze-20261029 (ignored local files). No gameplay or rendering code was changed for this investigation. A controlled next comparison is the normal Steam launch with its overlay disabled, keeping other settings the same. The running user game was not terminated.
