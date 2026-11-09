import AppKit
import SwiftUI

struct SettingsSectionContent: View {
    @EnvironmentObject var model: AppModel
    let section: SettingsSection

    var body: some View {
        VStack(spacing: 18) {
            switch section {
            case .general:
                GeneralSettingsPanel()
                QualitySettingsPanel()
                AudioSubtitlePanel()
            case .player:
                PlayerSettingsPanel()
                LiveStreamFormatPanel()
            case .audioSubtitles:
                AudioSubtitlePanel()
                SubtitleAdvancedPanel()
            case .quality:
                QualitySettingsPanel()
                DecodingPanel()
            case .account:
                AccountPanel()
            case .appearance:
                AppearancePanel()
            case .parental:
                ParentalPanel()
                HiddenContentPanel()
            case .backup:
                BackupPanel()
            case .playlists, .about:
                EmptyView()
            }
        }
    }
}

// MARK: - Helpers

private func binding<T>(_ model: AppModel, _ keyPath: WritableKeyPath<AppSettings, T>) -> Binding<T> {
    Binding(get: { model.settings[keyPath: keyPath] }, set: { v in model.updateSettings { $0[keyPath: keyPath] = v } })
}

struct SettingsPanel<Content: View>: View {
    let systemImage: String
    let title: String
    var accent: NeonAccent = .mixed
    let content: Content

    init(systemImage: String, title: String, accent: NeonAccent = .mixed, @ViewBuilder content: () -> Content) {
        self.systemImage = systemImage
        self.title = title
        self.accent = accent
        self.content = content()
    }

    var body: some View {
        NeonPanel(accent: accent, padding: 18) {
            VStack(spacing: 12) {
                PanelHeader(systemImage: systemImage, title: title, size: 28)
                content
            }
        }
    }
}

let languageOptions: [(String, String)] = [("ar", "العربية"), ("en", "English"), ("fr", "Français"), ("tr", "Türkçe")]

// MARK: - General

struct GeneralSettingsPanel: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        SettingsPanel(systemImage: "gearshape", title: tr("عام", "General")) {
            SettingsRow(systemImage: "globe", title: tr("اللغة", "Language")) {
                DropdownButton(options: [(AppLanguage.arabic, "العربية"), (AppLanguage.english, "English")], selection: binding(model, \.language))
            }
            SettingsRow(systemImage: "paperplane", title: tr("عند تشغيل التطبيق", "On launch")) {
                DropdownButton(options: [(LaunchScreen.home, tr("الصفحة الرئيسية", "Home")), (.live, tr("القنوات المباشرة", "Live TV")),
                                         (.movies, tr("الأفلام", "Movies")), (.series, tr("المسلسلات", "Series")), (.lastScreen, tr("آخر شاشة", "Last screen"))],
                               selection: binding(model, \.launchScreen))
            }
            SettingsRow(systemImage: "play.circle", title: tr("تشغيل الحلقة التالية تلقائيًا", "Auto-play next episode")) {
                NeonToggle(isOn: binding(model, \.autoPlayNextEpisode))
            }
            SettingsRow(systemImage: "clock.arrow.circlepath", title: tr("متابعة المشاهدة من آخر نقطة", "Resume from last position")) {
                NeonToggle(isOn: binding(model, \.resumePlayback))
            }
        }
    }
}

struct QualitySettingsPanel: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        SettingsPanel(systemImage: "sparkles.rectangle.stack", title: tr("جودة العرض", "Display Quality")) {
            SettingsRow(systemImage: "square.stack.3d.up", title: tr("جودة التشغيل الافتراضية", "Default playback quality")) {
                DropdownButton(options: PlaybackQuality.allCases.map { ($0, $0.label) }, selection: binding(model, \.defaultQuality))
            }
            SettingsRow(systemImage: "wifi", title: tr("ضبط الجودة تلقائيًا حسب سرعة الإنترنت", "Adjust quality to connection speed")) {
                NeonToggle(isOn: binding(model, \.autoQuality))
            }
        }
    }
}

