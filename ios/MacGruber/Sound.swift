import Foundation

/// One soundboard button. The key is stable and is used to store a custom clip or picture.
struct Sound: Identifiable, Equatable {
    let key: String
    let emoji: String
    let label: String
    /// Name of a bundled .m4a, or nil for a clip from the clips folder.
    let resource: String?
    /// A clip from Documents/clips, or nil for a built-in sound.
    let file: URL?
    /// A picture or GIF from Documents/gifs shown on this button, or nil.
    var gif: URL?

    var id: String { key }
}

enum BuiltIn {
    /// Fallback sounds, used only when the clips folder has no audio in it.
    static let sounds: [Sound] = [
        make("macgruber", "💪", "MACGRUBER!"),
        make("kfbr392", "🚗", "KFBR392"),
        make("countdown", "⏱️", "3… 2… 1… BOOM"),
        make("boom", "💥", "BIG BOOM"),
        make("paperclip", "📎", "PAPERCLIP & GUM"),
        make("celery", "🥬", "CELERY STALK"),
        make("mullet", "💇", "MULLET POWER"),
        make("seal", "🎖️", "NAVY SEAL… ETC"),
        make("lets_do_this", "🔥", "LET'S DO THIS!"),
        make("high_five", "✋", "HIGH FIVE?"),
        make("nooo", "😱", "SLOW-MO NOOO"),
        make("ticking", "💣", "TICKING BOMB"),
        make("rocket", "🚀", "ROCKET LAUNCHER"),
        make("gunshot", "🔫", "PEW PEW PEW"),
        make("helicopter", "🚁", "CHOPPER"),
        make("synth", "🎹", "80s SYNTH STAB"),
        make("synth_arp", "🕺", "MONTAGE SYNTH"),
        make("guitar", "🎸", "POWER RIFF"),
        make("airhorn", "📢", "AIR HORN"),
        make("sad", "🎺", "SAD TROMBONE"),
    ]

    static let fallbackEmoji = [
        "💥", "💪", "😎", "🔥", "💣", "🥬", "🚗", "🎖️",
        "🚀", "😱", "🎸", "🦅", "📎", "🚁", "💯", "😂",
    ]

    private static func make(_ key: String, _ emoji: String, _ label: String) -> Sound {
        Sound(key: key, emoji: emoji, label: label, resource: key, file: nil, gif: nil)
    }
}

enum Labels {
    /// "03_kfbr-392" becomes "KFBR 392".
    static func fromFileName(_ base: String) -> String {
        var label = base.trimmingCharacters(in: .whitespacesAndNewlines)
        label = label.replacingOccurrences(of: "^\\d+[\\s._-]*", with: "", options: .regularExpression)
        label = label.replacingOccurrences(of: "[_\\-.]+", with: " ", options: .regularExpression)
        label = label.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        label = label.trimmingCharacters(in: .whitespacesAndNewlines)
        if label.isEmpty {
            label = base
        }
        return label.uppercased()
    }
}
