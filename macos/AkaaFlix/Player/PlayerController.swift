import AppKit
import SwiftUI

/// What to play.
enum PlaybackRequest: Equatable {
    case live(Channel, list: [Channel])
    case movie(Movie, details: MovieDetails?, resumeAt: Double?)
    case episode(Episode, series: Series, details: SeriesDetails?, resumeAt: Double?)
    case file(URL, title: String, type: ContentType, itemID: String)

    var isLive: Bool { if case .live = self { return true }; return false }
    var isEpisode: Bool { if case .episode = self { return true }; return false }

    var title: String {
        switch self {
        case .live(let c, _): return c.name
        case .movie(let m, _, _): return ContentText.displayTitles(m.name).primary
        case .episode(_, let s, _, _): return ContentText.displayTitles(s.name).primary
        case .file(_, let t, _, _): return t
        }
    }

    var subtitle: String? {
        switch self {
        case .live: return nil
        case .movie(let m, let d, _): return d?.originalName ?? ContentText.displayTitles(m.name).secondary
        case .episode(let e, _, _, _):
            return tr("الموسم \(e.season) - الحلقة \(e.episodeNumber) : \(e.title)", "Season \(e.season) - Episode \(e.episodeNumber): \(e.title)")
        case .file: return nil
        }
    }

    func candidates(creds: XtreamCredentials, settings: AppSettings, account: AccountInfo?) -> [StreamCandidate] {
        switch self {
        case .live(let c, _):
            return StreamURLBuilder.liveCandidates(creds, streamID: c.id, enabledCompatibilityFormats: settings.enabledCompatibilityFormats, rtmpPort: account?.rtmpPort)
        case .movie(let m, let d, _):
            return [StreamURLBuilder.movie(creds, streamID: m.id, containerExtension: d?.movie.containerExtension ?? m.containerExtension)].compactMap { $0 }
        case .episode(let e, _, _, _):
            return [StreamURLBuilder.episode(creds, episodeID: e.id, containerExtension: e.containerExtension)].compactMap { $0 }
        case .file(let url, _, _, _):
            return [StreamCandidate(url: url, format: nil, isAdaptive: false)]
        }
    }
}

/// Drives one playback session: failover between candidates in the fixed order, recovery,
/// progress persistence (Continue Watching), auto next episode and the overlay UI state.
final class PlayerController: ObservableObject, PlaybackEngineDelegate {

    enum Phase: Equatable { case opening, playing, paused, buffering, reconnecting, ended, failed(String) }

    @Published private(set) var request: PlaybackRequest
    @Published private(set) var phase: Phase = .opening
    @Published private(set) var currentTime: Double = 0
    @Published private(set) var duration: Double = 0
    @Published private(set) var activeFormat: LiveStreamFormat?
    @Published private(set) var attemptTrail = ""
    @Published private(set) var audioTracks: [MediaTrack] = []
    @Published private(set) var subtitleTracks: [MediaTrack] = []
    @Published private(set) var selectedAudio: Int32 = -1
    @Published private(set) var selectedSubtitle: Int32 = -1
    @Published private(set) var videoSize: CGSize = .zero
    @Published private(set) var codec: String?
    @Published private(set) var epg: [EPGEntry] = []
    @Published private(set) var nextEpisodeCountdown: Int?
    @Published private(set) var seriesDetails: SeriesDetails?
    @Published var volume: Double
    @Published var isMuted = false
    @Published var controlsVisible = true
    @Published var isLocked = false
    @Published var aspect: AspectMode = .fit
    @Published var speed: Float = 1
    @Published var selectedQuality: PlaybackQuality
    @Published var isScrubbing = false
    @Published var scrubFraction: Double = 0
    @Published var isFloating = false
    @Published var isMini = false
    @Published var showEpisodes = false
    @Published var openMenu: PlayerMenu?
    /// Live TV preview controllers keep playing in the preview panel when the full player closes.
    var returnsToPreview = false

    enum PlayerMenu: Equatable { case quality, subtitles, audio, subtitleStyle, settings, channels }

