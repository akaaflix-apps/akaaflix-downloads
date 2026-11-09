import SwiftUI

/// Full-window player. Layout follows the approved Live, Movie and Series player designs.
struct PlayerScreen: View {
    @EnvironmentObject var model: AppModel
    @ObservedObject var controller: PlayerController
    @State private var keyMonitor = PlayerKeyMonitor()

    var body: some View {
        ZStack {
            Color.black
            VideoSurface(engine: controller.engine)
            MouseActivityView(onMove: { controller.pokeControls() },
                              onDoubleClick: { controller.toggleFullScreen() },
                              onClick: { if controller.openMenu != nil { controller.openMenu = nil } })
            statusOverlay
            if controller.isLocked {
                lockedOverlay
            } else if controller.controlsVisible || !controller.isPlaying {
                Group {
                    if controller.isMini {
                        MiniPlayerControls(controller: controller)
                    } else if controller.request.isLive {
                        LivePlayerOverlay(controller: controller)
                    } else {
                        VODPlayerOverlay(controller: controller)
                    }
                }
                .transition(.opacity)
            }
            if let n = controller.nextEpisodeCountdown, let next = controller.nextEpisode {
                NextEpisodeCard(seconds: n, episode: next, onPlay: { controller.cancelAutoNext(); controller.playNext() }, onCancel: { controller.cancelAutoNext() })
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
                    .padding(.trailing, 40).padding(.bottom, 150)
            }
        }
        .fixedLTR()
        .onAppear { installKeys() }
        .onDisappear { keyMonitor.remove() }
        .onExitCommand {
            if controller.isFullScreen { controller.toggleFullScreen() } else { model.closePlayer() }
        }
    }

    private func installKeys() {
        keyMonitor.install { event in
            let step = Double(model.settings.seekStepSeconds)
            switch event.keyCode {
            case 49: controller.togglePlay(); return true                        // space
            case 123: if controller.request.isLive { controller.switchChannel(by: -1) } else { controller.skip(-step) }; return true
            case 124: if controller.request.isLive { controller.switchChannel(by: 1) } else { controller.skip(step) }; return true
            case 126: controller.setVolume(controller.volume + 0.05); controller.pokeControls(); return true
            case 125: controller.setVolume(controller.volume - 0.05); controller.pokeControls(); return true
            case 3: controller.toggleFullScreen(); return true                   // F
            case 46: controller.toggleMute(); controller.pokeControls(); return true // M
            case 45: controller.playNext(); return true                          // N
            default: return false
            }
        }
    }

    @ViewBuilder private var statusOverlay: some View {
        switch controller.phase {
        case .opening, .reconnecting:
            VStack(spacing: 14) {
                ProgressView().scaleEffect(1.4)
                Text(controller.phase == .reconnecting ? tr("جارٍ إعادة الاتصال…", "Reconnecting…") : tr("جارٍ التحميل…", "Loading…"))
                    .font(.system(size: 17, weight: .bold)).foregroundColor(.white)
                if controller.request.isLive {
                    Text(controller.attemptTrail.isEmpty ? "MPEG-TS → HLS" : controller.attemptTrail)
                        .font(.system(size: 13, weight: .semibold)).foregroundColor(Theme.textSecondary)
                }
            }
            .padding(26)
            .background(RoundedRectangle(cornerRadius: 18).fill(Color.black.opacity(0.55)))
        case .buffering:
            ProgressView().scaleEffect(1.2)
        case .failed(let message):
            VStack(spacing: 16) {
                Image(systemName: "exclamationmark.triangle.fill").font(.system(size: 44)).foregroundColor(Theme.redBright)
                Text(message).font(.system(size: 18, weight: .bold)).foregroundColor(.white).multilineTextAlignment(.center)
                if !controller.attemptTrail.isEmpty {
                    Text(controller.attemptTrail).font(.system(size: 13, weight: .semibold)).foregroundColor(Theme.textSecondary)
                }
                HStack(spacing: 14) {
                    NeonButton(title: tr("إعادة المحاولة", "Retry"), systemImage: "arrow.clockwise", kind: .red) { controller.retry() }
                    NeonButton(title: tr("إغلاق", "Close"), systemImage: "xmark", kind: .dark) { model.closePlayer() }
                }
            }
            .padding(30)
            .background(RoundedRectangle(cornerRadius: 22).fill(Color.black.opacity(0.75)))
            .contentDirection()
        default:
            EmptyView()
        }
    }

    private var lockedOverlay: some View {
        VStack {
            Spacer()
            HStack {
                PlayerSideButton(systemImage: "lock.fill", active: true) { controller.isLocked = false; controller.pokeControls() }
                    .padding(.leading, 40)
                Spacer()
            }
            Spacer()
        }
    }
}

// MARK: - Shared pieces

