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

## iPhone app

The iPhone version is in `ios/`, written in Swift (SwiftUI) with the same look, the same 20 built-in sounds
(converted to `.m4a`) and the same mascot. It needs iOS 16 or newer.

**Your own clips and GIFs on iPhone:** tap **IMPORT** and pick sound clips or pictures in the Files app, or drop
them straight into *Files > On My iPhone > MacGruber* in the `clips` and `gifs` folders. They work exactly like
the Android folders: each clip becomes a button named from its file name, and pictures with a matching name go on
that button. Hold any button to give just that button your own clip or picture (from Files or Photos).
Imported files stay on your phone and are never committed or bundled into the app.

**Builds:** `.github/workflows/ios.yml` runs on GitHub's Macs. Every push that touches `ios/` builds the app and
runs its tests. Once the three API key secrets below are set, pushes also sign the app and upload it to TestFlight.

| Repository secret | What it is |
| --- | --- |
| `ASC_KEY_ID` | Key ID of an App Store Connect API key with the **Admin** role (Users and Access > Integrations) |
| `ASC_ISSUER_ID` | Issuer ID shown above the list of keys on that page |
| `ASC_KEY_P8` | The full text of the key's `.p8` file (`cat ~/Downloads/AuthKey_*.p8 \| pbcopy`) |

With that key the workflow handles the rest of the signing setup through the App Store Connect API
(`ios/ci/asc_signing.py`). It registers the bundle ID `com.thebradzter.macgruber`, creates one Apple Distribution
certificate, and creates an App Store provisioning profile named "MacGruber GitHub CI App Store". The certificate is
saved to `ios/ci/dist-cert.enc`, encrypted with a key derived from the API key, so later runs reuse it instead of
making new ones. Only someone holding the API key can decrypt it, and anyone with that key could create a
certificate anyway. If you replace the API key, the next run makes a fresh certificate. The app itself must exist
in App Store Connect (My Apps > + > New App) with that bundle ID before the first upload.

**Open it in Xcode on a Mac:** `brew install xcodegen`, then `cd ios && xcodegen` and open `MacGruber.xcodeproj`.
