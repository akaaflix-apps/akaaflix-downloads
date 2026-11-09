import SwiftUI

/// Settings layout: section list (left), artwork panel (centre) and the section content (right).
struct SettingsShell: View {
    @EnvironmentObject var model: AppModel
    let section: SettingsSection
    var formPlaylistID: Int64?
    var showForm = false

    var body: some View {
        HStack(alignment: .top, spacing: 18) {
            SettingsNav(selected: section)
                .frame(width: 340)
            Group {
                if showForm {
                    PlaylistFormView(playlistID: formPlaylistID)
                } else {
                    switch section {
                    case .playlists:
                        PlaylistsManagementView()
                    case .about:
                        HStack(alignment: .top, spacing: 18) {
                            AboutHeroPanel().frame(width: 520)
                            AboutView()
                        }
                    default:
                        HStack(alignment: .top, spacing: 18) {
                            SettingsHeroPanel(section: section).frame(width: 440)
                            ScrollView(.vertical, showsIndicators: false) {
                                SettingsSectionContent(section: section)
                                    .padding(.bottom, 20)
                            }
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 20)
    }
}

struct SettingsNav: View {
    @EnvironmentObject var model: AppModel
    let selected: SettingsSection

    var body: some View {
        VStack(spacing: 12) {
            ForEach(SettingsSection.allCases, id: \.self) { s in
                Button(action: { model.open(.settings(s)) }) {
                    HStack(spacing: 14) {
                        Image(systemName: s.systemImage).font(.system(size: 28, weight: .regular)).frame(width: 40)
                        Text(s.title).font(.system(size: 21, weight: .heavy)).lineLimit(1).minimumScaleFactor(0.7)
                        Spacer()
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 22)
                    .frame(height: 70)
                    .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(s == selected ? Theme.redGradient : Theme.darkPill))
                    .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(s == selected ? Color(hex: 0xFF8080) : Color.white.opacity(0.12), lineWidth: s == selected ? 2 : 1))
                    .shadow(color: s == selected ? Theme.red.opacity(0.8) : .black.opacity(0.4), radius: s == selected ? 14 : 4)
                    .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 24).fill(Color.black.opacity(0.35)))
        .contentDirection()
    }
}

/// Centre artwork panel ("الإعدادات — تحكم كامل في تجربة المشاهدة").
struct SettingsHeroPanel: View {
    let section: SettingsSection

    private var art: String { section == .player ? "PlayerHeroArt" : "SettingsHeroArt" }
    private var title: String { section == .general ? tr("الإعدادات", "Settings") : section.title }
    private var subtitle: String {
        switch section {
        case .general: return tr("تحكم كامل في تجربة المشاهدة", "Full control of your viewing experience")
        case .player: return tr("إعدادات تشغيل المحتوى والبث المباشر", "Playback and live streaming settings")
        case .account: return tr("معلومات الاشتراك من السيرفر", "Subscription details from your server")
        case .audioSubtitles: return tr("اللغة الصوتية ومظهر الترجمة", "Audio language and subtitle style")
        case .quality: return tr("جودة التشغيل الافتراضية", "Default playback quality")
        case .appearance: return tr("خيارات الواجهة", "Interface options")
        case .parental: return tr("حماية المحتوى بالرمز السري", "Protect content with a PIN")
        case .backup: return tr("حفظ واستعادة إعداداتك", "Save and restore your setup")
        default: return ""
        }
    }

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 24, style: .continuous).fill(linear([Color(hex: 0x0A0B16), Color(hex: 0x05060C)]))
            radial([Theme.red.opacity(0.45), .clear], center: UnitPoint(x: 0.2, y: 0.45), end: 260)
            radial([Theme.blue.opacity(0.4), .clear], center: UnitPoint(x: 0.9, y: 0.35), end: 240)
            VStack(spacing: 18) {
                Spacer()
                Image(art).resizable().aspectRatio(contentMode: .fit).frame(maxWidth: 400)
                Text(title).font(.system(size: 50, weight: .heavy)).foregroundColor(.white).shadow(color: .black, radius: 6)
                Text(subtitle).font(.system(size: 21, weight: .semibold)).foregroundColor(.white.opacity(0.92)).multilineTextAlignment(.center)
                Capsule().fill(linear([.clear, Theme.redBright, .clear], .leading, .trailing)).frame(width: 220, height: 3).shadow(color: Theme.red, radius: 6)
                Spacer()
                WaveLines().frame(height: 90).opacity(0.8)
            }
            .padding(.horizontal, 18)
        }
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(Theme.neonStrokeVertical, lineWidth: 1.4))
    }
}