struct PlayerSideButton: View {
    let systemImage: String
    var active = false
    var size: CGFloat = 64
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: size * 0.38, weight: .medium))
                .foregroundColor(.white)
                .frame(width: size, height: size)
                .background(Circle().fill(active ? Theme.redGradient : linear([Color(hex: 0x2A2C36).opacity(0.9), Color.black.opacity(0.85)])))
                .overlay(Circle().strokeBorder(Color.white.opacity(0.25), lineWidth: 1.2))
                .shadow(color: active ? Theme.red : .black.opacity(0.5), radius: 8)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct PlayerTopButton: View {
    let systemImage: String
    var active = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.system(size: 24, weight: .regular))
                .foregroundColor(.white)
                .frame(width: 70, height: 56)
                .background(RoundedRectangle(cornerRadius: 16).fill(active ? Theme.redGradient : linear([Color(hex: 0x26282F).opacity(0.92), Color(hex: 0x0C0D12).opacity(0.92)])))
                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.white.opacity(0.22), lineWidth: 1.2))
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct TransportControls: View {
    @ObservedObject var controller: PlayerController

    var body: some View {
        HStack(spacing: 44) {
            transportButton("backward.end.fill", 34) { controller.playPrevious() }
            transportButton("gobackward.10", 38) { controller.skip(-Double(10)) }
            Button(action: { controller.togglePlay() }) {
                Image(systemName: controller.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 44, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 120, height: 120)
                    .background(Circle().fill(Color.black.opacity(0.55)))
                    .overlay(Circle().strokeBorder(Theme.redBright, lineWidth: 4))
                    .shadow(color: Theme.red, radius: 18)
            }
            .buttonStyle(PlainButtonStyle())
            transportButton("goforward.10", 38) { controller.skip(Double(10)) }
            transportButton("forward.end.fill", 34) { controller.playNext() }
        }
    }

    private func transportButton(_ icon: String, _ size: CGFloat, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: size, weight: .semibold)).foregroundColor(.white)
                .frame(width: 64, height: 64).contentShape(Rectangle())
                .shadow(color: .black, radius: 4)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct TimelineBar: View {
    @ObservedObject var controller: PlayerController

    var body: some View {
        HStack(spacing: 24) {
            Text(TimeFormat.longClock(controller.isScrubbing ? controller.scrubFraction * controller.duration : controller.currentTime))
                .font(.system(size: 26, weight: .semibold)).monoDigits().foregroundColor(.white)
            NeonSlider(value: Binding(
                get: { controller.isScrubbing ? controller.scrubFraction : (controller.duration > 0 ? controller.currentTime / controller.duration : 0) },
                set: { controller.scrubFraction = $0 }),
                height: 5, knob: 26,
                onEditingChanged: { editing in
                    if editing {
                        controller.scrubFraction = controller.duration > 0 ? controller.currentTime / controller.duration : 0
                        controller.isScrubbing = true
                    } else {
                        controller.commitScrub()
                    }
                })
            Text(TimeFormat.longClock(controller.duration))
                .font(.system(size: 26, weight: .semibold)).monoDigits().foregroundColor(.white)
        }
    }
}

struct VolumePill: View {
    @ObservedObject var controller: PlayerController
    var width: CGFloat = 340

    var body: some View {
        HStack(spacing: 14) {
            Button(action: { controller.toggleMute() }) {
                Image(systemName: controller.isMuted || controller.volume == 0 ? "speaker.slash" : "speaker.wave.2")
                    .font(.system(size: 26)).foregroundColor(.white)
            }.buttonStyle(PlainButtonStyle())
            NeonSlider(value: Binding(get: { controller.isMuted ? 0 : controller.volume }, set: { controller.setVolume($0) }), height: 5, knob: 20)
        }
        .padding(.horizontal, 22)
        .frame(width: width, height: 64)
        .background(RoundedRectangle(cornerRadius: 18).fill(linear([Color(hex: 0x26282F).opacity(0.9), Color(hex: 0x0C0D12).opacity(0.9)])))
        .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(Color.white.opacity(0.22), lineWidth: 1.2))
    }
}

struct PlayerHeaderTitle: View {
    let title: String
    let subtitle: String?
    var body: some View {
        VStack(spacing: 4) {
            Text(title).font(.system(size: 28, weight: .heavy)).foregroundColor(.white).lineLimit(1)
            if let s = subtitle { Text(s).font(.system(size: 21, weight: .semibold)).foregroundColor(.white.opacity(0.92)).lineLimit(1) }
        }
        .shadow(color: .black, radius: 6)
    }
}

/// Quality popover ("الجودة": 4K ULTRA HD, 1080p FHD, 720p HD, 480p, 360p).
struct QualityMenu: View {
    @ObservedObject var controller: PlayerController

    var body: some View {
        VStack(spacing: 8) {
            HStack {
                Button(action: { controller.openMenu = nil }) { Image(systemName: "chevron.up").font(.system(size: 18, weight: .bold)) }.buttonStyle(PlainButtonStyle())
                Spacer()
                Text(tr("الجودة", "Quality")).font(.system(size: 20, weight: .heavy))
            }
            .foregroundColor(.white)
            .padding(.horizontal, 6)
            let adjustable = controller.qualityIsAdjustable
            let selected = adjustable ? controller.selectedQuality : (controller.detectedQuality ?? controller.selectedQuality)
            ForEach(PlaybackQuality.allCases, id: \.self) { q in
                Button(action: { if adjustable { controller.setQuality(q) } }) {
                    HStack(spacing: 6) {
                        Text(q.label).font(.system(size: 18, weight: .bold))
                        if q == selected { Image(systemName: "checkmark").font(.system(size: 16, weight: .heavy)) }
                    }
                    .foregroundColor(.white.opacity(adjustable || q == selected ? 1 : 0.4))
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(RoundedRectangle(cornerRadius: 12).fill(q == selected ? Theme.redGradient : Theme.darkPill))
                    .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(q == selected ? Theme.redBright : Color.white.opacity(0.15), lineWidth: 1.2))
                    .shadow(color: q == selected ? Theme.red.opacity(0.7) : .clear, radius: 8)
                }
                .buttonStyle(PlainButtonStyle())
            }
            if !adjustable {
                Text(tr("هذا الملف متوفر بجودة واحدة", "This file has a single quality"))
                    .font(.system(size: 11, weight: .medium)).foregroundColor(Theme.textTertiary)
            }
        }
        .padding(14)
        .frame(width: 220)
        .background(RoundedRectangle(cornerRadius: 20).fill(Color(hex: 0x0D0E14).opacity(0.94)))
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(Color.white.opacity(0.15), lineWidth: 1))
    }
}