struct AudioSubtitlePanel: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        SettingsPanel(systemImage: "captions.bubble", title: tr("الصوت والترجمة", "Audio & Subtitles")) {
            SettingsRow(systemImage: "headphones", title: tr("اللغة الصوتية الافتراضية", "Default audio language")) {
                DropdownButton(options: languageOptions, selection: binding(model, \.preferredAudioLanguage))
            }
            SettingsRow(systemImage: "captions.bubble", title: tr("لغة الترجمة الافتراضية", "Default subtitle language")) {
                DropdownButton(options: languageOptions, selection: binding(model, \.preferredSubtitleLanguage))
            }
            SettingsRow(systemImage: "textformat.size", title: tr("حجم خط الترجمة", "Subtitle size")) {
                DropdownButton(options: SubtitleSize.allCases.map { ($0, subtitleSizeLabel($0)) }, selection: binding(model, \.subtitleSize))
            }
            SettingsRow(systemImage: "circle.lefthalf.fill", title: tr("لون الترجمة", "Subtitle colour")) {
                DropdownButton(options: SubtitleColor.allCases.map { ($0, subtitleColorLabel($0)) }, selection: binding(model, \.subtitleColor), leadingIcon: "circle.fill")
            }
        }
    }
}

struct SubtitleAdvancedPanel: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        SettingsPanel(systemImage: "slider.horizontal.3", title: tr("خيارات متقدمة للترجمة", "Advanced subtitle options")) {
            SettingsRow(systemImage: "captions.bubble.fill", title: tr("تفعيل الترجمة", "Enable subtitles")) {
                NeonToggle(isOn: binding(model, \.subtitlesEnabled))
            }
            SettingsRow(systemImage: "rectangle.fill", title: tr("خلفية خلف النص", "Background box")) {
                NeonToggle(isOn: binding(model, \.subtitleBackground))
            }
            SettingsRow(systemImage: "timer", title: tr("تأخير الترجمة (مللي ثانية)", "Subtitle delay (ms)")) {
                DropdownButton(options: [-2000, -1000, -500, -250, 0, 250, 500, 1000, 2000].map { ($0, "\($0) ms") }, selection: binding(model, \.subtitleDelayMs))
            }
            SettingsRow(systemImage: "doc.text", title: tr("ترميز ملفات الترجمة", "Subtitle file encoding")) {
                DropdownButton(options: ["UTF-8", "Windows-1256", "ISO-8859-6", "Windows-1252", "ISO-8859-1"].map { ($0, $0) }, selection: binding(model, \.subtitleEncoding))
            }
        }
    }
}

struct DecodingPanel: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        SettingsPanel(systemImage: "cpu", title: tr("فك الترميز", "Decoding")) {
            SettingsRow(systemImage: "bolt", title: tr("التسريع العتادي", "Hardware acceleration"),
                        subtitle: tr("VideoToolbox على معالجات Intel و Apple Silicon مع الرجوع التلقائي للفك البرمجي", "VideoToolbox on Intel and Apple Silicon with automatic software fallback")) {
                DropdownButton(options: [(HardwareDecoding.auto, tr("تلقائي (موصى به)", "Auto (recommended)")), (.software, tr("برمجي فقط", "Software only"))],
                               selection: binding(model, \.hardwareDecoding))
            }
            SettingsRow(systemImage: "cpu", title: tr("المعالج الحالي", "This Mac")) {
                Text("\(SystemInfo.isAppleSilicon ? "Apple Silicon" : "Intel") · \(SystemInfo.binaryArchitecture)\(SystemInfo.isTranslatedByRosetta ? " (Rosetta)" : "")")
                    .font(.system(size: 15, weight: .bold)).foregroundColor(Theme.textSecondary).fixedLTR()
            }
        }
    }
}

// MARK: - Player

