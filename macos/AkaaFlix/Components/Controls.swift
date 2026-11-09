import SwiftUI

/// "4K ULTRA HD" (gold), "FHD 1080p" (white) and "HD 720p" (blue) badges.
struct QualityBadge: View {
    let quality: String
    var large = false

    private var color: Color {
        switch quality.uppercased() {
        case "4K", "UHD": return Theme.gold
        case "HD": return Theme.blueBright
        default: return .white
        }
    }
    private var subtitle: String? {
        guard large else { return nil }
        switch quality.uppercased() {
        case "4K", "UHD": return "ULTRA HD"
        case "FHD": return "1080p"
        case "HD": return "720p"
        default: return nil
        }
    }

    var body: some View {
        VStack(spacing: -2) {
            Text(quality.uppercased() == "UHD" ? "4K" : quality.uppercased())
                .font(.system(size: large ? 22 : 13, weight: .heavy))
            if let s = subtitle {
                Text(s).font(.system(size: large ? 10 : 7, weight: .bold))
            }
        }
        .foregroundColor(color)
        .padding(.horizontal, large ? 14 : 7)
        .padding(.vertical, large ? 5 : 2)
        .background(RoundedRectangle(cornerRadius: large ? 12 : 6).fill(Color.black.opacity(0.75)))
        .overlay(RoundedRectangle(cornerRadius: large ? 12 : 6).strokeBorder(color, lineWidth: large ? 2 : 1.4))
        .shadow(color: color.opacity(large ? 0.5 : 0.25), radius: large ? 8 : 3)
        .fixedLTR()
    }
}

struct NewBadge: View {
    var body: some View {
        Text(tr("جديد", "NEW"))
            .font(.system(size: 12, weight: .heavy))
            .foregroundColor(.white)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(RoundedRectangle(cornerRadius: 7).fill(Theme.redGradient))
            .shadow(color: Theme.red.opacity(0.6), radius: 4)
    }
}

/// Small rounded tag (genres, "5 مواسم", "+16", "2026").
struct Chip: View {
    let text: String
    var tint: Color = .white
    var filled = false
    var fontSize: CGFloat = 14

    var body: some View {
        Text(text)
            .font(.system(size: fontSize, weight: .bold))
            .foregroundColor(tint)
            .lineLimit(1)
            .padding(.horizontal, 14)
            .padding(.vertical, 6)
            .background(Capsule().fill(filled ? AnyShapeStyleBox.red : AnyShapeStyleBox.dark))
            .overlay(Capsule().strokeBorder(filled ? Theme.redBright : Color.white.opacity(0.22), lineWidth: 1))
    }
}

struct RatingLabel: View {
    let rating: Double
    var size: CGFloat = 15

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: "star.fill").foregroundColor(Theme.gold).font(.system(size: size))
            Text(String(format: "%.1f", rating)).font(.system(size: size, weight: .bold)).foregroundColor(.white)
        }
        .fixedLTR()
    }
}

/// Red (settings) or green (playlists) switch from the designs.
struct NeonToggle: View {
    @Binding var isOn: Bool
    var color: Color = Theme.redBright
    var width: CGFloat = 62

    var body: some View {
        Button(action: { withAnimation(.easeInOut(duration: 0.18)) { isOn.toggle() } }) {
            ZStack(alignment: isOn ? .trailing : .leading) {
                Capsule()
                    .fill(isOn ? linear([color.opacity(0.95), color.opacity(0.7)]) : linear([Color(hex: 0x3A3C48), Color(hex: 0x24252E)]))
                    .frame(width: width, height: width * 0.5)
                    .overlay(Capsule().strokeBorder(Color.white.opacity(0.18), lineWidth: 1))
                    .shadow(color: isOn ? color.opacity(0.7) : .clear, radius: 8)
                Circle()
                    .fill(Color.white)
                    .frame(width: width * 0.42, height: width * 0.42)
                    .shadow(color: .black.opacity(0.4), radius: 2)
                    .padding(.horizontal, width * 0.04)
            }
            .fixedLTR()
        }
        .buttonStyle(PlainButtonStyle())
        .accessibility(value: Text(isOn ? "on" : "off"))
    }
}

