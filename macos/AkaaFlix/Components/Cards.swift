import SwiftUI

/// Poster card of the Movies / Series grids (quality badge, "جديد", title, rating, date).
struct PosterCard: View {
    let title: String
    let imageURL: String?
    var quality: String?
    var isNew = false
    var rating: Double?
    var dateText: String?
    var accentIndex = 0
    var showsDetails = true
    let action: () -> Void
    @State private var hovering = false

    private var accent: NeonAccent { [.red, .blue, .red, .blue, .purple, .magenta][accentIndex % 6] }

    var body: some View {
        Button(action: action) {
            GeometryReader { geo in
                ZStack(alignment: .bottom) {
                    RemoteImage(url: imageURL, contentMode: .fill, placeholder: .poster, maxPixel: 520, title: title)
                        .frame(width: geo.size.width, height: geo.size.height)
                        .clipped()
                    linear([.clear, .clear, Color.black.opacity(0.65), Color.black.opacity(0.95)])
                    VStack(spacing: 4) {
                        let titles = ContentText.displayTitles(title)
                        Text(titles.primary)
                            .font(.system(size: max(13, geo.size.width * 0.09), weight: .heavy))
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                            .shadow(color: .black, radius: 3)
                        if showsDetails {
                            if let sub = titles.secondary {
                                Text(sub).font(.system(size: max(10, geo.size.width * 0.06), weight: .medium)).foregroundColor(.white.opacity(0.9)).lineLimit(1)
                            }
                            if let r = rating { RatingLabel(rating: r, size: max(12, geo.size.width * 0.075)) }
                            if let d = dateText {
                                HStack(spacing: 5) {
                                    Image(systemName: "calendar").font(.system(size: 11))
                                    Text(d).font(.system(size: max(11, geo.size.width * 0.065), weight: .bold)).monospacedDigit()
                                }
                                .foregroundColor(.white)
                                .padding(.horizontal, 10).padding(.vertical, 3)
                                .background(Capsule().fill(Color.black.opacity(0.6)))
                                .overlay(Capsule().strokeBorder(Color.white.opacity(0.3), lineWidth: 1))
                                .fixedLTR()
                            }
                        }
                    }
                    .padding(.horizontal, 8)
                    .padding(.bottom, 10)
                }
                .overlay(
                    HStack(alignment: .top) {
                        if let q = quality { QualityBadge(quality: q) }
                        Spacer()
                        if isNew { NewBadge() }
                    }
                    .padding(8), alignment: .top)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(accent.stroke, lineWidth: hovering ? 2.4 : 1.4))
                .shadow(color: accent.glow.opacity(hovering ? 0.8 : 0.35), radius: hovering ? 14 : 7)
                .scaleEffect(hovering ? 1.03 : 1)
            }
            .aspectRatio(2.0 / 3.0, contentMode: .fit)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
        .onHover { h in withAnimation(.easeOut(duration: 0.15)) { hovering = h } }
    }
}

/// Wide "Continue Watching" card (thumbnail, play button, title, quality and progress).
struct ContinueCard: View {
    let progress: WatchProgress
    var quality: String?
    let action: () -> Void
    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            ZStack {
                RemoteImage(url: progress.image, contentMode: .fill, placeholder: .backdrop, maxPixel: 600)
                    .frame(width: 520, height: 110)
                    .clipped()
                    .overlay(linear([Color.black.opacity(0.05), Color.black.opacity(0.75), Color.black.opacity(0.85)], .leading, .trailing))
                HStack(spacing: 16) {
                    Image(systemName: "play.fill")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .frame(width: 46, height: 46)
                        .background(Circle().fill(Color.black.opacity(0.55)))
                        .overlay(Circle().strokeBorder(Color.white, lineWidth: 2))
                    Spacer()
                    VStack(alignment: .trailing, spacing: 10) {
                        HStack {
                            if let q = quality { QualityBadge(quality: q) }
                            Spacer()
                            Text(progress.title).font(.system(size: 18, weight: .heavy)).foregroundColor(.white).lineLimit(1)
                        }
                        HStack(spacing: 10) {
                            Text("\(Int((progress.fraction * 100).rounded()))%").font(.system(size: 16, weight: .bold)).foregroundColor(.white).monospacedDigit()
                            ProgressBar(fraction: progress.fraction, height: 6)
                        }
                    }
                    .frame(width: 300)
                }
                .padding(.horizontal, 22)
            }
            .frame(width: 520, height: 110)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Theme.neonStroke, lineWidth: hovering ? 2.2 : 1.3))
            .shadow(color: Color(hex: 0x6A1AAE).opacity(hovering ? 0.8 : 0.45), radius: hovering ? 14 : 8)
            .fixedLTR()
        }
        .buttonStyle(PlainButtonStyle())
        .onHover { hovering = $0 }
    }
}

/// Channel logo tile (logo image or initials placeholder).
struct ChannelLogo: View {
    let url: String?
    let name: String
    var width: CGFloat = 72
    var height: CGFloat = 40