struct PlayerSettingsPanel: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        SettingsPanel(systemImage: "play.circle", title: tr("إعدادات المشغل", "Player Settings")) {
            SettingsRow(systemImage: "play.rectangle", title: tr("المشغل الافتراضي", "Default player")) {
                DropdownButton(options: PreferredPlayer.allCases.map { ($0, playerLabel($0)) }, selection: binding(model, \.preferredPlayer), width: 330)
            }
            SettingsRow(systemImage: "forward.end", title: tr("التشغيل التلقائي للحلقة التالية", "Auto-play next episode")) {
                NeonToggle(isOn: binding(model, \.autoPlayNextEpisode))
            }
            SettingsRow(systemImage: "clock.arrow.circlepath", title: tr("استكمال المشاهدة من آخر نقطة", "Resume from last position")) {
                NeonToggle(isOn: binding(model, \.resumePlayback))
            }
            SettingsRow(systemImage: "info.circle", title: tr("عرض معلومات التشغيل (الجودة - السرعة - الترميز)", "Show playback info (quality - speed - codec)")) {
                NeonToggle(isOn: binding(model, \.showPlaybackInfo))
            }
        }
    }

    private func playerLabel(_ p: PreferredPlayer) -> String {
        switch p {
        case .akaaPlayer: return tr("المشغل الداخلي (Akaa Player)", "Built-in (Akaa Player)")
        case .vlcApp: return "VLC" + (ExternalPlayer.isInstalled(.vlcApp) ? "" : tr(" (غير مثبت)", " (not installed)"))
        case .iina: return "IINA" + (ExternalPlayer.isInstalled(.iina) ? "" : tr(" (غير مثبت)", " (not installed)"))
        case .quickTime: return "QuickTime Player"
        }
    }
}

