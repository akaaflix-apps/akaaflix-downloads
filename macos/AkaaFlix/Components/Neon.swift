import SwiftUI

/// Window background: deep navy/black with the red (left) and blue (right) light waves of the designs.
struct AppBackground: View {
    var intensity: Double = 1

    var body: some View {
        GeometryReader { geo in
            ZStack {
                linear([Theme.backgroundTop, Theme.background, Color.black])
                radial([Theme.red.opacity(0.28 * intensity), .clear], center: UnitPoint(x: 0.08, y: 1.02), end: geo.size.width * 0.45)
                radial([Theme.blue.opacity(0.24 * intensity), .clear], center: UnitPoint(x: 0.95, y: 1.0), end: geo.size.width * 0.42)
                radial([Theme.red.opacity(0.12 * intensity), .clear], center: UnitPoint(x: 0.35, y: 0.0), end: geo.size.width * 0.35)
                radial([Theme.blue.opacity(0.12 * intensity), .clear], center: UnitPoint(x: 0.82, y: 0.02), end: geo.size.width * 0.32)
                WaveLines()
                    .opacity(0.55 * intensity)
                    .frame(height: 120)
                    .frame(maxHeight: .infinity, alignment: .bottom)
            }
        }
        .ignoresSafeArea()
    }
}

