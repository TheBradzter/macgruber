import SwiftUI

extension Color {
    init(hex: UInt32, opacity: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: opacity)
    }
}

/// Same palette as the Android app: army olive, explosion orange/red, mullet gold.
enum Theme {
    static let black = Color(hex: 0x121212)
    static let olive = Color(hex: 0x4B5320)
    static let oliveDark = Color(hex: 0x2F3414)
    static let orange = Color(hex: 0xFF8A1E)
    static let fire = Color(hex: 0xC8361A)
    static let gold = Color(hex: 0xFFC233)
    static let green = Color(hex: 0x3F8F2E)
    static let greenPressed = Color(hex: 0x2E6B21)
    static let red = Color(hex: 0xC8102E)
    static let redPressed = Color(hex: 0x8E0B20)
    static let grey = Color(hex: 0xCFCFC4)
}

/// Solid button with white bold text: green for actions, red for cancel and stop.
struct SolidButtonStyle: ButtonStyle {
    enum Kind { case green, red }

    var kind: Kind = .green
    var fullWidth = false

    func makeBody(configuration: Configuration) -> some View {
        let normal = kind == .green ? Theme.green : Theme.red
        let pressed = kind == .green ? Theme.greenPressed : Theme.redPressed
        return configuration.label
            .font(.system(size: 15, weight: .bold))
            .foregroundColor(.white)
            .multilineTextAlignment(.center)
            .padding(.horizontal, 16)
            .frame(maxWidth: fullWidth ? .infinity : nil, minHeight: 46)
            .background(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .fill(configuration.isPressed ? pressed : normal)
            )
            .contentShape(Rectangle())
    }
}