/// "Live Stream Format": fixed priority MPEG-TS → HLS (not editable) + optional compatibility formats.
struct LiveStreamFormatPanel: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        NeonPanel(accent: .red, padding: 18) {
            VStack(spacing: 14) {
                HStack(spacing: 10) {
                    Image(systemName: "dot.radiowaves.left.and.right").font(.system(size: 28)).foregroundColor(Theme.redBright)
                    Text("Live Stream Format").font(.system(size: 28, weight: .heavy)).foregroundColor(.white)
                    Text(tr("(تنسيق البث المباشر)", "")).font(.system(size: 24, weight: .bold)).foregroundColor(.white)
                    Spacer()
                }
                .contentDirection()
                // Approved layout: [fixed-priority box] [MPEG-TS · first] >>> [HLS · second]
                HStack(spacing: 16) {
                    VStack(spacing: 6) {
                        Image(systemName: "dot.radiowaves.left.and.right").font(.system(size: 40)).foregroundColor(Theme.redBright).shadow(color: Theme.red, radius: 8)
                        VStack(spacing: 2) {
                            Text(tr("أولوية تشغيل البث المباشر", "Live playback priority")).font(.system(size: 15, weight: .bold)).foregroundColor(.white)
                            Text(tr("(ثابتة)", "(fixed)")).font(.system(size: 18, weight: .heavy)).foregroundColor(Theme.gold)
                        }
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 14).fill(Theme.red.opacity(0.18)))
                        .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Theme.redBright, lineWidth: 1.6))
                    }
                    priorityCard(format: LiveStreamPolicy.mandatoryOrder[0], rank: tr("الأولوية الأولى", "First priority"), accent: .red, icon: "antenna.radiowaves.left.and.right")
                    HStack(spacing: -10) {
                        ForEach(0..<3, id: \.self) { _ in Image(systemName: "chevron.right").font(.system(size: 30, weight: .heavy)) }
                    }
                    .foregroundColor(.white)
                    priorityCard(format: LiveStreamPolicy.mandatoryOrder[1], rank: tr("الأولوية الثانية", "Second priority"), accent: .blue, icon: "play.rectangle.on.rectangle")
                }
                .fixedLTR()
                HStack(spacing: 10) {
                    Image(systemName: "info.circle").font(.system(size: 22))
                    Text(tr("سيتم تشغيل القناة أولاً بتنسيق MPEG-TS، وفي حال فشل التشغيل يتم الانتقال تلقائيًا إلى HLS.",
                            "Channels always start as MPEG-TS; if playback fails Akaa Flix switches to HLS automatically."))
                        .font(.system(size: 16, weight: .bold)).lineLimit(2)
                    Spacer()
                }
                .foregroundColor(.white)
                .padding(.horizontal, 16).frame(minHeight: 50)
                .background(RoundedRectangle(cornerRadius: 14).fill(Theme.greenDeep.opacity(0.55)))
                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Theme.green.opacity(0.8), lineWidth: 1.2))
                .contentDirection()
                HStack(spacing: 12) {
                    Image(systemName: "square.stack.3d.up").font(.system(size: 22)).foregroundColor(.white)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(tr("التنسيقات المدعومة الأخرى", "Other supported formats")).font(.system(size: 16, weight: .bold)).foregroundColor(.white)
                        Text(tr("(للتوافق عند الحاجة)", "(compatibility, after MPEG-TS and HLS)")).font(.system(size: 13, weight: .semibold)).foregroundColor(Theme.textSecondary)
                    }
                    Spacer()
                    ForEach(LiveStreamPolicy.optionalCompatibilityFormats.reversed(), id: \.self) { f in
                        formatCheckbox(f)
                    }
                }
                .contentDirection()
                SettingsRow(systemImage: "clock", title: tr("مهلة إعادة الاتصال", "Reconnect timeout")) {
                    DropdownButton(options: [5, 10, 15, 20, 30].map { ($0, tr("\($0) ثوانٍ", "\($0) seconds")) }, selection: binding(model, \.reconnectTimeoutSeconds), width: 200)
                }
                SettingsRow(systemImage: "square.stack.3d.up", title: tr("حجم ذاكرة التخزين المؤقت (Buffer)", "Buffer size")) {
                    DropdownButton(options: [5, 10, 20, 30, 60].map { ($0, tr("\($0) ثانية", "\($0) seconds")) }, selection: binding(model, \.bufferSeconds), width: 200)
                }
                SettingsRow(systemImage: "square.stack.3d.up.fill", title: tr("نوع التخزين المؤقت", "Buffer type")) {
                    DropdownButton(options: [(BufferMode.auto, tr("تلقائي (موصى به)", "Auto (recommended)")), (.low, tr("زمن تأخير منخفض", "Low latency")), (.high, tr("ثبات عالٍ", "High stability"))],
                                   selection: binding(model, \.bufferMode), width: 260)
                }
            }
        }
    }

    private func priorityCard(format: LiveStreamFormat, rank: String, accent: NeonAccent, icon: String) -> some View {
        HStack(spacing: 14) {
            Image(systemName: icon).font(.system(size: 36)).foregroundColor(.white)
            VStack(spacing: 4) {
                HStack(spacing: 8) {
                    Text(rank).font(.system(size: 14, weight: .heavy)).foregroundColor(.black)
                        .padding(.horizontal, 10).padding(.vertical, 3)
                        .background(Capsule().fill(Theme.gold))
                    Image(systemName: "checkmark.square.fill").font(.system(size: 22)).foregroundColor(.white)
                }
                Text(format.displayName).font(.system(size: 30, weight: .heavy)).foregroundColor(.white)
                Text(format.fileHint).font(.system(size: 17, weight: .semibold)).foregroundColor(.white.opacity(0.9))
            }
        }
        .padding(.horizontal, 18).padding(.vertical, 12)
        .frame(minWidth: 250)
        .background(RoundedRectangle(cornerRadius: 18).fill(linear([accent.glow.opacity(0.35), Color.black.opacity(0.6)])))
        .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(accent.stroke, lineWidth: 2.2))
        .shadow(color: accent.glow.opacity(0.7), radius: 12)
        .help(tr("الأولوية ثابتة ولا يمكن تغييرها", "This priority is fixed and cannot be changed"))
    }

    private func formatCheckbox(_ f: LiveStreamFormat) -> some View {
        let on = model.settings.enabledCompatibilityFormats.contains(f)
        return Button(action: {
            model.updateSettings { s in
                if on { s.enabledCompatibilityFormats.remove(f) } else { s.enabledCompatibilityFormats.insert(f) }
            }
        }) {
            VStack(spacing: 2) {
                HStack(spacing: 6) {
                    Text(f.displayName).font(.system(size: 18, weight: .heavy))
                    Image(systemName: on ? "checkmark.square.fill" : "square").font(.system(size: 18))
                }
                Text(f.fileHint).font(.system(size: 14, weight: .semibold)).foregroundColor(Theme.textSecondary)
            }
            .foregroundColor(.white)
            .frame(width: 110, height: 64)
            .background(RoundedRectangle(cornerRadius: 14).fill(on ? Theme.blue.opacity(0.25) : Color.black.opacity(0.35)))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(on ? Theme.blueBright : Color.white.opacity(0.18), lineWidth: 1.2))
        }
        .buttonStyle(PlainButtonStyle())
        .fixedLTR()
    }
}