/// Generic option list popover for subtitles / audio / settings.
struct PlayerOptionsMenu: View {
    @EnvironmentObject var model: AppModel
    @ObservedObject var controller: PlayerController
    let kind: PlayerController.PlayerMenu

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title).font(.system(size: 19, weight: .heavy)).foregroundColor(.white)
            ScrollView {
                VStack(spacing: 6) { content }
            }
            .frame(maxHeight: 360)
        }
        .padding(14)
        .frame(width: 300)
        .background(RoundedRectangle(cornerRadius: 20).fill(Color(hex: 0x0D0E14).opacity(0.95)))
        .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(Color.white.opacity(0.15), lineWidth: 1))
        .contentDirection()
    }

    private var title: String {
        switch kind {
        case .subtitles: return tr("الترجمة", "Subtitles")
        case .audio: return tr("المسار الصوتي", "Audio track")
        case .subtitleStyle: return tr("مظهر الترجمة", "Subtitle appearance")
        case .settings: return tr("إعدادات التشغيل", "Playback settings")
        case .channels: return tr("القنوات", "Channels")
        case .quality: return tr("الجودة", "Quality")
        }
    }

    @ViewBuilder private var content: some View {
        switch kind {
        case .subtitles:
            option(tr("إيقاف", "Off"), selected: controller.selectedSubtitle < 0) { controller.selectSubtitle(-1) }
            ForEach(controller.subtitleTracks) { t in
                option(t.name, selected: t.id == controller.selectedSubtitle) { controller.selectSubtitle(t.id) }
            }
            if controller.engine.supportsExternalSubtitles {
                option(tr("تحميل ملف ترجمة…", "Load subtitle file…"), icon: "doc.badge.plus", selected: false) { controller.loadSubtitleFile() }
            }
            HStack {
                Text(tr("التزامن", "Sync")).font(.system(size: 14, weight: .bold)).foregroundColor(.white)
                Spacer()
                smallButton("minus") { controller.adjustSubtitleDelay(-250) }
                Text("\(controller.subtitleDelayMs) ms").font(.system(size: 13, weight: .bold)).monoDigits().foregroundColor(.white).frame(width: 80)
                smallButton("plus") { controller.adjustSubtitleDelay(250) }
            }
            .fixedLTR()
            .padding(.top, 4)
        case .audio:
            if controller.audioTracks.isEmpty {
                Text(tr("لا توجد مسارات صوتية إضافية", "No additional audio tracks")).font(.system(size: 13)).foregroundColor(Theme.textSecondary)
            }
            ForEach(controller.audioTracks) { t in
                option(t.name, selected: t.id == controller.selectedAudio) { controller.selectAudio(t.id) }
            }
        case .subtitleStyle:
            ForEach(SubtitleSize.allCases, id: \.self) { s in
                option(subtitleSizeLabel(s), selected: model.settings.subtitleSize == s) { model.updateSettings { $0.subtitleSize = s } }
            }
            ForEach(SubtitleColor.allCases, id: \.self) { c in
                option(subtitleColorLabel(c), selected: model.settings.subtitleColor == c) { model.updateSettings { $0.subtitleColor = c } }
            }
            Text(tr("يطبق المظهر الجديد عند بدء التشغيل التالي", "The new style applies from the next playback"))
                .font(.system(size: 11)).foregroundColor(Theme.textTertiary)
        case .settings:
            Text(tr("السرعة", "Speed")).font(.system(size: 13, weight: .bold)).foregroundColor(Theme.textSecondary)
            HStack(spacing: 6) {
                ForEach([Float(0.5), 0.75, 1, 1.25, 1.5, 2], id: \.self) { r in
                    Button(action: { controller.setSpeed(r) }) {
                        Text(r == 1 ? "1×" : String(format: "%g×", r)).font(.system(size: 12, weight: .bold)).foregroundColor(.white)
                            .frame(width: 40, height: 30)
                            .background(RoundedRectangle(cornerRadius: 8).fill(controller.speed == r ? Theme.redGradient : Theme.darkPill))
                    }.buttonStyle(PlainButtonStyle())
                }
            }.fixedLTR()
            Text(tr("أبعاد الصورة", "Aspect ratio")).font(.system(size: 13, weight: .bold)).foregroundColor(Theme.textSecondary)
            ForEach(AspectMode.allCases, id: \.self) { a in
                option(a.label, selected: controller.aspect == a) { controller.setAspect(a) }
            }
            option(tr("عرض معلومات التشغيل", "Show playback info"), icon: "info.circle", selected: model.settings.showPlaybackInfo) {
                model.updateSettings { $0.showPlaybackInfo.toggle() }
            }
        case .channels, .quality:
            EmptyView()
        }
    }

    private func option(_ title: String, icon: String? = nil, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let i = icon { Image(systemName: i) }
                Text(title).font(.system(size: 15, weight: .semibold)).lineLimit(1)
                Spacer()
                if selected { Image(systemName: "checkmark").foregroundColor(Theme.redBright) }
            }
            .foregroundColor(.white)
            .padding(.horizontal, 12)
            .frame(height: 38)
            .background(RoundedRectangle(cornerRadius: 10).fill(selected ? Theme.red.opacity(0.3) : Color.white.opacity(0.05)))
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }

    private func smallButton(_ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 12, weight: .bold)).foregroundColor(.white)
                .frame(width: 30, height: 28).background(RoundedRectangle(cornerRadius: 7).fill(Theme.darkPill))
        }.buttonStyle(PlainButtonStyle())
    }
}

