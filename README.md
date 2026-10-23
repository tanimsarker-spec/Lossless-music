# LOSSLESS by TanimSarker
Audiophile local player. **C++ core** (miniaudio decoders + 10-band float EQ, bypass, FLAC header readout) shared by every platform. **Java** UI on Android and desktop (Swing), JNI between them. iOS uses the same C++ core with a small Swift UI shell (Java cannot run on iOS).

| Platform | UI | Build output (GitHub Actions artifact) |
|---|---|---|
| Android | Java | `lossless-android-apk` (debug APK, installable) |
| Windows / Linux / macOS | Java Swing | `lossless-desktop-<os>` (jar + native lib; run `run.bat` or `sh run.sh`; needs Java 17+) |
| iOS | Swift | `lossless-ios-unsigned-ipa` (unsigned: sideload with AltStore/Sideloadly or sign in Xcode) |

Everything builds automatically on every push; there is nothing to edit.

## What works
Open FLAC/WAV/MP3 files, play/pause/seek/next/prev, queue, 10-band EQ + preamp with true bypass, clip protection, source sample rate/bit depth readout (FLAC), reported output stream rate.

## Honest limits
- Untested by the author on real devices; first CI run may need a fix.
- The OS/device decides the final output rate; no bit-perfect claim. Bluetooth is lossy.
- Not yet built: library DB/search, playlists, lyrics, ReplayGain, crossfade, gapless, tag editing, lock-screen/notification controls on Android, themes, APE/WMA, AAC/ALAC/Opus (miniaudio decodes FLAC, WAV, MP3 only).
- Android copies picked files into app cache (uses extra storage). Playback is Activity-bound (no foreground service yet).
- Desktop needs a system audio backend (WASAPI / CoreAudio / ALSA or PulseAudio).

## Licenses
miniaudio: public domain / MIT-0. Java/Android SDK per their licenses.
