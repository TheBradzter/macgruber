# MacGruber Soundboard

An Android soundboard with 20 MacGruber-style buttons, in army olive, explosion orange and mullet gold.

## Sounds

Every built-in sound is original: synthesized effects (explosions, 80s synths, air horn, chopper and more) mixed
with text-to-speech readings of short MacGruber-style lines. No audio from the film or TV show is included,
since those clips are copyrighted.

**Pictures:** each button has a funny emoji, and the header has an original cartoon mullet-commando mascot
(`tools/art/mascot.svg`). Real MacGruber stills aren't bundled because they're copyrighted and show a real
actor's likeness, but you can add your own (see below).

**Load a whole folder of your own clips:** copy audio files (mp3, m4a, ogg, wav, ...) to
`Android/data/com.thebradzter.macgruber/files/clips/` on the phone, for example with
`adb push <file> /sdcard/Android/data/com.thebradzter.macgruber/files/clips/`. Each file becomes a button,
labelled from its file name (`03_kfbr-392.mp3` shows as "KFBR 392", sorted by name). While that folder has
audio in it, the built-in sounds are hidden. Your clips stay on your phone and are never committed here.

**Pictures and GIFs from a folder:** copy images (gif, webp, png, jpg) to
`Android/data/com.thebradzter.macgruber/files/gifs/` on the phone. A picture with the same name as a clip
(`kfbr392.gif` + `kfbr392.mp3`) goes on that clip's button; the others fill the remaining buttons in name
order. GIFs animate on Android 9 and newer. Like clips, they stay on your phone and are never committed here.

**Use your own clips and pictures:** hold any button, then tap **CHOOSE SOUND CLIP** or **CHOOSE PICTURE**
and pick a file on your phone. Custom clips show "★ your clip"; custom pictures fill the button.
Hold it again and tap **RESET BUTTON** to go back to the built-in sound and look.

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