func subtitleSizeLabel(_ s: SubtitleSize) -> String {
    switch s {
    case .small: return tr("صغير", "Small")
    case .medium: return tr("متوسط", "Medium")
    case .large: return tr("كبير", "Large")
    case .extraLarge: return tr("كبير جداً", "Extra large")
    }
}

func subtitleColorLabel(_ c: SubtitleColor) -> String {
    switch c {
    case .white: return tr("أبيض", "White")
    case .yellow: return tr("أصفر", "Yellow")
    case .green: return tr("أخضر", "Green")
    case .cyan: return tr("سماوي", "Cyan")
    }
}

// MARK: - Movie & series player

struct VODPlayerOverlay: View {
    @EnvironmentObject var model: AppModel
    @ObservedObject var controller: PlayerController
    @State private var showInfo = false

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                linear([Color.black.opacity(0.85), Color.black.opacity(0)]).frame(height: 170)
                Spacer()
                linear([Color.black.opacity(0), Color.black.opacity(0.9)]).frame(height: 330)
            }
            .allowsHitTesting(false)

            VStack(spacing: 0) {
                header
                if model.settings.showPlaybackInfo {
                    Text(controller.playbackInfo)
                        .font(.system(size: 12, weight: .semibold)).foregroundColor(.white.opacity(0.75))
                        .padding(.horizontal, 12).padding(.vertical, 5)
                        .background(Capsule().fill(Color.black.opacity(0.5)))
                        .padding(.top, 6)
                }
                Spacer()
                TransportControls(controller: controller).padding(.bottom, 28)
                TimelineBar(controller: controller).padding(.horizontal, 44).padding(.bottom, 22)
                bottomRow.padding(.horizontal, 40).padding(.bottom, 26)
            }

            // Left vertical buttons: lock, fullscreen, snapshot
            VStack(spacing: 22) {
                PlayerSideButton(systemImage: "lock") { controller.isLocked = true; controller.openMenu = nil }
                PlayerSideButton(systemImage: "arrow.up.left.and.arrow.down.right") { controller.toggleFullScreen() }
                PlayerSideButton(systemImage: "camera") { controller.takeSnapshot() }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .padding(.leading, 40)
            .offset(y: -60)

            // Right side menus / episode list
            Group {
                if controller.openMenu == .quality {
                    QualityMenu(controller: controller)
                } else if let menu = controller.openMenu, menu != .channels {
                    PlayerOptionsMenu(controller: controller, kind: menu)
                } else if controller.request.isEpisode && controller.showEpisodes {
                    EpisodeListPanel(controller: controller)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
            .padding(.top, controller.request.isEpisode ? 100 : 170)
            .padding(.trailing, 24)
        }
        .sheet(isPresented: $showInfo) { PlaybackInfoSheet(request: controller.request, onClose: { showInfo = false }) }
    }

    private var header: some View {
        ZStack {
            PlayerHeaderTitle(title: headerTitle, subtitle: headerSubtitle)
                .frame(maxWidth: 700)
            HStack(spacing: 16) {
                Button(action: { model.closePlayer() }) {
                    Image(systemName: "chevron.left").font(.system(size: 22, weight: .bold)).foregroundColor(.white)
                        .frame(width: 54, height: 54)
                        .background(Circle().fill(Color(hex: 0x1A1B22).opacity(0.9)))
                        .overlay(Circle().strokeBorder(Color.white.opacity(0.25), lineWidth: 1.2))
                }.buttonStyle(PlainButtonStyle())
                LogoView(height: 46)
                Spacer()
                RoutePickerButton()
                    .frame(width: 70, height: 56)
                    .background(RoundedRectangle(cornerRadius: 16).fill(linear([Color(hex: 0x26282F).opacity(0.92), Color(hex: 0x0C0D12).opacity(0.92)])))
                    .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.white.opacity(0.22), lineWidth: 1.2))
                    .help("AirPlay")
                PlayerTopButton(systemImage: "captions.bubble", active: controller.openMenu == .subtitles) { toggle(.subtitles) }
                PlayerTopButton(systemImage: "textformat.size", active: controller.openMenu == .subtitleStyle) { toggle(.subtitleStyle) }
                PlayerTopButton(systemImage: "headphones", active: controller.openMenu == .audio) { toggle(.audio) }
                PlayerTopButton(systemImage: "gearshape", active: controller.openMenu == .quality || controller.openMenu == .settings) {
                    toggle(controller.openMenu == .quality ? .settings : .quality)
                }
            }
        }
        .padding(.horizontal, 32)
        .padding(.top, 18)
    }

    private var headerTitle: String {
        if case .episode(let e, let s, _, _) = controller.request {
            return ContentText.displayTitles(s.name).primary + tr(" - الموسم \(e.season)", " - Season \(e.season)")
        }
        return controller.request.title
    }

    private var headerSubtitle: String? {
        if case .episode(let e, _, _, _) = controller.request {
            return tr("الحلقة \(e.episodeNumber) : \(e.title)", "Episode \(e.episodeNumber): \(e.title)")
        }
        return controller.request.subtitle
    }

    private func toggle(_ m: PlayerController.PlayerMenu) {
        controller.showEpisodes = false
        controller.openMenu = controller.openMenu == m ? nil : m
        controller.pokeControls()
    }

    @ViewBuilder private var bottomRow: some View {
        if controller.request.isEpisode {
            HStack(spacing: 18) {
                BottomPillButton(title: tr("الحلقات", "Episodes"), systemImage: "list.bullet", active: controller.showEpisodes) {
                    controller.openMenu = nil; controller.showEpisodes.toggle(); controller.pokeControls()
                }
                BottomPillButton(title: tr("المواسم", "Seasons"), systemImage: "square.stack.3d.up", active: false) {
                    controller.openMenu = nil; controller.showEpisodes = true; controller.pokeControls()
                }
                HStack(spacing: 14) {
                    NeonToggle(isOn: Binding(get: { model.settings.autoPlayNextEpisode }, set: { v in model.updateSettings { $0.autoPlayNextEpisode = v } }), width: 58)
                    Text(tr("تشغيل تلقائي للحلقة التالية", "Auto-play next episode")).font(.system(size: 18, weight: .bold)).foregroundColor(.white).lineLimit(1)
                }
                .padding(.horizontal, 20).frame(height: 64)
                .background(RoundedRectangle(cornerRadius: 18).fill(linear([Color(hex: 0x26282F).opacity(0.9), Color(hex: 0x0C0D12).opacity(0.9)])))
                .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(Color.white.opacity(0.22), lineWidth: 1.2))
                Spacer(minLength: 8)
                Button(action: { toggle(.quality) }) {
                    HStack(spacing: 14) {
                        Image(systemName: "display").font(.system(size: 24))
                        Text(tr("جودة الفيديو", "Video quality")).font(.system(size: 18, weight: .bold))
                        QualityBadge(quality: qualityBadgeText, large: true).scaleEffect(0.8)
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 20).frame(height: 64)
                    .background(RoundedRectangle(cornerRadius: 18).fill(linear([Color(hex: 0x26282F).opacity(0.9), Color(hex: 0x0C0D12).opacity(0.9)])))
                    .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(Color.white.opacity(0.22), lineWidth: 1.2))
                }.buttonStyle(PlainButtonStyle())
                VolumePill(controller: controller, width: 300)
                fullscreenButton
            }
        } else {
            HStack(spacing: 18) {
                BottomPillButton(title: tr("معلومات الفيلم", "Movie info"), systemImage: "film", active: false) { showInfo = true }
                Spacer()
                VolumePill(controller: controller)
                fullscreenButton
            }
        }
    }

    private var qualityBadgeText: String {
        switch controller.qualityIsAdjustable ? controller.selectedQuality : (controller.detectedQuality ?? controller.selectedQuality) {
        case .uhd4K: return "4K"
        case .fhd1080: return "FHD"
        case .hd720: return "HD"
        case .sd480: return "SD"
        case .low360: return "SD"
        }
    }

    private var fullscreenButton: some View {
        Button(action: { controller.toggleFullScreen() }) {
            Image(systemName: "arrow.up.left.and.arrow.down.right").font(.system(size: 26, weight: .medium)).foregroundColor(.white)
                .frame(width: 80, height: 64)
                .background(RoundedRectangle(cornerRadius: 18).fill(linear([Color(hex: 0x26282F).opacity(0.9), Color(hex: 0x0C0D12).opacity(0.9)])))
                .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(Color.white.opacity(0.22), lineWidth: 1.2))
        }.buttonStyle(PlainButtonStyle())
    }
}