/// Dropdown in the settings rows (custom popover so the approved styling is preserved on every macOS).
struct DropdownButton<Value: Hashable>: View {
    let options: [(Value, String)]
    @Binding var selection: Value
    var width: CGFloat = 230
    var height: CGFloat = 42
    var leadingIcon: String?
    var accent: Color = Color.white.opacity(0.25)
    @State private var open = false

    private var currentLabel: String { options.first(where: { $0.0 == selection })?.1 ?? "" }

    var body: some View {
        Button(action: { open.toggle() }) {
            HStack(spacing: 8) {
                Image(systemName: "chevron.down").font(.system(size: 13, weight: .bold))
                Spacer(minLength: 4)
                if let icon = leadingIcon { Image(systemName: icon).font(.system(size: 14)) }
                Text(currentLabel).font(.system(size: 15, weight: .bold)).lineLimit(1).minimumScaleFactor(0.6)
                Spacer(minLength: 4)
            }
            .foregroundColor(.white)
            .padding(.horizontal, 14)
            .frame(width: width, height: height)
            .background(Capsule().fill(Theme.darkPill))
            .overlay(Capsule().strokeBorder(accent, lineWidth: 1.2))
            .contentShape(Capsule())
            .fixedLTR()
        }
        .buttonStyle(PlainButtonStyle())
        .popover(isPresented: $open, arrowEdge: .bottom) {
            ScrollView {
                VStack(spacing: 4) {
                    ForEach(options.indices, id: \.self) { i in
                        let option = options[i]
                        Button(action: { selection = option.0; open = false }) {
                            HStack {
                                Text(option.1).font(.system(size: 14, weight: .semibold))
                                Spacer()
                                if option.0 == selection { Image(systemName: "checkmark").foregroundColor(Theme.redBright) }
                            }
                            .contentDirection()
                            .foregroundColor(.white)
                            .padding(.horizontal, 12)
                            .frame(height: 34)
                            .background(RoundedRectangle(cornerRadius: 8).fill(option.0 == selection ? Theme.red.opacity(0.35) : Color.white.opacity(0.04)))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(8)
            }
            .frame(width: max(width, 200), height: min(CGFloat(options.count) * 38 + 16, 360))
            .background(Theme.panel)
        }
    }
}

/// Search box with magnifier ("بحث في القنوات...").
struct SearchField: View {
    let placeholder: String
    @Binding var text: String
    var accent: NeonAccent = .none
    var height: CGFloat = 46
    var onCommit: () -> Void = {}

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").font(.system(size: 18, weight: .semibold)).foregroundColor(.white.opacity(0.85))
            ZStack(alignment: Lang.isArabic ? .trailing : .leading) {
                if text.isEmpty {
                    Text(placeholder).font(.system(size: 16, weight: .medium)).foregroundColor(Theme.textTertiary)
                }
                TextField("", text: $text, onCommit: onCommit)
                    .textFieldStyle(PlainTextFieldStyle())
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.white)
                    .multilineTextAlignment(Lang.isArabic ? .trailing : .leading)
            }
            if !text.isEmpty {
                Button(action: { text = "" }) {
                    Image(systemName: "xmark.circle.fill").foregroundColor(Theme.textTertiary)
                }.buttonStyle(PlainButtonStyle())
            }
        }
        .padding(.horizontal, 16)
        .frame(height: height)
        .background(Capsule().fill(Color(hex: 0x10121C)))
        .overlay(Capsule().strokeBorder(accent == .none ? linear([Color.white.opacity(0.2), Color.white.opacity(0.06)]) : accent.stroke, lineWidth: 1.4))
        .shadow(color: accent.glow.opacity(0.4), radius: 8)
        .fixedLTR()
    }
}

/// Red progress line (Continue Watching cards, player timeline background). Always fills left→right.
struct ProgressBar: View {
    let fraction: Double
    var height: CGFloat = 5
    var track: Color = Color.white.opacity(0.25)

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(track)
                Capsule().fill(Theme.redGradient)
                    .frame(width: max(0, min(1, CGFloat(fraction))) * geo.size.width)
                    .shadow(color: Theme.red.opacity(0.8), radius: 4)
            }
        }
        .frame(height: height)
        .fixedLTR()
    }
}