/// Thin glowing waves along the bottom edge (red on the left, blue on the right).
struct WaveLines: View {
    var body: some View {
        GeometryReader { geo in
            ZStack {
                ForEach(0..<3, id: \.self) { i in
                    WaveShape(phase: Double(i) * 0.7, amplitude: 14 + CGFloat(i) * 6)
                        .stroke(linear([Theme.redBright.opacity(0.9), Theme.magenta.opacity(0.4), Theme.blueBright.opacity(0.9)], .leading, .trailing), lineWidth: 1.2)
                        .blur(radius: CGFloat(i) * 0.6)
                }
                WaveShape(phase: 0.3, amplitude: 18)
                    .stroke(linear([Theme.red, Theme.blue], .leading, .trailing), lineWidth: 6)
                    .blur(radius: 12)
                    .opacity(0.6)
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
        .allowsHitTesting(false)
    }
}

struct WaveShape: Shape {
    var phase: Double
    var amplitude: CGFloat

    func path(in rect: CGRect) -> Path {
        var p = Path()
        let steps = 60
        for s in 0...steps {
            let x = rect.width * CGFloat(s) / CGFloat(steps)
            let t = Double(s) / Double(steps) * .pi * 2.2 + phase
            let y = rect.midY + CGFloat(sin(t)) * amplitude
            if s == 0 { p.move(to: CGPoint(x: x, y: y)) } else { p.addLine(to: CGPoint(x: x, y: y)) }
        }
        return p
    }
}

/// Dark glass panel with the neon gradient border used throughout the designs.
struct NeonPanel<Content: View>: View {
    var accent: NeonAccent = .mixed
    var corner: CGFloat = Theme.cornerLarge
    var padding: CGFloat = 16
    var glow: Bool = true
    let content: Content

    init(accent: NeonAccent = .mixed, corner: CGFloat = Theme.cornerLarge, padding: CGFloat = 16, glow: Bool = true, @ViewBuilder content: () -> Content) {
        self.accent = accent
        self.corner = corner
        self.padding = padding
        self.glow = glow
        self.content = content()
    }

    var body: some View {
        content
            .padding(padding)
            .background(
                RoundedRectangle(cornerRadius: corner, style: .continuous)
                    .fill(Theme.panelFill)
            )
            .overlay(
                RoundedRectangle(cornerRadius: corner, style: .continuous)
                    .strokeBorder(accent.stroke, lineWidth: 1.6)
            )
            .shadow(color: glow ? accent.glow.opacity(0.45) : .clear, radius: glow ? 14 : 0)
    }
}

/// Top navigation tab ("الرئيسية", "القنوات المباشرة", …).
struct PillTabButton: View {
    let title: String
    let systemImage: String
    let isActive: Bool
    var compact = false
    let action: () -> Void
    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                Image(systemName: systemImage)
                    .font(.system(size: compact ? 16 : 20, weight: .medium))
                Text(title)
                    .font(.system(size: compact ? 15 : 18, weight: .bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .contentDirection()
            .foregroundColor(.white)
            .padding(.horizontal, compact ? 14 : 22)
            .frame(height: compact ? 40 : 50)
            .frame(minWidth: compact ? 110 : 150)
            .background(
                Capsule().fill(isActive ? AnyShapeStyleBox.red : AnyShapeStyleBox.dark)
            )
            .overlay(
                Capsule().strokeBorder(isActive ? linear([Color(hex: 0xFF7A7A), Theme.redBright]) : linear([Color.white.opacity(hovering ? 0.35 : 0.18), Color.white.opacity(0.05)]), lineWidth: isActive ? 1.6 : 1)
            )
            .shadow(color: isActive ? Theme.red.opacity(0.75) : .clear, radius: 12)
            .contentShape(Capsule())
        }
        .buttonStyle(PlainButtonStyle())
        .onHover { hovering = $0 }
    }
}

/// Type-erased fills (SwiftUI on macOS 11 has no AnyShapeStyle).
enum AnyShapeStyleBox {
    static var red: LinearGradient { Theme.redGradient }
    static var dark: LinearGradient { Theme.darkPill }
}

enum NeonButtonStyleKind { case red, blue, dark, green, gold, outlineRed, outlineBlue, outlineGreen }

/// Rounded action button ("شاهد الآن", "إضافة القائمة", "اختبار الاتصال", …).
struct NeonButton: View {
    let title: String
    var systemImage: String?
    var kind: NeonButtonStyleKind = .red
    var height: CGFloat = 46
    var fontSize: CGFloat = 16
    var expand = false
    var isLoading = false
    let action: () -> Void
    @State private var hovering = false
    @Environment(\.isEnabled) private var isEnabled

    private var fill: LinearGradient {
        switch kind {
        case .red: return Theme.redGradient
        case .blue: return Theme.blueGradient
        case .green: return Theme.greenGradient
        case .gold: return linear([Color(hex: 0x3A2E05), Color(hex: 0x1C1602)])
        case .dark, .outlineRed, .outlineBlue, .outlineGreen: return Theme.darkPill
        }
    }

    private var border: LinearGradient {
        switch kind {
        case .red, .outlineRed: return linear([Color(hex: 0xFF8080), Theme.redBright])
        case .blue, .outlineBlue: return linear([Color(hex: 0x7FB2FF), Theme.blueBright])
        case .green, .outlineGreen: return linear([Color(hex: 0x7CF0A0), Theme.green])
        case .gold: return linear([Color(hex: 0xFFE27A), Theme.gold])
        case .dark: return linear([Color.white.opacity(0.28), Color.white.opacity(0.06)])
        }
    }

    private var glow: Color {
        switch kind {
        case .red, .outlineRed: return Theme.red
        case .blue, .outlineBlue: return Theme.blue
        case .green, .outlineGreen: return Theme.green
        case .gold: return Theme.gold
        case .dark: return .clear
        }
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if isLoading {
                    ProgressView().scaleEffect(0.6).frame(width: 18, height: 18)
                } else if let icon = systemImage {
                    Image(systemName: icon).font(.system(size: fontSize + 3, weight: .semibold))
                }
                Text(title)
                    .font(.system(size: fontSize, weight: .bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }
            .contentDirection()
            .foregroundColor(kind == .gold ? Theme.gold : .white)
            .padding(.horizontal, 20)
            .frame(height: height)
            .frame(maxWidth: expand ? .infinity : nil)
            .background(RoundedRectangle(cornerRadius: height / 2.6, style: .continuous).fill(fill))
            .overlay(RoundedRectangle(cornerRadius: height / 2.6, style: .continuous).strokeBorder(border, lineWidth: 1.5))
            .shadow(color: glow.opacity(hovering ? 0.85 : 0.55), radius: hovering ? 14 : 9)
            .opacity(isEnabled ? 1 : 0.45)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
        .onHover { hovering = $0 }
    }
}

/// Round icon button (search, notifications, favorites, settings, player controls).
struct CircleIconButton: View {
    let systemImage: String
    var isActive = false
    var showsDot = false
    var size: CGFloat = 50
    var iconSize: CGFloat?
    var help: String?
    let action: () -> Void
    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            ZStack(alignment: .topTrailing) {
                Image(systemName: systemImage)
                    .font(.system(size: iconSize ?? size * 0.42, weight: .medium))
                    .foregroundColor(.white)
                    .frame(width: size, height: size)
                    .background(Circle().fill(isActive ? Theme.redGradient : Theme.darkPill))
                    .overlay(Circle().strokeBorder(isActive ? linear([Color(hex: 0xFF8080), Theme.redBright]) : linear([Color.white.opacity(hovering ? 0.4 : 0.22), Color.white.opacity(0.05)]), lineWidth: 1.3))
                    .shadow(color: isActive ? Theme.red.opacity(0.8) : Color.black.opacity(0.4), radius: isActive ? 12 : 4)
                if showsDot {
                    Circle().fill(Theme.redBright)
                        .frame(width: size * 0.2, height: size * 0.2)
                        .overlay(Circle().stroke(Color.black, lineWidth: 1.5))
                        .offset(x: -size * 0.12, y: size * 0.1)
                }
            }
            .contentShape(Circle())
        }
        .buttonStyle(PlainButtonStyle())
        .onHover { hovering = $0 }
        .help(help ?? "")
    }
}

/// Section header with the red slash used on Home ("/ مواصلة المشاهدة").
struct SectionTitle: View {
    let title: String
    var trailing: AnyView?

    var body: some View {
        HStack(spacing: 10) {
            Rectangle()
                .fill(Theme.redGradient)
                .frame(width: 6, height: 26)
                .rotationEffect(.degrees(18))
                .shadow(color: Theme.red, radius: 6)
            Text(title)
                .font(.system(size: 22, weight: .heavy))
                .foregroundColor(.white)
            Spacer()
            if let trailing = trailing { trailing }
        }
        .contentDirection()
    }
}

/// The approved Akaa Flix wordmark (artwork from the brand PDF, never redrawn).
struct LogoView: View {
    var height: CGFloat = 44
    var body: some View {
        Image("AkaaFlixLogo")
            .resizable()
            .interpolation(.high)
            .aspectRatio(contentMode: .fit)
            .frame(height: height)
            .accessibility(label: Text("Akaa Flix"))
    }
}

/// Small vertical divider used in headers ("AkaaFlix | Live TV").
struct HeaderDivider: View {
    var height: CGFloat = 30
    var body: some View {
        Rectangle().fill(Color.white.opacity(0.35)).frame(width: 1.5, height: height)
    }
}