struct BottomPillButton: View {
    let title: String
    let systemImage: String
    var active = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: systemImage).font(.system(size: 24))
                Text(title).font(.system(size: 19, weight: .bold)).lineLimit(1)
            }
            .foregroundColor(.white)
            .padding(.horizontal, 26)
            .frame(height: 64)
            .background(RoundedRectangle(cornerRadius: 18).fill(active ? Theme.redGradient : linear([Color(hex: 0x26282F).opacity(0.9), Color(hex: 0x0C0D12).opacity(0.9)])))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(Color.white.opacity(0.22), lineWidth: 1.2))
        }
        .buttonStyle(PlainButtonStyle())
    }
}

/// "قائمة الحلقات" panel of the series player.
struct EpisodeListPanel: View {
    @ObservedObject var controller: PlayerController
    @State private var season: Int?

    var body: some View {
        let details = controller.seriesDetails
        let seasons = details?.seasons.map { $0.number } ?? []
        let currentSeason = season ?? controller.currentEpisode?.season ?? seasons.first ?? 1
        VStack(spacing: 12) {
            HStack {
                Button(action: { controller.showEpisodes = false }) { Image(systemName: "chevron.down").font(.system(size: 20, weight: .bold)) }.buttonStyle(PlainButtonStyle())
                Spacer()
                Text(tr("قائمة الحلقات", "Episodes")).font(.system(size: 22, weight: .heavy))
                Image(systemName: "list.bullet").font(.system(size: 22))
            }
            .foregroundColor(.white)
            HStack {
                Button(action: { step(seasons, currentSeason, -1) }) { Image(systemName: "chevron.left") }.buttonStyle(PlainButtonStyle())
                Spacer()
                Text(tr("الموسم \(currentSeason)", "Season \(currentSeason)")).font(.system(size: 20, weight: .bold))
                Spacer()
                Button(action: { step(seasons, currentSeason, 1) }) { Image(systemName: "chevron.right") }.buttonStyle(PlainButtonStyle())
            }
            .font(.system(size: 20, weight: .bold))
            .foregroundColor(.white)
            .padding(.horizontal, 18).frame(height: 50)
            .background(Capsule().fill(Theme.darkPill))
            .overlay(Capsule().strokeBorder(Color.white.opacity(0.2), lineWidth: 1))
            ScrollView {
                VStack(spacing: 8) {
                    ForEach(details?.episodesBySeason[currentSeason] ?? []) { ep in
                        let selected = ep.id == controller.currentEpisode?.id
                        Button(action: { controller.play(episode: ep) }) {
                            HStack(spacing: 12) {
                                Image(systemName: "play.fill").font(.system(size: 14)).foregroundColor(.white)
                                    .frame(width: 34, height: 34).overlay(Circle().strokeBorder(Color.white, lineWidth: 1.5))
                                Spacer()
                                Text(tr("الحلقة \(ep.episodeNumber)", "Episode \(ep.episodeNumber)")).font(.system(size: 18, weight: .bold)).foregroundColor(.white)
                                RemoteImage(url: ep.thumbnail, placeholder: .episode, maxPixel: 240)
                                    .frame(width: 96, height: 54).clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                            .padding(.horizontal, 10).frame(height: 66)
                            .background(RoundedRectangle(cornerRadius: 14).fill(selected ? linear([Theme.red.opacity(0.8), Theme.redDeep]) : Theme.darkPill))
                            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(selected ? Theme.redBright : Color.white.opacity(0.1), lineWidth: selected ? 2 : 1))
                            .shadow(color: selected ? Theme.red.opacity(0.6) : .clear, radius: 8)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
            }
            .frame(maxHeight: 420)
        }
        .padding(16)
        .frame(width: 390)
        .background(RoundedRectangle(cornerRadius: 22).fill(Color(hex: 0x0C0D13).opacity(0.94)))
        .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(Color.white.opacity(0.15), lineWidth: 1))
        .fixedLTR()
    }

    private func step(_ seasons: [Int], _ current: Int, _ delta: Int) {
        guard let idx = seasons.firstIndex(of: current) else { return }
        let n = idx + delta
        if seasons.indices.contains(n) { season = seasons[n] }
    }
}

struct NextEpisodeCard: View {
    let seconds: Int
    let episode: Episode
    let onPlay: () -> Void
    let onCancel: () -> Void

    var body: some View {
        NeonPanel(accent: .red, corner: 20, padding: 18) {
            VStack(alignment: .leading, spacing: 10) {
                Text(tr("الحلقة التالية خلال \(seconds) ثوانٍ", "Next episode in \(seconds)s")).font(.system(size: 18, weight: .heavy)).foregroundColor(.white)
                Text(tr("الموسم \(episode.season) - الحلقة \(episode.episodeNumber) : \(episode.title)", "S\(episode.season) E\(episode.episodeNumber): \(episode.title)"))
                    .font(.system(size: 14, weight: .semibold)).foregroundColor(Theme.textSecondary).lineLimit(2)
                HStack(spacing: 10) {
                    NeonButton(title: tr("تشغيل الآن", "Play now"), systemImage: "play.fill", kind: .red, height: 40, fontSize: 14, action: onPlay)
                    NeonButton(title: tr("إلغاء", "Cancel"), systemImage: "xmark", kind: .dark, height: 40, fontSize: 14, action: onCancel)
                }
            }
            .frame(width: 340, alignment: .leading)
        }
        .contentDirection()
    }
}

struct PlaybackInfoSheet: View {
    let request: PlaybackRequest
    let onClose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(request.title).font(.system(size: 22, weight: .heavy))
                Spacer()
                Button(action: onClose) { Image(systemName: "xmark.circle.fill").font(.system(size: 22)) }.buttonStyle(PlainButtonStyle())
            }
            if let s = request.subtitle { Text(s).font(.system(size: 15, weight: .semibold)).foregroundColor(Theme.textSecondary) }
            if case .movie(let m, let d, _) = request {
                if let plot = d?.plot ?? m.plot { Text(plot).font(.system(size: 14)).foregroundColor(.white) }
                if let g = d?.genre ?? m.genre { infoLine(tr("النوع", "Genre"), g) }
                if let c = d?.cast { infoLine(tr("الممثلون", "Cast"), c) }
                if let dir = d?.director { infoLine(tr("المخرج", "Director"), dir) }
                if let r = d?.releaseDate { infoLine(tr("تاريخ الإصدار", "Release date"), r) }
            }
            Spacer()
        }
        .foregroundColor(.white)
        .padding(24)
        .frame(width: 520, height: 420)
        .background(Theme.panel)
        .contentDirection()
    }

    private func infoLine(_ k: String, _ v: String) -> some View {
        HStack(alignment: .top) {
            Text(k + ":").font(.system(size: 14, weight: .bold)).foregroundColor(Theme.textSecondary)
            Text(v).font(.system(size: 14, weight: .semibold))
        }
    }
}