    var body: some View {
        RemoteImage(url: url, contentMode: .fit, placeholder: .channel, maxPixel: 200, title: name)
            .frame(width: width, height: height)
            .padding(3)
            .background(RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.92)))
            .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

/// Home shortcut tile ("المفضلة", "الأفلام", …) — optional artwork from the approved design.
struct ShortcutTile: View {
    let title: String
    let systemImage: String
    let accent: NeonAccent
    var artwork: String?
    var wide = false
    let action: () -> Void
    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(linear([accent.glow.opacity(0.55), accent.glow.opacity(0.18), Color.black.opacity(0.8)]))
                    if let art = artwork {
                        // Approved tiles: label on the left, photo on the right.
                        HStack(spacing: 0) {
                            Spacer(minLength: 0)
                            Image(art)
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: geo.size.width * 0.58, height: geo.size.height)
                                .clipped()
                                .mask(linear([.clear, Color.black.opacity(0.85), .black, .black], .leading, .trailing))
                        }
                    }
                    VStack(spacing: 6) {
                        Image(systemName: systemImage).font(.system(size: 30, weight: .regular))
                        Text(title).font(.system(size: 17, weight: .heavy)).lineLimit(1).minimumScaleFactor(0.6)
                    }
                    .foregroundColor(.white)
                    .shadow(color: .black.opacity(0.7), radius: 4)
                    .frame(width: wide ? min(170, geo.size.width * 0.5) : geo.size.width)
                    .padding(.leading, wide ? 8 : 0)
                }
            }
            .frame(height: 112)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(accent.stroke, lineWidth: hovering ? 2.4 : 1.5))
            .shadow(color: accent.glow.opacity(hovering ? 0.9 : 0.55), radius: hovering ? 16 : 10)
            .contentShape(Rectangle())
            .fixedLTR()
        }
        .buttonStyle(PlainButtonStyle())
        .onHover { hovering = $0 }
    }
}

/// Group row of the left panels ("الأكثر مشاهدة", "المفضلة", categories with counts).
struct GroupRow: View {
    let title: String
    var systemImage: String?
    var emoji: String?
    var iconColor: Color = .white
    var count: Int?
    var isSelected = false
    var showParentheses = true
    let action: () -> Void
    @State private var hovering = false

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Group {
                    if let e = emoji {
                        Text(e).font(.system(size: 22))
                    } else if let icon = systemImage {
                        Image(systemName: icon).font(.system(size: 20, weight: .semibold)).foregroundColor(iconColor)
                    }
                }
                .frame(width: 30)
                Text(title).font(.system(size: 17, weight: .bold)).foregroundColor(.white).lineLimit(1)
                Spacer(minLength: 6)
                if let c = count {
                    Text(showParentheses ? "(\(c))" : "\(c)")
                        .font(.system(size: 16, weight: .bold))
                        .monospacedDigit()
                        .foregroundColor(.white)
                        .padding(.horizontal, 12)
                        .frame(height: 30)
                        .background(Capsule().fill(Color.black.opacity(isSelected ? 0.25 : 0.45)))
                        .fixedLTR()
                }
            }
            .padding(.horizontal, 14)
            .frame(height: 46)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(isSelected ? Theme.redGradient : Theme.darkPill))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(isSelected ? linear([Color(hex: 0xFF8A8A), Theme.redBright]) : linear([Color.white.opacity(hovering ? 0.3 : 0.14), Color.white.opacity(0.04)]), lineWidth: isSelected ? 1.6 : 1))
            .shadow(color: isSelected ? Theme.red.opacity(0.7) : .clear, radius: 10)
            .contentShape(Rectangle())
            .contentDirection()
        }
        .buttonStyle(PlainButtonStyle())
        .onHover { hovering = $0 }
    }
}

extension CategoryKind {
    var systemImage: String {
        switch self {
        case .arabic: return "antenna.radiowaves.left.and.right"
        case .international: return "globe"
        case .movies: return "film"
        case .series: return "square.stack.3d.up"
        case .kids: return "face.smiling"
        case .documentary: return "leaf"
        case .news: return "newspaper"
        case .sports: return "sportscourt"
        case .adult: return "lock"
        case .other: return "tv"
        }
    }
}

extension ContentCategory {
    /// Emoji flag for country categories ("قنوات مصر" → 🇪🇬) or nil.
    var flag: String? { CategoryClassifier.flagCountry(name).map(CategoryClassifier.flagEmoji) }

    var movieIcon: String {
        let n = ContentText.normalize(name)
        if n.contains("action") || n.contains("اكشن") { return "scope" }
        if n.contains("advent") || n.contains("مغامر") { return "safari" }
        if n.contains("sci") || n.contains("خيال") { return "sparkles" }
        if n.contains("horror") || n.contains("رعب") { return "moon.stars" }
        if n.contains("comed") || n.contains("كوميد") { return "theatermasks" }
        if n.contains("drama") || n.contains("دراما") { return "theatermasks.fill" }
        if n.contains("new") || n.contains("جديد") { return "flame.fill" }
        if n.contains("anim") || n.contains("كرتون") || n.contains("انمي") { return "face.smiling" }
        return kind.systemImage
    }
}
