#if canImport(VLCKit)
import AppKit
import VLCKit

/// VLCKit 3.x engine. Plays raw MPEG-TS over HTTP (which AVFoundation cannot), HLS, DASH,
/// RTMP/RTSP and progressive files, with VideoToolbox hardware decoding on Intel and Apple
/// Silicon and automatic software (libavcodec) fallback.
final class VLCPlaybackEngine: NSObject, PlaybackEngine, VLCMediaPlayerDelegate {

    let name = "VLCKit"
    weak var delegate: PlaybackEngineDelegate?

    private let videoView: VLCVideoView
    private let player: VLCMediaPlayer
    private var aspectBuffer: UnsafeMutablePointer<CChar>?
    private(set) var state: EngineState = .idle

    static var libraryVersion: String { VLCLibrary.shared().version }
    var version: String { VLCPlaybackEngine.libraryVersion }
    var view: NSView { videoView }

    init(configuration: EngineConfiguration) {
        videoView = VLCVideoView(frame: NSRect(x: 0, y: 0, width: 640, height: 360))
        videoView.backColor = NSColor.black
        videoView.fillScreen = false
        videoView.autoresizingMask = [.width, .height]
        player = VLCMediaPlayer(options: VLCPlaybackEngine.libraryOptions(configuration))
        super.init()
        player.drawable = videoView
        player.delegate = self
    }

    deinit {
        player.delegate = nil
        player.stop()
        if let b = aspectBuffer { free(b) }
    }

    static func libraryOptions(_ c: EngineConfiguration) -> [String] {
        var o = ["--no-video-title-show", "--no-snapshot-preview", "--network-caching=\(c.networkCachingMs)"]
        // Subtitle rendering (freetype): relative size, colour and optional background box.
        let relSize: Int
        switch c.subtitleSize {
        case .small: relSize = 20
        case .medium: relSize = 16
        case .large: relSize = 12
        case .extraLarge: relSize = 9
        }
        let color: Int
        switch c.subtitleColor {
        case .white: color = 0xFFFFFF
        case .yellow: color = 0xFFEB3B
        case .green: color = 0x4CFF6A
        case .cyan: color = 0x40E0FF
        }
        o += ["--freetype-rel-fontsize=\(relSize)", "--freetype-color=\(color)", "--freetype-outline-thickness=2"]
        if c.subtitleBackground { o += ["--freetype-background-opacity=150", "--freetype-background-color=0"] }
        if c.hardwareDecoding == .software {
            // Software decoding fallback: prefer libavcodec without VideoToolbox.
            o += ["--codec=avcodec,all", "--avcodec-hw=none"]
        }
        return o
    }

    // MARK: Loading

    func load(_ url: URL, options: EngineLoadOptions) {
        let media = VLCMedia(url: url)
        media.addOption(":network-caching=\(options.networkCachingMs)")
        if options.isLive {
            media.addOption(":http-reconnect")
            media.addOption(":live-caching=\(options.networkCachingMs)")
        }
        if let start = options.startTime, start > 1 {
            media.addOption(":start-time=\(Int(start))")
        }
        if let res = options.preferredResolution {
            media.addOption(":preferred-resolution=\(res)")
            media.addOption(":adaptive-maxheight=\(res)")
        }
        media.addOption(":audio-language=\(VLCPlaybackEngine.languageList(options.audioLanguage))")
        if options.subtitlesEnabled {
            media.addOption(":sub-language=\(VLCPlaybackEngine.languageList(options.subtitleLanguage))")
        } else {
            media.addOption(":no-sub-autodetect-file")
        }
        state = .opening
        player.media = media
        player.play()
    }

    static func languageList(_ code: String) -> String {
        switch code.lowercased() {
        case "ar": return "ar,ara,arabic"
        case "en": return "en,eng,english"
        case "fr": return "fr,fre,fra,french"
        case "tr": return "tr,tur,turkish"
        default: return code
        }
    }

    func play() { player.play() }
    func pause() { if player.canPause { player.pause() } }
    func stop() {
        player.stop()
        state = .stopped
    }

    // MARK: State