    let engine: PlaybackEngine
    let playlist: Playlist
    let credentials: XtreamCredentials
    private weak var model: AppModel?
    private var planner: FailoverPlanner?
    private var current: StreamCandidate?
    private var confirmed = false
    private var watchdog: DispatchWorkItem?
    private var pendingLoad: DispatchWorkItem?
    private var progressTimer: Timer?
    private var hideTimer: Timer?
    private var countdownTimer: Timer?
    private var epgTimer: Timer?
    private var resumeAt: Double?
    private var savedFrame: NSRect?
    private var stopped = false
    private var lastPersist = Date.distantPast
    private(set) var startedAt = Date()
    private(set) var confirmedAt: Date?

    init(model: AppModel, request: PlaybackRequest, playlist: Playlist, credentials: XtreamCredentials) {
        self.model = model
        self.request = request
        self.playlist = playlist
        self.credentials = credentials
        let settings = model.settings
        self.engine = EngineFactory.make(configuration: EngineConfiguration(settings: settings))
        self.volume = Double(settings.defaultVolume) / 100
        self.selectedQuality = settings.defaultQuality
        self.engine.delegate = self
        if case .episode(_, _, let d, _) = request { seriesDetails = d }
    }

    deinit {
        watchdog?.cancel()
        pendingLoad?.cancel()
        progressTimer?.invalidate()
        hideTimer?.invalidate()
        countdownTimer?.invalidate()
        epgTimer?.invalidate()
    }

    private var settings: AppSettings { model?.settings ?? AppSettings() }

    // MARK: Session

    func start() {
        stopped = false
        confirmed = false
        confirmedAt = nil
        startedAt = Date()
        activeFormat = nil
        nextEpisodeCountdown = nil
        countdownTimer?.invalidate()
        currentTime = 0
        duration = 0
        switch request {
        case .movie(_, _, let r), .episode(_, _, _, let r): resumeAt = settings.resumePlayback ? r : nil
        default: resumeAt = nil
        }
        let candidates = request.candidates(creds: credentials, settings: settings, account: playlist.account)
        let p = FailoverPlanner(candidates: candidates, maxReconnects: request.isLive ? 8 : 2)
        planner = p
        apply(p.start())
        if request.isLive { loadEPG() }
        if case .episode(_, let s, let d, _) = request, d == nil { loadSeriesDetails(s) }
        progressTimer?.invalidate()
        progressTimer = Timer.scheduledTimer(withTimeInterval: 10, repeats: true) { [weak self] _ in self?.persistProgress() }
        pokeControls()
    }