// MARK: - Live player

struct LivePlayerOverlay: View {
    @EnvironmentObject var model: AppModel
    @ObservedObject var controller: PlayerController

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                linear([Color.black.opacity(0.8), Color.black.opacity(0)]).frame(height: 160)
                Spacer()
                linear([Color.black.opacity(0), Color.black.opacity(0.92)]).frame(height: 360)
            }
            .allowsHitTesting(false)

            VStack(spacing: 0) {
                HStack(spacing: 18) {
                    LogoView(height: 46)
                    HeaderDivider(height: 40)
                    LiveBadge(large: true)
                    if model.settings.showPlaybackInfo {
                        Text(controller.playbackInfo).font(.system(size: 12, weight: .semibold)).foregroundColor(.white.opacity(0.75))
                            .padding(.horizontal, 10).padding(.vertical, 4).background(Capsule().fill(Color.black.opacity(0.5)))
                    }
                    Spacer()
                    CircleIconButton(systemImage: "pin", isActive: controller.isFloating, size: 62, help: tr("تثبيت فوق النوافذ", "Keep on top")) { controller.setFloating(!controller.isFloating) }
                    CircleIconButton(systemImage: "rectangle.on.rectangle", isActive: controller.isMini, size: 62, help: tr("المشغل المصغر", "Mini player")) { controller.toggleMini() }
                    CircleIconButton(systemImage: "xmark", size: 62, help: tr("إغلاق", "Close")) { model.closePlayer() }
                }
                .padding(.horizontal, 36).padding(.top, 18)
                HStack {
                    Spacer()
                    if let c = controller.currentChannel {
                        ChannelLogo(url: c.icon, name: c.name, width: 280, height: 70)
                            .shadow(color: .black.opacity(0.6), radius: 8)
                    }
                }
                .padding(.trailing, 36).padding(.top, 12)
                Spacer()
                HStack(alignment: .bottom, spacing: 24) {
                    if let c = controller.currentChannel {
                        HStack(spacing: 24) {
                            ChannelLogo(url: c.icon, name: c.name, width: 150, height: 74)
                            HeaderDivider(height: 80)
                            VStack(alignment: .leading, spacing: 10) {
                                Text(c.name).font(.system(size: 34, weight: .heavy)).foregroundColor(.white).lineLimit(1)
                                if let now = controller.nowPlaying {
                                    HStack(spacing: 10) {
                                        Circle().fill(Theme.redBright).frame(width: 18, height: 18).shadow(color: Theme.red, radius: 5)
                                        Text(tr("مباشر الآن: ", "Live now: ") + now.title).font(.system(size: 22, weight: .bold)).foregroundColor(.white).lineLimit(1)
                                    }
                                    .contentDirection()
                                }
                            }
                        }
                    }
                    Spacer()
                    EPGListPanel(entries: Array(controller.epg.prefix(3)), compact: false)
                        .frame(width: 700)
                }
                .padding(.horizontal, 40)
                ProgressBar(fraction: controller.nowPlaying?.progress() ?? 1, height: 5, track: Color.white.opacity(0.15))
                    .padding(.horizontal, 50).padding(.top, 34)
                HStack(spacing: 30) {
                    Button(action: { controller.togglePlay() }) {
                        Image(systemName: controller.isPlaying ? "pause.fill" : "play.fill").font(.system(size: 46, weight: .bold)).foregroundColor(.white)
                    }.buttonStyle(PlainButtonStyle())
                    HStack(spacing: 18) {
                        Button(action: { controller.toggleMute() }) {
                            Image(systemName: controller.isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill").font(.system(size: 32)).foregroundColor(.white)
                        }.buttonStyle(PlainButtonStyle())
                        NeonSlider(value: Binding(get: { controller.isMuted ? 0 : controller.volume }, set: { controller.setVolume($0) }), height: 7, knob: 32)
                            .frame(width: 250)
                    }
                    Spacer()
                    Button(action: { controller.openMenu = controller.openMenu == .quality ? nil : .quality }) {
                        Text(liveQualityLabel).font(.system(size: 24, weight: .heavy)).foregroundColor(.white)
                            .frame(width: 84, height: 54)
                            .background(RoundedRectangle(cornerRadius: 10).fill(Theme.red.opacity(0.25)))
                            .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(Theme.redBright, lineWidth: 2.5))
                            .shadow(color: Theme.red, radius: 8)
                    }.buttonStyle(PlainButtonStyle())
                    liveIcon("gearshape") { controller.openMenu = controller.openMenu == .settings ? nil : .settings }
                    liveIcon("captions.bubble") { controller.openMenu = controller.openMenu == .subtitles ? nil : .subtitles }
                    liveIcon("rectangle") { controller.setAspect(nextAspect) }
                    liveIcon("arrow.up.left.and.arrow.down.right") { controller.toggleFullScreen() }
                }
                .padding(.horizontal, 50).padding(.vertical, 22)
            }

            // Channel up/down arrows
            HStack {
                ChannelArrow(systemImage: "chevron.left", accent: .red) { controller.switchChannel(by: -1) }
                Spacer()
                ChannelArrow(systemImage: "chevron.right", accent: .blue) { controller.switchChannel(by: 1) }
            }
            .padding(.horizontal, 20)
            .offset(y: -40)

            Group {
                if controller.openMenu == .quality {
                    QualityMenu(controller: controller)
                } else if let menu = controller.openMenu, menu != .channels {
                    PlayerOptionsMenu(controller: controller, kind: menu)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing)
            .padding(.trailing, 40).padding(.bottom, 120)
        }
    }

    private var liveQualityLabel: String {
        let h = Int(controller.videoSize.height)
        if h >= 1800 { return "4K" }
        if h >= 900 { return "FHD" }
        if h >= 600 || h == 0 { return "HD" }
        return "SD"
    }

    private var nextAspect: AspectMode {
        let all = AspectMode.allCases
        let idx = all.firstIndex(of: controller.aspect) ?? 0
        return all[(idx + 1) % all.count]
    }

    private func liveIcon(_ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 34, weight: .regular)).foregroundColor(.white).frame(width: 60, height: 54)
        }.buttonStyle(PlainButtonStyle())
    }
}

