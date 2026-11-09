import AppKit
import SwiftUI

/// Values configurable at build time (Info.plist keys set from xcconfig).
enum BrandInfo {
    private static func plist(_ key: String) -> String? {
        let v = Bundle.main.object(forInfoDictionaryKey: key) as? String
        return (v?.isEmpty ?? true) ? nil : v
    }
    static var website: String { plist("AKWebsite") ?? "www.akaaflix.com" }
    static var email: String { plist("AKSupportEmail") ?? "support@akaaflix.com" }
    static var developer: String { plist("AKDeveloper") ?? "Akaa Team" }
    /// WhatsApp number in international format, e.g. +201234567890 (set AKAAFLIX_SUPPORT_WHATSAPP).
    static var whatsapp: String? { plist("AKSupportWhatsApp") }
    static var whatsappLink: String {
        if let n = whatsapp {
            let digits = n.filter { $0.isNumber }
            return "https://wa.me/\(digits)"
        }
        return "https://\(website)"
    }
    static var appVersion: String { (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? AppInfo.version }
    static var build: String { (Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String) ?? "1" }
}

/// Left artwork of the About screen ("AkaaFlix — عالمك من الترفيه — More Than Entertainment").
struct AboutHeroPanel: View {
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 24, style: .continuous).fill(Color.black)
            // Approved artwork (wordmark, "عالمك من الترفيه", "More Than Entertainment") used as supplied.
            Image("AboutHeroArt").resizable().aspectRatio(contentMode: .fill)
                .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        }
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(Theme.neonStrokeVertical, lineWidth: 1.6))
        .shadow(color: Theme.blue.opacity(0.35), radius: 12)
    }
}