    private func apply(_ decision: FailoverPlanner.Decision) {
        attemptTrail = planner?.trail ?? ""
        switch decision {
        case .giveUp:
            watchdog?.cancel()
            engine.stop()
            let message: String
            if request.isLive {
                message = tr("تعذر تشغيل القناة بتنسيق MPEG-TS أو HLS", "The channel could not be played as MPEG-TS or HLS")
            } else {
                message = tr("تعذر تشغيل هذا المحتوى", "This content could not be played")
            }
            phase = .failed(message)
        case .play(let candidate, let delay):
            current = candidate
            confirmed = false
            if phase != .opening { phase = confirmedAt == nil ? .opening : .reconnecting }
            pendingLoad?.cancel()
            let work = DispatchWorkItem { [weak self] in self?.load(candidate) }
            pendingLoad = work
            DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: work)
        }
    }

    private func load(_ candidate: StreamCandidate) {
        guard !stopped else { return }
        let s = settings
        var preferred: Int?
        if candidate.isAdaptive {
            preferred = s.autoQuality && selectedQuality == s.defaultQuality ? nil : selectedQuality.maxHeight
        }
        let options = EngineLoadOptions(
            networkCachingMs: s.networkCachingMs,
            startTime: resumeAt,
            preferredResolution: preferred,
            audioLanguage: s.preferredAudioLanguage,
            subtitleLanguage: s.preferredSubtitleLanguage,
            subtitlesEnabled: s.subtitlesEnabled,
            isLive: request.isLive)
        engine.load(candidate.url, options: options)
        engine.volume = Int(volume * 100)
        engine.isMuted = isMuted
        engine.setAspect(aspect)
        watchdog?.cancel()
        let timeout = Double(max(3, s.reconnectTimeoutSeconds)) + (candidate.isAdaptive ? 4 : 0)
        let w = DispatchWorkItem { [weak self] in
            guard let self = self, !self.confirmed, !self.stopped else { return }
            self.failCurrent()
        }
        watchdog = w
        DispatchQueue.main.asyncAfter(deadline: .now() + timeout, execute: w)
    }

    private func failCurrent() {
        guard let p = planner, !stopped else { return }
        apply(p.didFail())
    }

    private func confirmPlayback() {
        guard !confirmed else { return }
        confirmed = true
        confirmedAt = Date()
        watchdog?.cancel()
        planner?.didConfirmPlayback()
        attemptTrail = planner?.trail ?? ""
        activeFormat = current?.format
        phase = engine.isPlaying ? .playing : .buffering
        resumeAt = nil
        refreshTracks()
        if engine.subtitleDelayMs != settings.subtitleDelayMs { engine.subtitleDelayMs = settings.subtitleDelayMs }
        engine.rate = speed
        persistProgress(countPlay: true)
    }

    func retry() {
        start()
    }

    func stop() {
        guard !stopped else { return }
        persistProgress()
        stopped = true
        watchdog?.cancel()
        pendingLoad?.cancel()
        progressTimer?.invalidate()
        hideTimer?.invalidate()
        countdownTimer?.invalidate()
        epgTimer?.invalidate()
        engine.stop()
        if isFloating { setFloating(false) }
        if isMini { toggleMini() }
    }

    // MARK: Engine delegate

    func engine(_ engine: PlaybackEngine, didChange state: EngineState) {
        guard !stopped else { return }
        switch state {
        case .playing:
            if confirmed {
                phase = .playing
            } else if !request.isLive || engine.currentTime > 0 || engine.videoSize != .zero {
                // Files confirm on "playing"; live streams also need media time or a video output.
                confirmPlayback()
            }
        case .buffering:
            if confirmed { phase = .buffering }
        case .paused:
            if confirmed { phase = .paused }
        case .opening:
            break
        case .ended:
            if !confirmed { failCurrent(); return }
            if request.isLive { failCurrent(); return }
            handleEnded()
        case .error:
            if confirmed && !request.isLive {
                // VOD hiccup: resume once from the last position.
                resumeAt = currentTime > 5 ? currentTime : nil
            }
            failCurrent()
        case .stopped, .idle:
            break
        }
    }

    func engineTimeDidChange(_ engine: PlaybackEngine) {
        guard !stopped else { return }
        if !confirmed, engine.currentTime > 0 { confirmPlayback() }
        if !isScrubbing { currentTime = engine.currentTime }
        let d = engine.duration
        if d > 0, abs(d - duration) > 0.5 { duration = d }
        if videoSize == .zero || videoSize != engine.videoSize { videoSize = engine.videoSize }
        if confirmed, phase != .playing, engine.isPlaying { phase = .playing }
        if audioTracks.isEmpty, confirmed { refreshTracks() }
    }

    private func refreshTracks() {
        audioTracks = engine.audioTracks
        subtitleTracks = engine.subtitleTracks
        selectedAudio = engine.currentAudioTrack
        selectedSubtitle = engine.currentSubtitleTrack
        codec = engine.codecDescription
        videoSize = engine.videoSize
    }

    // MARK: Transport

    var isPlaying: Bool { phase == .playing || phase == .buffering }

    func togglePlay() {
        if case .failed = phase { retry(); return }
        if phase == .ended { seek(to: 0); engine.play(); return }
        if engine.isPlaying { engine.pause(); phase = .paused } else { engine.play(); phase = confirmed ? .playing : .opening }
        pokeControls()
    }

    func seek(to seconds: Double) {
        guard engine.isSeekable else { return }
        engine.seek(to: min(max(0, seconds), max(0, duration - 1)))
        currentTime = seconds
        pokeControls()
    }

    func skip(_ delta: Double) { seek(to: currentTime + delta) }

    func commitScrub() {
        isScrubbing = false
        seek(to: scrubFraction * duration)
    }

    func setVolume(_ v: Double) {
        volume = min(1, max(0, v))
        engine.volume = Int(volume * 100)
        if volume > 0 && isMuted { toggleMute() }
    }

    func toggleMute() {
        isMuted.toggle()
        engine.isMuted = isMuted
    }

    func selectAudio(_ id: Int32) {
        engine.currentAudioTrack = id
        selectedAudio = engine.currentAudioTrack
    }

    func selectSubtitle(_ id: Int32) {
        engine.currentSubtitleTrack = id
        selectedSubtitle = engine.currentSubtitleTrack
    }

    func adjustSubtitleDelay(_ deltaMs: Int) {
        engine.subtitleDelayMs += deltaMs
        model?.updateSettings { $0.subtitleDelayMs = engine.subtitleDelayMs }
    }

    var subtitleDelayMs: Int { engine.subtitleDelayMs }

    func loadSubtitleFile() {
        let panel = NSOpenPanel()
        panel.allowedFileTypes = ["srt", "vtt", "ass", "ssa", "sub", "smi"]
        panel.allowsMultipleSelection = false
        panel.canChooseDirectories = false
        guard panel.runModal() == .OK, let url = panel.url else { return }
        if engine.addExternalSubtitle(url) {
            model?.showToast(tr("تم تحميل ملف الترجمة", "Subtitle file loaded"), .success)
            DispatchQueue.main.asyncAfter(deadline: .now() + 1) { [weak self] in self?.refreshTracks() }
        } else {
            model?.showToast(tr("تعذر تحميل ملف الترجمة", "Could not load the subtitle file"), .error)
        }
    }

    func setAspect(_ mode: AspectMode) {
        aspect = mode
        engine.setAspect(mode)
    }

    func setSpeed(_ value: Float) {
        speed = value
        engine.rate = value
    }

    /// Requests a maximum resolution for adaptive (HLS/DASH) sources and reloads at the same position.
    func setQuality(_ q: PlaybackQuality) {
        selectedQuality = q
        guard current?.isAdaptive == true, let c = current else { return }
        resumeAt = request.isLive ? nil : currentTime
        confirmed = false
        phase = .opening
        load(c)
    }

    var qualityIsAdjustable: Bool { current?.isAdaptive == true }

    /// Quality matching the decoded picture (for single-quality files).
    var detectedQuality: PlaybackQuality? {
        let h = Int(videoSize.height)
        guard h > 0 else { return nil }
        if h >= 1800 { return .uhd4K }
        if h >= 900 { return .fhd1080 }
        if h >= 620 { return .hd720 }
        if h >= 420 { return .sd480 }
        return .low360
    }

    func takeSnapshot() {
        let dir = model?.paths.snapshots ?? FileManager.default.temporaryDirectory
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd 'at' HH.mm.ss"
        let url = dir.appendingPathComponent("Akaa Flix \(f.string(from: Date())).png")
        if engine.snapshot(to: url) {
            model?.showToast(tr("تم حفظ لقطة الشاشة في الصور", "Screenshot saved to Pictures"), .success)
        } else {
            model?.showToast(tr("تعذر التقاط الصورة", "Could not take a screenshot"), .error)
        }
    }

    // MARK: Live channels

    var channelList: [Channel] { if case .live(_, let list) = request { return list }; return [] }

    var currentChannel: Channel? { if case .live(let c, _) = request { return c }; return nil }

    func switchChannel(by offset: Int) {
        guard case .live(let c, let list) = request, !list.isEmpty,
              let idx = list.firstIndex(where: { $0.id == c.id }) else { return }
        let next = list[(idx + offset + list.count) % list.count]
        play(channel: next)
    }

    func play(channel: Channel) {
        guard case .live(_, let list) = request else { return }
        persistProgress()
        request = .live(channel, list: list)
        start()
    }

    private func loadEPG() {
        guard case .live(let c, _) = request else { return }
        let creds = credentials
        let client = model?.client
        let id = c.id
        epg = []
        Task { [weak self] in
            let entries = (try? await client?.shortEPG(creds, streamID: id, limit: 6)) ?? []
            await MainActor.run {
                guard let self = self, self.currentChannel?.id == id else { return }
                self.epg = entries.filter { $0.end > Date() }
            }
        }
        epgTimer?.invalidate()
        epgTimer = Timer.scheduledTimer(withTimeInterval: 300, repeats: true) { [weak self] _ in self?.loadEPG() }
    }

    var nowPlaying: EPGEntry? { epg.first(where: { $0.isLive() }) }

    // MARK: Episodes

    private func loadSeriesDetails(_ s: Series) {
        let creds = credentials
        let client = model?.client
        Task { [weak self] in
            guard let details = try? await client?.seriesDetails(creds, series: s) else { return }
            await MainActor.run { self?.seriesDetails = details }
        }
    }

    var currentEpisode: Episode? { if case .episode(let e, _, _, _) = request { return e }; return nil }

    var nextEpisode: Episode? {
        guard let e = currentEpisode, let d = seriesDetails else { return nil }
        return EpisodeNavigator.next(after: e, in: d)
    }

    var previousEpisode: Episode? {
        guard let e = currentEpisode, let d = seriesDetails else { return nil }
        return EpisodeNavigator.previous(before: e, in: d)
    }

    func play(episode: Episode) {
        guard case .episode(_, let s, _, _) = request else { return }
        persistProgress()
        let progress = model?.activePlaylist.flatMap { model?.database.userData.progress($0.id, .episode, episode.id) }
        let resume = progress.flatMap { ContinueWatchingRules.resumePosition(position: $0.position, duration: $0.duration) }
        request = .episode(episode, series: s, details: seriesDetails, resumeAt: resume)
        start()
    }

    func playNext() {
        if request.isLive { switchChannel(by: 1); return }
        if let n = nextEpisode { play(episode: n) }
    }

    func playPrevious() {
        if request.isLive { switchChannel(by: -1); return }
        if let p = previousEpisode { play(episode: p) } else { seek(to: 0) }
    }

    private func handleEnded() {
        phase = .ended
        currentTime = duration
        persistProgress()
        guard request.isEpisode, settings.autoPlayNextEpisode, nextEpisode != nil else { return }
        nextEpisodeCountdown = max(3, settings.nextEpisodeCountdown)
        countdownTimer?.invalidate()
        countdownTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] t in
            guard let self = self, let n = self.nextEpisodeCountdown else { t.invalidate(); return }
            if n <= 1 {
                t.invalidate()
                self.nextEpisodeCountdown = nil
                self.playNext()
            } else {
                self.nextEpisodeCountdown = n - 1
            }
        }
    }

    func cancelAutoNext() {
        countdownTimer?.invalidate()
        nextEpisodeCountdown = nil
    }

    /// Test hook used by the self-test harness to simulate the end of an episode.
    func simulateEndForTesting() { handleEnded() }

    // MARK: Persistence (Continue Watching / history / most watched)

    func persistProgress(countPlay: Bool = false) {
        guard let model = model, confirmed || countPlay else { return }
        let pid = playlist.id
        let now = Date()
        let position = currentTime
        let total = duration
        let entry: WatchProgress?
        switch request {
        case .live(let c, _):
            guard countPlay else { return }
            entry = WatchProgress(type: .live, itemID: String(c.id), title: c.name, image: c.icon, position: 0, duration: 0, updatedAt: now,
                                  seriesID: nil, season: nil, episodeNumber: nil, containerExtension: nil, playCount: 0)
        case .movie(let m, let d, _):
            guard position > 1 || countPlay else { return }
            entry = WatchProgress(type: .movie, itemID: String(m.id), title: ContentText.displayTitles(m.name).primary, image: d?.backdrop ?? m.poster,
                                  position: position, duration: total, updatedAt: now, seriesID: nil, season: nil, episodeNumber: nil,
                                  containerExtension: d?.movie.containerExtension ?? m.containerExtension, playCount: 0)
        case .episode(let e, let s, _, _):
            guard position > 1 || countPlay else { return }
            entry = WatchProgress(type: .episode, itemID: e.id, title: ContentText.displayTitles(s.name).primary + " · S\(e.season)E\(e.episodeNumber)",
                                  image: e.thumbnail ?? s.backdrop ?? s.cover, position: position, duration: total, updatedAt: now,
                                  seriesID: s.id, season: e.season, episodeNumber: e.episodeNumber, containerExtension: e.containerExtension, playCount: 0)
        case .file(_, let title, let type, let itemID):
            guard position > 1 else { return }
            entry = WatchProgress(type: type, itemID: itemID, title: title, image: nil, position: position, duration: total, updatedAt: now,
                                  seriesID: nil, season: nil, episodeNumber: nil, containerExtension: nil, playCount: 0)
        }
        guard let e = entry else { return }
        try? model.database.userData.saveProgress(pid, e, countPlay: countPlay)
        lastPersist = now
    }

    // MARK: Overlay

    func pokeControls() {
        controlsVisible = true
        hideTimer?.invalidate()
        hideTimer = Timer.scheduledTimer(withTimeInterval: 3.5, repeats: false) { [weak self] _ in
            guard let self = self else { return }
            if self.isPlaying && self.openMenu == nil && !self.showEpisodes && !self.isScrubbing {
                withAnimation(.easeOut(duration: 0.3)) { self.controlsVisible = false }
            }
        }
    }

    /// The app's main window (key/main may be nil while another app is frontmost).
    private var hostWindow: NSWindow? {
        NSApp.keyWindow ?? NSApp.mainWindow ?? NSApp.windows.first(where: { $0.isVisible && $0.styleMask.contains(.titled) && !($0 is NSPanel) })
    }

    func toggleFullScreen() {
        guard let window = hostWindow else { return }
        if !window.isKeyWindow { window.makeKeyAndOrderFront(nil) }
        window.toggleFullScreen(nil)
    }

    var isFullScreen: Bool {
        hostWindow?.styleMask.contains(.fullScreen) ?? false
    }

    /// "Pin" — keeps the window above other apps.
    func setFloating(_ on: Bool) {
        isFloating = on
        hostWindow?.level = on ? .floating : .normal
    }

    /// Mini player: shrinks the window to a floating 16:9 video.
    func toggleMini() {
        guard let window = hostWindow else { return }
        if isMini {
            isMini = false
            window.level = isFloating ? .floating : .normal
            if let f = savedFrame { window.setFrame(f, display: true, animate: true) }
            window.minSize = NSSize(width: 1100, height: 680)
        } else {
            if window.styleMask.contains(.fullScreen) { window.toggleFullScreen(nil) }
            savedFrame = window.frame
            isMini = true
            window.minSize = NSSize(width: 320, height: 180)
            let screen = window.screen?.visibleFrame ?? NSRect(x: 0, y: 0, width: 1440, height: 900)
            let size = NSSize(width: 560, height: 340)
            window.setFrame(NSRect(x: screen.maxX - size.width - 24, y: screen.minY + 24, width: size.width, height: size.height), display: true, animate: true)
            window.level = .floating
        }
    }

    /// Diagnostics line shown when "show playback info" is enabled.
    var playbackInfo: String {
        var parts: [String] = []
        if let f = activeFormat { parts.append(f.displayName) } else if current != nil && !request.isLive { parts.append(tr("ملف", "File")) }
        if videoSize.width > 0 { parts.append("\u{2066}\(Int(videoSize.width))×\(Int(videoSize.height))\u{2069}") }
        if let c = codec { parts.append(c) }
        parts.append(engine.name)
        parts.append(settings.hardwareDecoding == .software ? tr("فك ترميز برمجي", "Software decoding") : tr("تسريع عتادي", "Hardware decoding"))
        parts.append(SystemInfo.binaryArchitecture)
        return parts.joined(separator: " · ")
    }
}