/// Seekable timeline / volume slider with the red glowing knob.
struct NeonSlider: View {
    @Binding var value: Double          // 0...1
    var height: CGFloat = 6
    var knob: CGFloat = 20
    var onEditingChanged: (Bool) -> Void = { _ in }
    @State private var dragging = false

    var body: some View {
        GeometryReader { geo in
            let w = max(1, geo.size.width)
            let x = CGFloat(min(1, max(0, value))) * w
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.3)).frame(height: height)
                Capsule().fill(linear([Theme.redBright, Theme.red], .leading, .trailing)).frame(width: x, height: height)
                    .shadow(color: Theme.red, radius: 5)
                Circle().fill(Color.white)
                    .frame(width: knob, height: knob)
                    .overlay(Circle().strokeBorder(Theme.redBright, lineWidth: 3))
                    .shadow(color: Theme.red, radius: dragging ? 10 : 6)
                    .offset(x: x - knob / 2)
            }
            .frame(height: max(height, knob))
            .contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .onChanged { g in
                    if !dragging { dragging = true; onEditingChanged(true) }
                    value = Double(min(1, max(0, g.location.x / w)))
                }
                .onEnded { _ in dragging = false; onEditingChanged(false) })
        }
        .frame(height: max(height, knob))
        .fixedLTR()
    }
}

struct EmptyStateView: View {
    let systemImage: String
    let title: String
    var message: String?
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: 14) {
            Image(systemName: systemImage)
                .font(.system(size: 46, weight: .light))
                .foregroundColor(Theme.redBright)
                .shadow(color: Theme.red, radius: 10)
            Text(title).font(.system(size: 20, weight: .bold)).foregroundColor(.white).multilineTextAlignment(.center)
            if let m = message {
                Text(m).font(.system(size: 14)).foregroundColor(Theme.textSecondary).multilineTextAlignment(.center).frame(maxWidth: 460)
            }
            if let t = actionTitle, let a = action {
                NeonButton(title: t, systemImage: "plus.circle", kind: .red, action: a).padding(.top, 6)
            }
        }
        .padding(30)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Horizontal row with the round chevron buttons at both ends (Home rows, cast, similar titles).
struct CarouselRow<Item: Identifiable, Cell: View>: View {
    let items: [Item]
    var spacing: CGFloat = 16
    var arrowSize: CGFloat = 44
    let cell: (Item) -> Cell
    @State private var anchor = 0

    var body: some View {
        ScrollViewReader { proxy in
            HStack(spacing: 10) {
                arrow("chevron.left") { scroll(proxy, by: -4) }
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(spacing: spacing) {
                        ForEach(Array(items.enumerated()), id: \.element.id) { index, item in
                            cell(item).id(index)
                        }
                    }
                    .padding(.vertical, 10)
                    .padding(.horizontal, 4)
                }
                arrow("chevron.right") { scroll(proxy, by: 4) }
            }
            .fixedLTR()
        }
    }

    private func arrow(_ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: arrowSize * 0.4, weight: .bold))
                .foregroundColor(.white)
                .frame(width: arrowSize, height: arrowSize * 1.15)
                .background(RoundedRectangle(cornerRadius: 12).fill(Theme.darkPill))
                .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color.white.opacity(0.18), lineWidth: 1))
        }
        .buttonStyle(PlainButtonStyle())
    }

    private func scroll(_ proxy: ScrollViewProxy, by delta: Int) {
        guard !items.isEmpty else { return }
        anchor = min(max(0, anchor + delta), items.count - 1)
        withAnimation(.easeInOut(duration: 0.35)) { proxy.scrollTo(anchor, anchor: .leading) }
    }
}