// MARK: - Account

struct AccountPanel: View {
    @EnvironmentObject var model: AppModel
    @State private var refreshing = false

    var body: some View {
        SettingsPanel(systemImage: "person", title: tr("الحساب", "Account")) {
            if let p = model.activePlaylist {
                let a = p.account
                SettingsRow(systemImage: "square.stack.3d.up", title: tr("القائمة النشطة", "Active playlist")) { value(p.name) }
                SettingsRow(systemImage: "link", title: tr("السيرفر", "Server")) { value(p.displayHost) }
                SettingsRow(systemImage: "person", title: tr("اسم المستخدم", "Username")) { value(p.username) }
                SettingsRow(systemImage: "checkmark.seal", title: tr("الحالة", "Status")) { value(statusText(a?.status, raw: a?.rawStatus)) }
                SettingsRow(systemImage: "calendar", title: tr("تاريخ الانتهاء", "Expires")) {
                    value(a?.expiresAt.map { TimeFormat.dayMonthYear.string(from: $0) } ?? tr("غير محدود", "Unlimited"))
                }
                if let days = a?.remainingDays() {
                    SettingsRow(systemImage: "hourglass", title: tr("الأيام المتبقية", "Days remaining")) { value("\(days)") }
                }
                SettingsRow(systemImage: "person.2", title: tr("الاتصالات", "Connections")) {
                    value("\(a?.activeConnections.map { String($0) } ?? "—") / \(a?.maxConnections.map { String($0) } ?? "—")")
                }
                SettingsRow(systemImage: "film", title: tr("التنسيقات المسموحة", "Allowed formats")) {
                    value((a?.allowedOutputFormats ?? []).map { $0.uppercased() }.joined(separator: ", ").ifEmpty("—"))
                }
                SettingsRow(systemImage: "globe", title: tr("المنطقة الزمنية للسيرفر", "Server time zone")) { value(a?.serverTimezone ?? "—") }
                if let created = a?.createdAt {
                    SettingsRow(systemImage: "calendar.badge.plus", title: tr("تاريخ الإنشاء", "Created")) { value(TimeFormat.dayMonthYear.string(from: created)) }
                }
                HStack {
                    NeonButton(title: tr("تحديث معلومات الحساب", "Refresh account"), systemImage: "arrow.triangle.2.circlepath", kind: .blue, isLoading: model.isSyncing) { model.sync() }
                    Spacer()
                }
                .contentDirection()
                Text(tr("تأتي جميع معلومات الحساب من السيرفر مباشرة. لتجديد الاشتراك تواصل مع مزود الخدمة.",
                        "All account details come from your server. Contact your provider to renew."))
                    .font(.system(size: 13, weight: .medium)).foregroundColor(Theme.textTertiary)
                    .frame(maxWidth: .infinity, alignment: .leading).contentDirection()
            } else {
                EmptyStateView(systemImage: "person.crop.circle.badge.questionmark", title: tr("لا توجد قائمة نشطة", "No active playlist"))
            }
        }
    }

    private func value(_ s: String) -> some View {
        Text(s).font(.system(size: 16, weight: .bold)).foregroundColor(Theme.textSecondary).lineLimit(1)
    }

    private func statusText(_ s: AccountStatus?, raw: String?) -> String {
        switch s {
        case .some(.active): return tr("نشط", "Active")
        case .some(.trial): return tr("تجريبي", "Trial")
        case .some(.expired): return tr("منتهي", "Expired")
        case .some(.banned): return tr("محظور", "Banned")
        case .some(.disabled): return tr("معطل", "Disabled")
        default: return raw ?? "—"
        }
    }
}

