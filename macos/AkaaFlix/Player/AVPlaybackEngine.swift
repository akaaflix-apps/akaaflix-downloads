import AppKit
import AVFoundation
import CoreImage

/// AVFoundation fallback engine (HLS, MP4/MOV). It cannot play raw MPEG-TS over HTTP; when it is
/// in use the MPEG-TS attempt fails fast and the fixed priority moves on to HLS.
final class AVPlaybackEngine: NSObject, PlaybackEngine {

    let name = "AVFoundation"
    var version: String { ProcessInfo.processInfo.operatingSystemVersionString }
    weak var delegate: PlaybackEngineDelegate?

    private let player = AVPlayer()
    private let hostView = PlayerLayerView()
    private var statusObservation: NSKeyValueObservation?
    private var timeControlObservation: NSKeyValueObservation?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var failObserver: NSObjectProtocol?
    private var videoOutput: AVPlayerItemVideoOutput?
    private(set) var state: EngineState = .idle

    var view: NSView { hostView }

    override init() {
        super.init()
        hostView.playerLayer.player = player
        hostView.playerLayer.videoGravity = .resizeAspect
        player.automaticallyWaitsToMinimizeStalling = true
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main) { [weak self] _ in
            guard let self = self else { return }
            self.delegate?.engineTimeDidChange(self)
        }
        timeControlObservation = player.observe(\.timeControlStatus, options: [.new]) { [weak self] p, _ in
            DispatchQueue.main.async {
                guard let self = self else { return }
                switch p.timeControlStatus {
                case .playing: self.set(.playing)
                case .paused: if self.state == .playing { self.set(.paused) }
                case .waitingToPlayAtSpecifiedRate: if self.state == .playing { self.set(.buffering) }
                @unknown default: break
                }
            }
        }
    }

    deinit {
        if let t = timeObserver { player.removeTimeObserver(t) }
        if let e = endObserver { NotificationCenter.default.removeObserver(e) }
        if let f = failObserver { NotificationCenter.default.removeObserver(f) }
    }

    private func set(_ s: EngineState) {
        guard s != state || s == .error else { return }
        state = s
        delegate?.engine(self, didChange: s)
    }

    func load(_ url: URL, options: EngineLoadOptions) {
        if let e = endObserver { NotificationCenter.default.removeObserver(e) }
        if let f = failObserver { NotificationCenter.default.removeObserver(f) }
        let asset = AVURLAsset(url: url)
        let item = AVPlayerItem(asset: asset)
        item.preferredForwardBufferDuration = Double(options.networkCachingMs) / 1000
        if let res = options.preferredResolution {
            item.preferredMaximumResolution = CGSize(width: Double(res) * 16 / 9, height: Double(res))
        }
        let output = AVPlayerItemVideoOutput(pixelBufferAttributes: [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA])
        item.add(output)
        videoOutput = output
        statusObservation = item.observe(\.status, options: [.new]) { [weak self] it, _ in
            DispatchQueue.main.async {
                guard let self = self else { return }
                if it.status == .failed { self.set(.error) }
                if it.status == .readyToPlay, let start = options.startTime, start > 1 {
                    self.seek(to: start)
                }
            }
        }
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            self?.set(.ended)
        }
        failObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemFailedToPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            self?.set(.error)
        }
        state = .opening
        player.replaceCurrentItem(with: item)
        player.play()
    }

    func play() { player.play() }
    func pause() { player.pause() }
    func stop() {
        player.pause()
        player.replaceCurrentItem(with: nil)
        state = .stopped
    }

    var isPlaying: Bool { player.timeControlStatus == .playing }
    var currentTime: Double {
        let t = player.currentTime().seconds
        return t.isFinite ? t : 0
    }
    var duration: Double {
        guard let d = player.currentItem?.duration.seconds, d.isFinite else { return 0 }
        return d
    }
    var isSeekable: Bool { duration > 0 }
    func seek(to seconds: Double) {
        player.seek(to: CMTime(seconds: max(0, seconds), preferredTimescale: 600), toleranceBefore: .zero, toleranceAfter: .zero)
    }

    var volume: Int {
        get { Int(player.volume * 100) }
        set { player.volume = Float(min(100, max(0, newValue))) / 100 }
    }
    var isMuted: Bool {
        get { player.isMuted }
        set { player.isMuted = newValue }
    }
    var rate: Float {
        get { player.rate == 0 ? 1 : player.rate }
        set { if isPlaying { player.rate = newValue } }
    }

    private func group(_ characteristic: AVMediaCharacteristic) -> AVMediaSelectionGroup? {
        player.currentItem?.asset.mediaSelectionGroup(forMediaCharacteristic: characteristic)
    }

    private func tracks(_ characteristic: AVMediaCharacteristic) -> [MediaTrack] {
        guard let g = group(characteristic) else { return [] }
        return g.options.enumerated().map { MediaTrack(id: Int32($0.offset), name: $0.element.displayName) }
    }

    private func selected(_ characteristic: AVMediaCharacteristic) -> Int32 {
        guard let g = group(characteristic), let item = player.currentItem,
              let opt = item.currentMediaSelection.selectedMediaOption(in: g),
              let idx = g.options.firstIndex(of: opt) else { return -1 }
        return Int32(idx)
    }

    private func select(_ characteristic: AVMediaCharacteristic, _ index: Int32) {
        guard let g = group(characteristic), let item = player.currentItem else { return }
        if index < 0 || Int(index) >= g.options.count {
            item.select(nil, in: g)
        } else {
            item.select(g.options[Int(index)], in: g)
        }
    }

    var audioTracks: [MediaTrack] { tracks(.audible) }
    var currentAudioTrack: Int32 {
        get { selected(.audible) }
        set { select(.audible, newValue) }
    }
    var subtitleTracks: [MediaTrack] { tracks(.legible) }
    var currentSubtitleTrack: Int32 {
        get { selected(.legible) }
        set { select(.legible, newValue) }
    }
    var supportsExternalSubtitles: Bool { false }
    func addExternalSubtitle(_ url: URL) -> Bool { false }
    var subtitleDelayMs: Int = 0

    func setAspect(_ mode: AspectMode) {
        switch mode {
        case .fill: hostView.playerLayer.videoGravity = .resizeAspectFill
        case .fit: hostView.playerLayer.videoGravity = .resizeAspect
        case .wide16x9, .classic4x3: hostView.playerLayer.videoGravity = .resize
        }
    }

    var videoSize: CGSize { player.currentItem?.presentationSize ?? .zero }
    var codecDescription: String? { nil }

    func snapshot(to url: URL) -> Bool {
        guard let output = videoOutput, let item = player.currentItem else { return false }
        let time = item.currentTime()
        guard let buffer = output.copyPixelBuffer(forItemTime: time, itemTimeForDisplay: nil) else { return false }
        let ci = CIImage(cvPixelBuffer: buffer)
        let rep = NSCIImageRep(ciImage: ci)
        let image = NSImage(size: rep.size)
        image.addRepresentation(rep)
        guard let tiff = image.tiffRepresentation, let bitmap = NSBitmapImageRep(data: tiff),
              let png = bitmap.representation(using: .png, properties: [:]) else { return false }
        return (try? png.write(to: url)) != nil
    }
}

/// Layer-backed view hosting an AVPlayerLayer.
final class PlayerLayerView: NSView {
    let playerLayer = AVPlayerLayer()

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        layer = CALayer()
        layer?.backgroundColor = NSColor.black.cgColor
        playerLayer.frame = bounds
        playerLayer.autoresizingMask = [.layerWidthSizable, .layerHeightSizable]
        layer?.addSublayer(playerLayer)
        autoresizingMask = [.width, .height]
    }

    convenience init() { self.init(frame: NSRect(x: 0, y: 0, width: 640, height: 360)) }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func layout() {
        super.layout()
        playerLayer.frame = bounds
    }
}
