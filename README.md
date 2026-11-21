# Merso Translator

Free Android app from the [Merso Gaming](https://www.youtube.com/@mersostore) channel that translates English game text to Arabic on screen while you play (PS5 Remote Play + camera OCR modes, Arabic captions shown on the TV).

## Licenses

This app uses the open-source [Chiaki](https://git.sr.ht/~thestr4ng3r/chiaki) engine for Remote Play, licensed under the GNU Affero General Public License v3 (AGPL-3.0). The native library (`libchiaki-jni.so`) is built from upstream Chiaki with small patches (audio mute + surface handling); the patches are in `chiaki-patches.diff`.

It also embeds [PS5 Account Tools](https://github.com/MangoScango/ps5-account-tools) (GPL-3.0) for offline local-account activation.

Because the app links AGPL-3.0 code, this source is published under the AGPL-3.0 as well.

## Building

The app is built with a manual pipeline (aapt2 + kotlinc + R8 + apksigner); see `build.sh` and `keep-rules.txt` (paths are specific to the author's build machine and will need adjusting).