struct ChannelArrow: View {
    let systemImage: String
    let accent: NeonAccent
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage).font(.system(size: 34, weight: .bold)).foregroundColor(.white)
                .frame(width: 72, height: 96)
                .background(RoundedRectangle(cornerRadius: 18).fill(Color.black.opacity(0.55)))
                .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(accent.stroke, lineWidth: 2))
                .shadow(color: accent.glow, radius: 10)
        }.buttonStyle(PlainButtonStyle())
    }
}

struct LiveBadge: View {
    var large = false
    var body: some View {
        HStack(spacing: 8) {
            Circle().strokeBorder(Color.white, lineWidth: large ? 3 : 2).frame(width: large ? 18 : 13, height: large ? 18 : 13)
                .overlay(Circle().fill(Color.white).frame(width: large ? 8 : 5, height: large ? 8 : 5))
            Text("LIVE").font(.system(size: large ? 28 : 20, weight: .heavy))
        }
        .foregroundColor(.white)
        .padding(.horizontal, large ? 18 : 12)
        .frame(height: large ? 52 : 38)
        .background(RoundedRectangle(cornerRadius: 10).fill(Theme.redGradient))
        .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(Color(hex: 0xFF8080), lineWidth: 1.4))
        .shadow(color: Theme.red, radius: 10)
        .fixedLTR()
    }
}

