import SwiftUI
import AppKit

/// Colours, gradients and metrics taken from the approved Akaa Flix designs.
enum Theme {
    // Base surfaces
    static let background = Color(hex: 0x05060B)
    static let backgroundTop = Color(hex: 0x0A0B16)
    static let panel = Color(hex: 0x0B0D18)
    static let panelHigh = Color(hex: 0x141624)
    static let row = Color(hex: 0x12131D)
    static let rowHighlight = Color(hex: 0x1B1D2B)
    static let stroke = Color.white.opacity(0.10)

    // Brand
    static let red = Color(hex: 0xE5101B)
    static let redBright = Color(hex: 0xFF2A2F)
    static let redDeep = Color(hex: 0x9E0610)
    static let blue = Color(hex: 0x1F6BFF)
    static let blueBright = Color(hex: 0x3D8BFF)
    static let purple = Color(hex: 0x8B2BE2)
    static let magenta = Color(hex: 0xC2187F)
    static let gold = Color(hex: 0xF5C518)
    static let green = Color(hex: 0x22C55E)
    static let greenDeep = Color(hex: 0x0F6B35)

    // Text
    static let textPrimary = Color.white
    static let textSecondary = Color(hex: 0xC3C6D4)
    static let textTertiary = Color(hex: 0x8A8EA3)

    static let redGradient = LinearGradient(gradient: Gradient(colors: [Color(hex: 0xFF3B3F), Color(hex: 0xD10A16), Color(hex: 0x8F0510)]), startPoint: .top, endPoint: .bottom)
    static let blueGradient = LinearGradient(gradient: Gradient(colors: [Color(hex: 0x2F7BFF), Color(hex: 0x1748C9), Color(hex: 0x0D2C82)]), startPoint: .top, endPoint: .bottom)
    static let greenGradient = LinearGradient(gradient: Gradient(colors: [Color(hex: 0x36D46C), Color(hex: 0x14873F)]), startPoint: .top, endPoint: .bottom)
    static let darkPill = LinearGradient(gradient: Gradient(colors: [Color(hex: 0x23252F), Color(hex: 0x111219), Color(hex: 0x08090E)]), startPoint: .top, endPoint: .bottom)
    static let panelFill = LinearGradient(gradient: Gradient(colors: [Color(hex: 0x0E1120).opacity(0.94), Color(hex: 0x070810).opacity(0.96)]), startPoint: .top, endPoint: .bottom)
    static let neonStroke = LinearGradient(gradient: Gradient(colors: [redBright, Color(hex: 0xB0157A), blueBright]), startPoint: .leading, endPoint: .trailing)
    static let neonStrokeVertical = LinearGradient(gradient: Gradient(colors: [redBright, Color(hex: 0x8F1DB0), blueBright]), startPoint: .top, endPoint: .bottom)

    static let cornerLarge: CGFloat = 22
    static let cornerMedium: CGFloat = 14
    static let cornerSmall: CGFloat = 9
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xff) / 255,
                  green: Double((hex >> 8) & 0xff) / 255,
                  blue: Double(hex & 0xff) / 255,
                  opacity: alpha)
    }
}

enum NeonAccent { case red, blue, mixed, purple, magenta, green, none

    var stroke: LinearGradient {
        switch self {
        case .red: return LinearGradient(gradient: Gradient(colors: [Theme.redBright, Theme.red.opacity(0.7)]), startPoint: .top, endPoint: .bottom)
        case .blue: return LinearGradient(gradient: Gradient(colors: [Theme.blueBright, Theme.blue.opacity(0.6)]), startPoint: .top, endPoint: .bottom)
        case .mixed: return Theme.neonStroke
        case .purple: return LinearGradient(gradient: Gradient(colors: [Theme.purple, Color(hex: 0xD04BFF)]), startPoint: .top, endPoint: .bottom)
        case .magenta: return LinearGradient(gradient: Gradient(colors: [Theme.magenta, Color(hex: 0xFF4FB8)]), startPoint: .top, endPoint: .bottom)
        case .green: return LinearGradient(gradient: Gradient(colors: [Theme.green, Theme.greenDeep]), startPoint: .top, endPoint: .bottom)
        case .none: return LinearGradient(gradient: Gradient(colors: [Theme.stroke, Theme.stroke]), startPoint: .top, endPoint: .bottom)
        }
    }

    var glow: Color {
        switch self {
        case .red: return Theme.red
        case .blue: return Theme.blue
        case .mixed: return Color(hex: 0x7A1A8C)
        case .purple: return Theme.purple
        case .magenta: return Theme.magenta
        case .green: return Theme.green
        case .none: return .clear
        }
    }
}

// MARK: - Typography

enum AkaaFont {
    static func title(_ size: CGFloat = 30) -> Font { .system(size: size, weight: .heavy) }
    static func heading(_ size: CGFloat = 20) -> Font { .system(size: size, weight: .bold) }
    static func body(_ size: CGFloat = 15, weight: Font.Weight = .semibold) -> Font { .system(size: size, weight: weight) }
    static func caption(_ size: CGFloat = 12, weight: Font.Weight = .medium) -> Font { .system(size: size, weight: weight) }
    static func number(_ size: CGFloat = 15) -> Font { .system(size: size, weight: .bold).monospacedDigit() }
}

// MARK: - Language / direction

/// Current UI language (set from settings). Views are rebuilt when it changes.
enum Lang {
    static var current: AppLanguage = .arabic
    static var isArabic: Bool { current == .arabic }
    /// Row content direction. The window chrome keeps the approved left-to-right placement
    /// (logo left, tabs, icons right) while rows read right-to-left in Arabic.
    static var contentDirection: LayoutDirection { isArabic ? .rightToLeft : .leftToRight }
}

/// Arabic / English string pair.
func tr(_ arabic: String, _ english: String) -> String { Lang.isArabic ? arabic : english }

extension View {
    /// Rows inside panels follow the reading direction of the active language.
    func contentDirection() -> some View { environment(\.layoutDirection, Lang.contentDirection) }
    func fixedLTR() -> some View { environment(\.layoutDirection, .leftToRight) }
}

// MARK: - Gradient helpers (macOS 11 compatible initialisers)

func linear(_ colors: [Color], _ start: UnitPoint = .top, _ end: UnitPoint = .bottom) -> LinearGradient {
    LinearGradient(gradient: Gradient(colors: colors), startPoint: start, endPoint: end)
}

func radial(_ colors: [Color], center: UnitPoint = .center, start: CGFloat = 0, end: CGFloat) -> RadialGradient {
    RadialGradient(gradient: Gradient(colors: colors), center: center, startRadius: start, endRadius: end)
}
