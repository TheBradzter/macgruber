# MacGruber Soundboard

An Android soundboard with 20 MacGruber-style buttons, in army olive, explosion orange and mullet gold.

## Sounds

Every built-in sound is original: synthesized effects (explosions, 80s synths, air horn, chopper and more) mixed
with text-to-speech readings of short MacGruber-style lines. No audio from the film or TV show is included,
since those clips are copyrighted.

**Use your own clips:** hold any button, tap **CHOOSE CLIP** and pick an audio file on your phone. That button
then plays your file (it shows "★ your clip"). Hold it again and tap **RESET** to go back to the built-in sound.

To regenerate the built-in sounds (needs Python 3 with numpy, `espeak-ng` and `ffmpeg`):

```
python3 tools/make_sounds.py
```

## Builds

GitHub Actions builds the APK on every push and pull request (`.github/workflows/build.yml`).
Each push to `main` also publishes a release, so the newest APK is always at
`https://github.com/<owner>/<repo>/releases/latest/download/MacGruberSoundboard.apk`.

The APK is signed with `keystore/soundboard.jks`. It is committed on purpose and is not a secret: it only
keeps the signature identical between builds so a new APK installs as an update over the old one.

## Install on your phone

**From the release:** open the latest release on your phone, tap `MacGruberSoundboard.apk`, and allow
installing from your browser when Android asks.

**Over Wi-Fi with adb** (Android 11+): on the phone turn on Developer options > Wireless debugging and tap
"Pair device with pairing code". Then on a computer on the same Wi-Fi:

```
adb pair <phone-ip>:<pairing-port>      # enter the 6-digit code
adb connect <phone-ip>:<debug-port>     # the port shown on the Wireless debugging screen
adb install -r MacGruberSoundboard.apk
```