/// EPG rows: first (current) row red with "مباشر الآن", next rows with a clock icon.
struct EPGListPanel: View {
    let entries: [EPGEntry]
    var compact = false

    var body: some View {
        VStack(spacing: compact ? 6 : 8) {
            if entries.isEmpty {
                Text(tr("لا يوجد دليل برامج لهذه القناة", "No programme guide for this channel"))
                    .font(.system(size: compact ? 14 : 17, weight: .semibold)).foregroundColor(Theme.textSecondary)
                    .frame(maxWidth: .infinity).frame(height: compact ? 44 : 52)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.black.opacity(0.4)))
            }
            ForEach(Array(entries.enumerated()), id: \.element.id) { index, e in
                let live = index == 0 && e.isLive()
                HStack(spacing: 12) {
                    Text("\(TimeFormat.hourMinute.string(from: e.start)) - \(TimeFormat.hourMinute.string(from: e.end))")
                        .font(.system(size: compact ? 16 : 20, weight: .semibold)).monoDigits().foregroundColor(.white)
                        .fixedLTR()
                    Spacer()
                    Text(e.title).font(.system(size: compact ? 16 : 20, weight: .bold)).foregroundColor(.white).lineLimit(1)
                    if live {
                        Text(tr("مباشر الآن", "LIVE NOW")).font(.system(size: compact ? 14 : 18, weight: .heavy)).foregroundColor(.white)
                            .padding(.horizontal, 14).frame(height: compact ? 30 : 36)
                            .background(Capsule().fill(Theme.redGradient))
                    } else {
                        Image(systemName: "clock").font(.system(size: compact ? 18 : 22)).foregroundColor(.white)
                    }
                }
                .padding(.horizontal, 18)
                .frame(height: compact ? 46 : 54)
                .background(RoundedRectangle(cornerRadius: 14).fill(live ? linear([Theme.red.opacity(0.55), Theme.redDeep.opacity(0.65)], .leading, .trailing) : linear([Color(hex: 0x15161E).opacity(0.85), Color(hex: 0x0B0C12).opacity(0.85)])))
                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(live ? Theme.redBright : Color.white.opacity(0.12), lineWidth: live ? 2 : 1))
                .shadow(color: live ? Theme.red.opacity(0.6) : .clear, radius: 8)
                .fixedLTR()
            }
        }
    }
}

/// Compact controls while the window is in mini-player mode.
struct MiniPlayerControls: View {
    @EnvironmentObject var model: AppModel
    @ObservedObject var controller: PlayerController

    var body: some View {
        VStack {
            HStack {
                LiveBadgeIfNeeded(isLive: controller.request.isLive)
                Spacer()
                CircleIconButton(systemImage: "arrow.up.backward.and.arrow.down.forward", size: 34) { controller.toggleMini() }
                CircleIconButton(systemImage: "xmark", size: 34) { model.closePlayer() }
            }
            Spacer()
            HStack(spacing: 18) {
                if controller.request.isLive {
                    CircleIconButton(systemImage: "chevron.left", size: 34) { controller.switchChannel(by: -1) }
                }
                CircleIconButton(systemImage: controller.isPlaying ? "pause.fill" : "play.fill", isActive: true, size: 44) { controller.togglePlay() }
                if controller.request.isLive {
                    CircleIconButton(systemImage: "chevron.right", size: 34) { controller.switchChannel(by: 1) }
                }
            }
        }
        .padding(10)
    }
}

struct LiveBadgeIfNeeded: View {
    let isLive: Bool
    var body: some View {
        if isLive { LiveBadge() } else { EmptyView() }
    }
}