extension String {
    func ifEmpty(_ fallback: String) -> String { isEmpty ? fallback : self }
}

// MARK: - Appearance

struct AppearancePanel: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        SettingsPanel(systemImage: "paintbrush", title: tr("المظهر والواجهة", "Appearance")) {
            SettingsRow(systemImage: "globe", title: tr("لغة الواجهة", "Interface language")) {
                DropdownButton(options: [(AppLanguage.arabic, "العربية"), (AppLanguage.english, "English")], selection: binding(model, \.language))
            }
            SettingsRow(systemImage: "sparkles", title: tr("تقليل الحركة", "Reduce motion"), subtitle: tr("إيقاف التنقل التلقائي في البانر", "Stops the hero banner auto-advance")) {
                NeonToggle(isOn: binding(model, \.reduceMotion))
            }
            SettingsRow(systemImage: "photo.on.rectangle", title: tr("مسح ذاكرة الصور المؤقتة", "Clear image cache")) {
                NeonButton(title: tr("مسح", "Clear"), systemImage: "trash", kind: .dark, height: 40, fontSize: 14) {
                    ImagePipeline.shared.clear()
                    model.showToast(tr("تم مسح ذاكرة الصور", "Image cache cleared"), .success)
                }
            }
            SettingsRow(systemImage: "bell", title: tr("إشعارات المحتوى الجديد", "New content notifications")) {
                NeonToggle(isOn: binding(model, \.notifyNewContent))
            }
            SettingsRow(systemImage: "calendar.badge.exclamationmark", title: tr("تنبيه قرب انتهاء الاشتراك", "Expiry reminders")) {
                NeonToggle(isOn: binding(model, \.notifyExpiry))
            }
            SettingsRow(systemImage: "arrow.triangle.2.circlepath", title: tr("التحديث التلقائي للمحتوى", "Automatic content refresh")) {
                DropdownButton(options: [(6, tr("كل 6 ساعات", "Every 6 hours")), (12, tr("كل 12 ساعة", "Every 12 hours")), (24, tr("يوميًا", "Daily")), (72, tr("كل 3 أيام", "Every 3 days"))],
                               selection: binding(model, \.autoRefreshHours))
            }
        }
    }
}

// MARK: - Parental control

struct ParentalPanel: View {
    @EnvironmentObject var model: AppModel
    @State private var newPIN = ""
    @State private var confirmPIN = ""

    var body: some View {
        SettingsPanel(systemImage: "lock.shield", title: tr("التحكم الأبوي", "Parental Control")) {
            SettingsRow(systemImage: "key", title: model.settings.parentalPINHash == nil ? tr("تعيين رمز سري", "Set a PIN") : tr("تغيير الرمز السري", "Change PIN"),
                        subtitle: tr("4 إلى 8 أرقام، يُحفظ بشكل مشفر فقط", "4–8 digits, stored only as a secure hash")) {
                HStack(spacing: 8) {
                    pinField(tr("الرمز", "PIN"), $newPIN)
                    pinField(tr("تأكيد", "Confirm"), $confirmPIN)
                    NeonButton(title: tr("حفظ", "Save"), kind: .red, height: 40, fontSize: 14) { savePIN() }
                }
            }
            if model.settings.parentalPINHash != nil {
                SettingsRow(systemImage: "lock.open", title: tr("إزالة الرمز السري", "Remove PIN")) {
                    NeonButton(title: tr("إزالة", "Remove"), systemImage: "trash", kind: .outlineRed, height: 40, fontSize: 14) {
                        model.requestPIN(tr("أدخل الرمز الحالي", "Enter the current PIN")) {
                            model.updateSettings { $0.parentalPINHash = nil; $0.lockSettings = false }
                            model.showToast(tr("تمت إزالة الرمز السري", "PIN removed"), .success)
                        }
                    }
                }
            }
            SettingsRow(systemImage: "eye.slash", title: tr("إخفاء محتوى البالغين", "Hide adult content")) {
                NeonToggle(isOn: Binding(get: { model.settings.hideAdultContent }, set: { v in
                    let apply = { model.updateSettings { $0.hideAdultContent = v } }
                    if v { apply() } else { model.requestPIN(tr("أدخل الرمز لإظهار محتوى البالغين", "Enter the PIN to show adult content"), then: apply) }
                }))
            }
            SettingsRow(systemImage: "person.badge.clock", title: tr("الحد الأقصى للتصنيف العمري", "Maximum age rating")) {
                DropdownButton(options: [(0, tr("بدون حد", "No limit")), (7, "7+"), (13, "13+"), (16, "16+"), (18, "18+")], selection: binding(model, \.maxAgeRating))
            }
            SettingsRow(systemImage: "gearshape.2", title: tr("قفل الإعدادات بالرمز", "Lock settings with PIN")) {
                NeonToggle(isOn: binding(model, \.lockSettings))
            }
            SettingsRow(systemImage: "lock.rotation", title: tr("إزالة جميع الأقفال", "Remove all locks")) {
                NeonButton(title: tr("إزالة", "Clear"), kind: .dark, height: 40, fontSize: 14) {
                    guard let pid = model.activePlaylist?.id else { return }
                    model.requestPIN(tr("أدخل الرمز", "Enter the PIN")) {
                        for lock in (try? model.database.userData.locks(pid)) ?? [] {
                            let parts = lock.split(separator: "|", maxSplits: 1).map(String.init)
                            if parts.count == 2 { try? model.database.userData.setLocked(pid, kind: parts[0], value: parts[1], locked: false) }
                        }
                        model.showToast(tr("تمت إزالة الأقفال", "Locks removed"), .success)
                    }
                }
            }
        }
    }