struct AboutView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        NeonPanel(accent: .blue, padding: 24) {
            VStack(spacing: 20) {
                PanelHeader(systemImage: "info.circle", title: tr("حول التطبيق", "About"), size: 36)
                Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1)
                HStack(alignment: .top, spacing: 24) {
                    Text(tr("Akaa Flix هو تطبيق متكامل لمشاهدة القنوات المباشرة والأفلام والمسلسلات بجودة عالية، مع تجربة استخدام سهلة وواجهة عصرية مصممة خصيصاً لك.",
                            "Akaa Flix is a complete app for watching live channels, movies and series in high quality, with an easy experience and a modern interface designed for you."))
                        .font(.system(size: 19, weight: .semibold)).foregroundColor(.white).lineSpacing(6)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    VStack(alignment: .leading, spacing: 8) {
                        LogoView(height: 50)
                        Text("IPTV & Movies & Series").font(.system(size: 21, weight: .semibold)).foregroundColor(.white)
                        Text("\(AppInfo.displayVersion) for macOS").font(.system(size: 19, weight: .bold)).foregroundColor(.white)
                            .padding(.horizontal, 18).padding(.vertical, 6)
                            .background(Capsule().fill(Theme.darkPill))
                            .overlay(Capsule().strokeBorder(Color.white.opacity(0.3), lineWidth: 1))
                    }
                    .fixedLTR()
                    Image("AppIconArt").resizable().aspectRatio(contentMode: .fit).frame(width: 140, height: 140)
                        .clipShape(RoundedRectangle(cornerRadius: 26))
                        .overlay(RoundedRectangle(cornerRadius: 26).strokeBorder(Color.white.opacity(0.2), lineWidth: 1))
                }
                .contentDirection()
                HStack(spacing: 14) {
                    feature("tv", tr("قنوات مباشرة", "Live Channels")) { model.open(.live) }
                    feature("film", tr("أفلام", "Movies")) { model.open(.movies) }
                    feature("square.stack.3d.up", tr("مسلسلات", "Series")) { model.open(.series) }
                    feature("icloud.and.arrow.down", tr("تحميلات", "Downloads")) { model.open(.downloads) }
                    feature("star", tr("المفضلة", "Favorites")) { model.open(.favorites) }
                }
                .contentDirection()
                HStack(alignment: .top, spacing: 18) {
                    VStack(spacing: 0) {
                        infoRow("a.square", tr("اسم التطبيق", "App name"), AppInfo.name)
                        infoRow("gearshape", tr("الإصدار", "Version"), "V\(BrandInfo.appVersion)")
                        infoRow("laptopcomputer", tr("نظام التشغيل", "Platform"), "macOS")
                        infoRow("person.2", tr("المطور", "Developer"), BrandInfo.developer)
                        infoRow("globe", tr("الموقع الرسمي", "Website"), BrandInfo.website, link: "https://\(BrandInfo.website)")
                        infoRow("envelope", tr("البريد الإلكتروني", "Email"), BrandInfo.email, link: "mailto:\(BrandInfo.email)")
                    }
                    .padding(.vertical, 6)
                    .background(RoundedRectangle(cornerRadius: 18).fill(Color.black.opacity(0.35)))
                    .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(Color.white.opacity(0.12), lineWidth: 1))
                    whatsappCard
                }
                .contentDirection()
                Text(SystemInfo.summary + " · " + EngineFactory.primaryEngineName + " " + EngineFactory.primaryEngineVersion)
                    .font(.system(size: 12, weight: .medium)).foregroundColor(Theme.textTertiary).fixedLTR()
                HStack(spacing: 14) {
                    Image(systemName: "checkmark.shield").font(.system(size: 34)).foregroundColor(Theme.blueBright)
                    Spacer()
                    Text(tr("جميع الحقوق محفوظة © 2026 Akaa Flix", "© 2026 Akaa Flix. All rights reserved."))
                        .font(.system(size: 20, weight: .bold)).foregroundColor(.white)
                    Spacer()
                }
                .padding(.horizontal, 22).frame(height: 70)
                .background(RoundedRectangle(cornerRadius: 18).fill(Theme.blue.opacity(0.18)))
                .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NeonAccent.blue.stroke, lineWidth: 1.2))
                .fixedLTR()
                Button(tr("تراخيص المكونات مفتوحة المصدر", "Open-source licences")) { Licenses.show() }
                    .buttonStyle(PlainButtonStyle())
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(Theme.textSecondary)
            }
        }
    }

    private func feature(_ icon: String, _ title: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 10) {
                Image(systemName: icon).font(.system(size: 30))
                Text(title).font(.system(size: 18, weight: .bold)).lineLimit(1)
            }
            .foregroundColor(.white)
            .frame(maxWidth: .infinity).frame(height: 110)
            .background(RoundedRectangle(cornerRadius: 18).fill(linear([Color(hex: 0x15192C), Color(hex: 0x0A0B14)])))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NeonAccent.blue.stroke, lineWidth: 1.2))
        }.buttonStyle(PlainButtonStyle())
    }

    private func infoRow(_ icon: String, _ label: String, _ value: String, link: String? = nil) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon).font(.system(size: 22)).foregroundColor(.white).frame(width: 30)
            Text(label).font(.system(size: 18, weight: .semibold)).foregroundColor(.white).frame(width: 170, alignment: .leading)
            Group {
                if let l = link, let url = URL(string: l) {
                    Button(action: { NSWorkspace.shared.open(url) }) {
                        Text(value).font(.system(size: 18, weight: .bold)).foregroundColor(Theme.blueBright)
                    }.buttonStyle(PlainButtonStyle())
                } else {
                    Text(value).font(.system(size: 18, weight: .bold)).foregroundColor(.white)
                }
            }
            .fixedLTR()
            Spacer()
        }
        .padding(.horizontal, 16).frame(height: 50)
    }

    private var whatsappCard: some View {
        VStack(spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: "phone.circle.fill").font(.system(size: 34)).foregroundColor(Theme.green)
                Text(tr("تواصل واتساب", "WhatsApp")).font(.system(size: 22, weight: .heavy)).foregroundColor(.white)
            }
            ZStack {
                RoundedRectangle(cornerRadius: 16).fill(Color.white)
                if let img = QRDecoder.generate(BrandInfo.whatsappLink, size: 360) {
                    Image(nsImage: img).interpolation(.none).resizable().aspectRatio(contentMode: .fit).padding(12)
                }
            }
            .frame(width: 210, height: 210)
            .onTapGesture { if let u = URL(string: BrandInfo.whatsappLink) { NSWorkspace.shared.open(u) } }
            if BrandInfo.whatsapp == nil {
                Text(tr("رقم واتساب غير مُعد في هذا الإصدار", "WhatsApp number not configured in this build"))
                    .font(.system(size: 11, weight: .medium)).foregroundColor(Theme.textTertiary).multilineTextAlignment(.center).frame(width: 210)
            }
        }
        .padding(16)
        .background(RoundedRectangle(cornerRadius: 20).fill(Theme.greenDeep.opacity(0.35)))
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(NeonAccent.green.stroke, lineWidth: 2))
        .shadow(color: Theme.green.opacity(0.5), radius: 10)
    }
}

/// Shows bundled third-party licence notices (VLCKit / libVLC are LGPL-2.1).
enum Licenses {
    static func show() {
        guard let url = Bundle.main.url(forResource: "ThirdPartyNotices", withExtension: "txt") else { return }
        NSWorkspace.shared.open(url)
    }
}
