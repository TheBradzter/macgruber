import AVFoundation
import SwiftUI

/// Loads the buttons, plays sounds, and keeps the clips and pictures the user picked for each button.
@MainActor
final class SoundStore: NSObject, ObservableObject, AVAudioPlayerDelegate {
    enum Folder { case clips, gifs }

    @Published private(set) var sounds: [Sound] = []
    @Published private(set) var playingKey: String?
    @Published private(set) var customClips: [String: String]
    @Published private(set) var customPictures: [String: String]
    @Published private(set) var toast: String?

    static let audioExtensions: Set<String> = [
        "mp3", "m4a", "aac", "wav", "aif", "aiff", "aifc", "caf", "flac", "mp4", "m4b",
    ]
    static let imageExtensions: Set<String> = ["gif", "webp", "png", "jpg", "jpeg", "heic", "heif"]

    private static let clipsDefaultsKey = "customClips"
    private static let picturesDefaultsKey = "customPictures"

    let documentsDir: URL
    let clipsDir: URL
    let gifsDir: URL
    private let customDir: URL
    private let defaults = UserDefaults.standard
    private let fileManager = FileManager.default
    private var player: AVAudioPlayer?

    override init() {
        documentsDir = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        clipsDir = documentsDir.appendingPathComponent("clips", isDirectory: true)
        gifsDir = documentsDir.appendingPathComponent("gifs", isDirectory: true)
        let support = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        customDir = support.appendingPathComponent("Custom", isDirectory: true)
        customClips = defaults.dictionary(forKey: Self.clipsDefaultsKey) as? [String: String] ?? [:]
        customPictures = defaults.dictionary(forKey: Self.picturesDefaultsKey) as? [String: String] ?? [:]
        super.init()

        for dir in [clipsDir, gifsDir, customDir] {
            try? fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        }
        writeReadMe()
        // Play even when the ring/silent switch is on silent: it's a soundboard.
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        reload()
    }

    // MARK: - Loading buttons

    /// Rebuilds the buttons from the clips and gifs folders (call when the app comes back to the front).
    func reload() {
        let loaded = loadSounds()
        if loaded != sounds {
            sounds = loaded
        }
        if let key = playingKey, !sounds.contains(where: { $0.key == key }) {
            stop()
        }
    }

    /// Each audio file in Documents/clips becomes a button, labelled from its file name.
    /// The built-in sounds are used only while that folder has no audio in it.
    private func loadSounds() -> [Sound] {
        var found: [Sound] = []
        for url in files(in: clipsDir, extensions: Self.audioExtensions) {
            let base = url.deletingPathExtension().lastPathComponent
            let emoji = BuiltIn.fallbackEmoji[found.count % BuiltIn.fallbackEmoji.count]
            found.append(Sound(key: "file:" + url.lastPathComponent, emoji: emoji,
                               label: Labels.fromFileName(base), resource: nil, file: url, gif: nil))
        }
        var result = found.isEmpty ? BuiltIn.sounds : found
        assignGifs(&result)
        return result
    }

    /// A picture whose name matches a clip's name goes on that clip's button;
    /// the rest fill the other buttons in name order.
    private func assignGifs(_ list: inout [Sound]) {
        var unmatched: [URL] = []
        for url in files(in: gifsDir, extensions: Self.imageExtensions) {
            let base = url.deletingPathExtension().lastPathComponent
            if let i = list.firstIndex(where: {
                $0.gif == nil && $0.file != nil
                    && $0.file!.deletingPathExtension().lastPathComponent.caseInsensitiveCompare(base) == .orderedSame
            }) {
                list[i].gif = url
            } else {
                unmatched.append(url)
            }
        }
        var next = 0
        for i in list.indices where next < unmatched.count && list[i].gif == nil {
            list[i].gif = unmatched[next]
            next += 1
        }
    }

    private func files(in dir: URL, extensions: Set<String>) -> [URL] {
        let urls = (try? fileManager.contentsOfDirectory(
            at: dir, includingPropertiesForKeys: [.isRegularFileKey], options: [.skipsHiddenFiles])) ?? []
        return urls
            .filter { url in
                let isFile = (try? url.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) ?? false
                return isFile && extensions.contains(url.pathExtension.lowercased())
                    && !url.deletingPathExtension().lastPathComponent.isEmpty
            }
            .sorted { $0.lastPathComponent.caseInsensitiveCompare($1.lastPathComponent) == .orderedAscending }
    }

    private func writeReadMe() {
        let url = documentsDir.appendingPathComponent("How to add your clips.txt")
        guard !fileManager.fileExists(atPath: url.path) else { return }
        let text = """
        MacGruber Soundboard

        clips folder: put sound files here (mp3, m4a, wav, aac, ...). Each one becomes a button,
        labelled from its file name ("03_kfbr-392.mp3" shows as "KFBR 392", sorted by name).
        While this folder has sounds in it, the built-in sounds are hidden.

        gifs folder: put pictures and GIFs here (gif, png, jpg, webp, heic). A picture with the
        same name as a clip ("kfbr392.gif" + "kfbr392.mp3") goes on that clip's button; the others
        fill the remaining buttons in name order.

        Go back to the app and the buttons update.
        """
        try? text.write(to: url, atomically: true, encoding: .utf8)
    }

    // MARK: - Playback