/// Labelled input row of the playlist forms (icon + label on the reading side, field on the other).
struct FormFieldRow: View {
    let systemImage: String
    let label: String
    let placeholder: String
    @Binding var text: String
    var secure = false
    var ltrValue = true
    @State private var reveal = false

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: systemImage).font(.system(size: 22, weight: .regular)).foregroundColor(.white).frame(width: 30)
            Text(label).font(.system(size: 17, weight: .bold)).foregroundColor(.white).frame(minWidth: 120, alignment: .leading)
            ZStack(alignment: .leading) {
                if text.isEmpty {
                    Text(placeholder).font(.system(size: 16, weight: .medium)).foregroundColor(Theme.textTertiary)
                        .frame(maxWidth: .infinity, alignment: ltrValue ? .leading : .trailing)
                }
                Group {
                    if secure && !reveal {
                        SecureField("", text: $text)
                    } else {
                        TextField("", text: $text)
                    }
                }
                .textFieldStyle(PlainTextFieldStyle())
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(.white)
                .disableAutocorrection(true)
            }
            .padding(.horizontal, 12)
            .frame(height: 44)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color.black.opacity(text.isEmpty ? 0 : 0.35)))
            .fixedLTR()
            if secure {
                Button(action: { reveal.toggle() }) {
                    Image(systemName: reveal ? "eye.slash" : "eye").font(.system(size: 20)).foregroundColor(.white)
                }.buttonStyle(PlainButtonStyle())
            }
        }
        .padding(.horizontal, 18)
        .frame(height: 64)
        .background(RoundedRectangle(cornerRadius: 16).fill(linear([Color(hex: 0x161A2A), Color(hex: 0x0B0D16)])))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(linear([Color.white.opacity(0.22), Theme.blue.opacity(0.25)]), lineWidth: 1.2))
        .contentDirection()
    }
}

/// Settings row: icon + title on the reading side, control on the opposite side.
struct SettingsRow<Control: View>: View {
    let systemImage: String
    let title: String
    var subtitle: String?
    let control: Control

    init(systemImage: String, title: String, subtitle: String? = nil, @ViewBuilder control: () -> Control) {
        self.systemImage = systemImage
        self.title = title
        self.subtitle = subtitle
        self.control = control()
    }

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: systemImage)
                .font(.system(size: 22, weight: .regular))
                .foregroundColor(.white)
                .frame(width: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 17, weight: .bold)).foregroundColor(.white).lineLimit(2)
                if let s = subtitle { Text(s).font(.system(size: 12, weight: .medium)).foregroundColor(Theme.textTertiary).lineLimit(2) }
            }
            Spacer(minLength: 12)
            control
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 56)
        .background(RoundedRectangle(cornerRadius: 14).fill(linear([Color(hex: 0x141724), Color(hex: 0x0B0C14)])))
        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.white.opacity(0.08), lineWidth: 1))
        .contentDirection()
    }
}

/// Panel heading with icon ("عام", "جودة العرض", …).
struct PanelHeader: View {
    let systemImage: String
    let title: String
    var subtitle: String?
    var size: CGFloat = 26

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: systemImage).font(.system(size: size * 0.95, weight: .regular)).foregroundColor(.white)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: size, weight: .heavy)).foregroundColor(.white)
                if let s = subtitle { Text(s).font(.system(size: size * 0.55, weight: .semibold)).foregroundColor(Theme.textSecondary) }
            }
            Spacer()
        }
        .contentDirection()
    }
}

struct Toast: Equatable {
    enum Kind { case info, success, error }
    var message: String
    var kind: Kind = .info
}

struct ToastView: View {
    let toast: Toast
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: toast.kind == .error ? "exclamationmark.triangle.fill" : toast.kind == .success ? "checkmark.circle.fill" : "info.circle.fill")
                .foregroundColor(toast.kind == .error ? Theme.redBright : toast.kind == .success ? Theme.green : Theme.blueBright)
            Text(toast.message).font(.system(size: 15, weight: .semibold)).foregroundColor(.white).lineLimit(3)
        }
        .contentDirection()
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .background(Capsule().fill(Color(hex: 0x11131E).opacity(0.97)))
        .overlay(Capsule().strokeBorder(toast.kind == .error ? NeonAccent.red.stroke : NeonAccent.mixed.stroke, lineWidth: 1.3))
        .shadow(color: .black.opacity(0.5), radius: 12)
    }
}