    private func pinField(_ placeholder: String, _ text: Binding<String>) -> some View {
        SecureField(placeholder, text: text)
            .textFieldStyle(PlainTextFieldStyle())
            .font(.system(size: 16, weight: .bold))
            .multilineTextAlignment(.center)
            .frame(width: 90, height: 40)
            .background(RoundedRectangle(cornerRadius: 10).fill(Color.black.opacity(0.4)))
            .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(Color.white.opacity(0.2), lineWidth: 1))
    }

    private func savePIN() {
        guard PinHasher.isValidPIN(newPIN) else { model.showToast(tr("الرمز يجب أن يكون من 4 إلى 8 أرقام", "The PIN must be 4–8 digits"), .error); return }
        guard newPIN == confirmPIN else { model.showToast(tr("الرمزان غير متطابقين", "PINs do not match"), .error); return }
        let save = {
            let hash = PinHasher.hash(newPIN)
            model.updateSettings { $0.parentalPINHash = hash }
            newPIN = ""; confirmPIN = ""
            model.showToast(tr("تم حفظ الرمز السري", "PIN saved"), .success)
        }
        if model.settings.parentalPINHash != nil { model.requestPIN(tr("أدخل الرمز الحالي", "Enter the current PIN"), then: save) } else { save() }
    }
}

struct HiddenContentPanel: View {
    @EnvironmentObject var model: AppModel
    @State private var keyword = ""

    var body: some View {
        let hidden = model.hiddenContent()
        SettingsPanel(systemImage: "eye.slash", title: tr("إخفاء المحتوى", "Hidden content")) {
            SettingsRow(systemImage: "text.badge.minus", title: tr("إخفاء العناوين التي تحتوي على كلمة", "Hide titles containing a word")) {
                HStack(spacing: 8) {
                    TextField(tr("كلمة", "Keyword"), text: $keyword)
                        .textFieldStyle(PlainTextFieldStyle()).font(.system(size: 15, weight: .semibold))
                        .padding(.horizontal, 10).frame(width: 160, height: 38)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color.black.opacity(0.4)))
                    NeonButton(title: tr("إضافة", "Add"), kind: .red, height: 38, fontSize: 14) {
                        let k = keyword.trimmingCharacters(in: .whitespaces)
                        guard !k.isEmpty else { return }
                        model.setHidden(kind: "keyword", value: k, hidden: true)
                        keyword = ""
                    }
                }
            }
            ForEach(hidden.keywords, id: \.self) { k in
                SettingsRow(systemImage: "textformat", title: k) {
                    NeonButton(title: tr("إظهار", "Show"), kind: .dark, height: 36, fontSize: 13) { model.setHidden(kind: "keyword", value: k, hidden: false) }
                }
            }
            SettingsRow(systemImage: "rectangle.stack.badge.minus", title: tr("المجموعات والعناصر المخفية", "Hidden categories and items"),
                        subtitle: tr("\(hidden.categoryIDs.count) مجموعة · \(hidden.itemIDs.count) عنصر", "\(hidden.categoryIDs.count) categories · \(hidden.itemIDs.count) items")) {
                NeonButton(title: tr("استعادة الكل", "Restore all"), systemImage: "arrow.uturn.backward", kind: .blue, height: 40, fontSize: 14) {
                    if let pid = model.activePlaylist?.id { try? model.database.userData.clearHidden(pid) }
                    model.setHidden(kind: "noop", value: "noop", hidden: false)
                    model.showToast(tr("تمت استعادة المحتوى المخفي", "Hidden content restored"), .success)
                }
            }
        }
    }
}