    var isPlaying: Bool { player.isPlaying }
    var currentTime: Double { Double(player.time.intValue) / 1000 }
    var duration: Double {
        guard let length = player.media?.length else { return 0 }
        return max(0, Double(length.intValue) / 1000)
    }
    var isSeekable: Bool { player.isSeekable }

    func seek(to seconds: Double) {
        guard player.isSeekable else { return }
        player.time = VLCTime(int: Int32(max(0, seconds) * 1000))
    }

    var volume: Int {
        get { Int(player.audio?.volume ?? 100) }
        set { player.audio?.volume = Int32(min(100, max(0, newValue))) }
    }

    var isMuted: Bool {
        get { player.audio?.isMuted ?? false }
        set { player.audio?.isMuted = newValue }
    }

    var rate: Float {
        get { player.rate }
        set { player.rate = newValue }
    }

    private func tracks(names: [Any]?, indexes: [Any]?) -> [MediaTrack] {
        let n = (names ?? []).map { "\($0)" }
        let ids = (indexes ?? []).compactMap { ($0 as? NSNumber)?.int32Value }
        return zip(ids, n).map { MediaTrack(id: $0.0, name: $0.1) }
    }

    var audioTracks: [MediaTrack] { tracks(names: player.audioTrackNames, indexes: player.audioTrackIndexes).filter { $0.id >= 0 } }
    var currentAudioTrack: Int32 {
        get { player.currentAudioTrackIndex }
        set { player.currentAudioTrackIndex = newValue }
    }

    var subtitleTracks: [MediaTrack] { tracks(names: player.videoSubTitlesNames, indexes: player.videoSubTitlesIndexes).filter { $0.id >= 0 } }
    var currentSubtitleTrack: Int32 {
        get { player.currentVideoSubTitleIndex }
        set { player.currentVideoSubTitleIndex = newValue }
    }

    var supportsExternalSubtitles: Bool { true }

    func addExternalSubtitle(_ url: URL) -> Bool {
        player.addPlaybackSlave(url, type: .subtitle, enforce: true) == 0
    }

    /// VLCKit expresses the delay in microseconds.
    var subtitleDelayMs: Int {
        get { player.currentVideoSubTitleDelay / 1000 }
        set { player.currentVideoSubTitleDelay = newValue * 1000 }
    }

    func setAspect(_ mode: AspectMode) {
        videoView.fillScreen = mode == .fill
        let old = aspectBuffer
        if let ratio = mode.ratio {
            aspectBuffer = strdup(ratio)
            player.videoAspectRatio = aspectBuffer
        } else {
            aspectBuffer = nil
            player.videoAspectRatio = nil
        }
        if let o = old { free(o) }
    }

    var videoSize: CGSize { player.videoSize }

    var codecDescription: String? {
        guard let infos = player.media?.tracksInformation else { return nil }
        for case let info as [String: Any] in infos {
            guard (info[VLCMediaTracksInformationType] as? String) == VLCMediaTracksInformationTypeVideo,
                  let fourcc = (info[VLCMediaTracksInformationCodec] as? NSNumber)?.uint32Value else { continue }
            return VLCMedia.codecName(forFourCC: fourcc, trackType: VLCMediaTracksInformationTypeVideo)
        }
        return nil
    }

    func snapshot(to url: URL) -> Bool {
        guard player.hasVideoOut else { return false }
        player.saveVideoSnapshot(at: url.path, withWidth: 0, andHeight: 0)
        return true
    }

    // MARK: VLCMediaPlayerDelegate

    func mediaPlayerStateChanged(_ aNotification: Notification) {
        let mapped: EngineState
        switch player.state {
        case .opening: mapped = .opening
        case .buffering: mapped = player.isPlaying ? .playing : .buffering
        case .playing: mapped = .playing
        case .paused: mapped = .paused
        case .ended: mapped = .ended
        case .error: mapped = .error
        case .stopped: mapped = state == .opening ? .error : .stopped
        default: return
        }
        guard mapped != state || mapped == .error || mapped == .ended else { return }
        state = mapped
        let s = mapped
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.delegate?.engine(self, didChange: s)
        }
    }

    func mediaPlayerTimeChanged(_ aNotification: Notification) {
        if state != .playing && player.isPlaying { state = .playing }
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            self.delegate?.engineTimeDidChange(self)
        }
    }
}
#endif