    func play(_ sound: Sound) {
        stop()
        var candidate: AVAudioPlayer?
        if let name = customClips[sound.key] {
            candidate = try? AVAudioPlayer(contentsOf: customDir.appendingPathComponent(name))
            if candidate == nil {
                show("Couldn't play your clip, playing the built-in sound.")
            }
        }
        if candidate == nil, let file = sound.file {
            candidate = try? AVAudioPlayer(contentsOf: file)
        }
        if candidate == nil, let resource = sound.resource,
           let url = Bundle.main.url(forResource: resource, withExtension: "m4a") {
            candidate = try? AVAudioPlayer(contentsOf: url)
        }
        guard let newPlayer = candidate else {
            show("Couldn't play that sound.")
            return
        }
        try? AVAudioSession.sharedInstance().setActive(true)
        newPlayer.delegate = self
        newPlayer.prepareToPlay()
        if newPlayer.play() {
            player = newPlayer
            playingKey = sound.key
        }
    }

    func stop() {
        player?.stop()
        player = nil
        playingKey = nil
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        let id = ObjectIdentifier(player)
        Task { @MainActor in self.finished(id) }
    }

    nonisolated func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        let id = ObjectIdentifier(player)
        Task { @MainActor in self.finished(id) }
    }

    private func finished(_ id: ObjectIdentifier) {
        if let player, ObjectIdentifier(player) == id {
            stop()
        }
    }

    // MARK: - Per-button custom clips and pictures

    func hasCustomClip(_ sound: Sound) -> Bool { customClips[sound.key] != nil }

    func hasCustomisation(_ sound: Sound) -> Bool {
        customClips[sound.key] != nil || customPictures[sound.key] != nil
    }

    /// The picture to show on a button: one picked for it wins over one from the gifs folder.
    func pictureURL(for sound: Sound) -> URL? {
        if let name = customPictures[sound.key] {
            return customDir.appendingPathComponent(name)
        }
        return sound.gif
    }

    /// Copies a file the user picked (from the Files app) and uses it for the button.
    func setCustomClip(from url: URL, for sound: Sound) {
        guard let name = copyIn(url) else { return }
        if playingKey == sound.key { stop() }
        replace(&customClips, key: sound.key, with: name, defaultsKey: Self.clipsDefaultsKey)
        show("Clip saved. Tap the button to play it.")
    }

    func setCustomPicture(from url: URL, for sound: Sound) {
        guard let name = copyIn(url) else { return }
        replace(&customPictures, key: sound.key, with: name, defaultsKey: Self.picturesDefaultsKey)
        show("Picture saved.")
    }

    /// Saves picture data from the Photos library for the button.
    func setCustomPicture(data: Data, fileExtension: String, for sound: Sound) {
        let name = UUID().uuidString + "." + fileExtension
        do {
            try data.write(to: customDir.appendingPathComponent(name), options: .atomic)
        } catch {
            show("Couldn't save that picture.")
            return
        }
        replace(&customPictures, key: sound.key, with: name, defaultsKey: Self.picturesDefaultsKey)
        show("Picture saved.")
    }

    func reset(_ sound: Sound) {
        if playingKey == sound.key { stop() }
        replace(&customClips, key: sound.key, with: nil, defaultsKey: Self.clipsDefaultsKey)
        replace(&customPictures, key: sound.key, with: nil, defaultsKey: Self.picturesDefaultsKey)
    }

    private func replace(_ dict: inout [String: String], key: String, with name: String?, defaultsKey: String) {
        if let old = dict[key], old != name {
            try? fileManager.removeItem(at: customDir.appendingPathComponent(old))
        }
        dict[key] = name
        defaults.set(dict, forKey: defaultsKey)
    }

    /// Copies a picked file into the app's own storage, so it keeps working after the original moves.
    private func copyIn(_ url: URL) -> String? {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let ext = url.pathExtension.isEmpty ? "dat" : url.pathExtension.lowercased()
        let name = UUID().uuidString + "." + ext
        do {
            try fileManager.copyItem(at: url, to: customDir.appendingPathComponent(name))
            return name
        } catch {
            show("Couldn't copy that file.")
            return nil
        }
    }

    // MARK: - Importing into the clips and gifs folders

    /// Copies files picked in the Files app into Documents/clips or Documents/gifs.
    func importFiles(_ urls: [URL], into folder: Folder) {
        let dir = folder == .clips ? clipsDir : gifsDir
        let allowed = folder == .clips ? Self.audioExtensions : Self.imageExtensions
        var count = 0
        var skipped = 0
        for url in urls {
            guard allowed.contains(url.pathExtension.lowercased()) else {
                skipped += 1
                continue
            }
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            let dest = dir.appendingPathComponent(url.lastPathComponent)
            if dest.standardizedFileURL == url.standardizedFileURL {
                count += 1
                continue
            }
            try? fileManager.removeItem(at: dest)
            if (try? fileManager.copyItem(at: url, to: dest)) != nil {
                count += 1
            } else {
                skipped += 1
            }
        }
        reload()
        let noun = folder == .clips ? (count == 1 ? "clip" : "clips") : (count == 1 ? "picture" : "pictures")
        show(skipped == 0 ? "Imported \(count) \(noun)." : "Imported \(count) \(noun), skipped \(skipped).")
    }

    // MARK: - Toast

    func show(_ message: String) {
        toast = message
        Task {
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            if toast == message {
                toast = nil
            }
        }
    }
}