// MARK: - Backup

struct BackupPanel: View {
    @EnvironmentObject var model: AppModel
    @State private var includeCredentials = false
    @State private var restoreSettings = true

    var body: some View {
        SettingsPanel(systemImage: "icloud.and.arrow.up", title: tr("النسخ الاحتياطي", "Backup")) {
            SettingsRow(systemImage: "key.fill", title: tr("تضمين كلمات المرور في النسخة", "Include passwords in the backup"),
                        subtitle: tr("احفظ الملف في مكان آمن إذا قمت بتضمين كلمات المرور", "Keep the file safe if passwords are included")) {
                NeonToggle(isOn: $includeCredentials)
            }
            SettingsRow(systemImage: "square.and.arrow.up", title: tr("إنشاء نسخة احتياطية", "Create backup")) {
                NeonButton(title: tr("نسخ احتياطي", "Back up"), systemImage: "icloud.and.arrow.up", kind: .blue, height: 42, fontSize: 15) { BackupUI.export(model: model, includeCredentials: includeCredentials) }
            }
            SettingsRow(systemImage: "gearshape", title: tr("استعادة الإعدادات أيضًا", "Also restore settings")) {
                NeonToggle(isOn: $restoreSettings)
            }
            SettingsRow(systemImage: "square.and.arrow.down", title: tr("استعادة من ملف", "Restore from file")) {
                NeonButton(title: tr("استعادة", "Restore"), systemImage: "icloud.and.arrow.down", kind: .blue, height: 42, fontSize: 15) { BackupUI.restore(model: model, restoreSettings: restoreSettings) }
            }
        }
    }
}

enum BackupUI {
    static func export(model: AppModel, includeCredentials: Bool) {
        let panel = NSSavePanel()
        panel.allowedFileTypes = ["json"]
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        panel.nameFieldStringValue = "AkaaFlix-backup-\(f.string(from: Date())).json"
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            let data = try BackupManager.export(database: model.database, secrets: model.secrets, settings: model.settings, includeCredentials: includeCredentials)
            try data.write(to: url, options: .atomic)
            model.showToast(tr("تم حفظ النسخة الاحتياطية", "Backup saved"), .success)
        } catch { model.showError(error) }
    }

    static func restore(model: AppModel, restoreSettings: Bool) {
        let panel = NSOpenPanel()
        panel.allowedFileTypes = ["json"]
        panel.allowsMultipleSelection = false
        guard panel.runModal() == .OK, let url = panel.url else { return }
        do {
            let data = try Data(contentsOf: url)
            let result = try BackupManager.restore(data, database: model.database, secrets: model.secrets, currentSettings: model.settings, restoreSettings: restoreSettings)
            if restoreSettings { model.updateSettings { $0 = result.settings } }
            model.reloadPlaylists()
            model.showToast(tr("تمت الاستعادة: \(result.playlistsAdded) جديدة، \(result.playlistsMerged) مدمجة", "Restored: \(result.playlistsAdded) new, \(result.playlistsMerged) merged"), .success)
        } catch { model.showError(error) }
    }
}
