import AppKit
import Foundation

enum EngineState: Equatable {
    case idle, opening, buffering, playing, paused, ended, error, stopped
}

struct MediaTrack: Identifiable, Hashable {
    let id: Int32
    let name: String
}

enum AspectMode: String, CaseIterable {
    case fit, fill, wide16x9, classic4x3

    var label: String {
        switch self {
        case .fit: return tr("ملاءمة", "Fit")
        case .fill: return tr("ملء الشاشة", "Fill")
        case .wide16x9: return "16:9"
        case .classic4x3: return "4:3"
        }
    }
    var ratio: String? {
        switch self {
        case .wide16x9: return "16:9"
        case .classic4x3: return "4:3"
        default: return nil
        }
    }
}

/// Per-load options derived from settings.
struct EngineLoadOptions {
    var networkCachingMs: Int
    var startTime: Double?
    var preferredResolution: Int?
    var audioLanguage: String
    var subtitleLanguage: String
    var subtitlesEnabled: Bool
    var isLive: Bool
}

/// Library-level options (applied when an engine is created).
struct EngineConfiguration: Equatable {
    var hardwareDecoding: HardwareDecoding
    var subtitleSize: SubtitleSize
    var subtitleColor: SubtitleColor
    var subtitleBackground: Bool
    var networkCachingMs: Int

    init(settings: AppSettings) {
        hardwareDecoding = settings.hardwareDecoding
        subtitleSize = settings.subtitleSize
        subtitleColor = settings.subtitleColor
        subtitleBackground = settings.subtitleBackground
        networkCachingMs = settings.networkCachingMs
    }
}

protocol PlaybackEngineDelegate: AnyObject {
    func engine(_ engine: PlaybackEngine, didChange state: EngineState)
    func engineTimeDidChange(_ engine: PlaybackEngine)
}

/// Abstraction over the playback back-ends so the product rules (stream priority, failover,
/// resume, auto-next) are implemented once, independent of engine and CPU architecture.
protocol PlaybackEngine: AnyObject {
    var name: String { get }
    var version: String { get }
    var view: NSView { get }
    var delegate: PlaybackEngineDelegate? { get set }

    func load(_ url: URL, options: EngineLoadOptions)
    func play()
    func pause()
    func stop()

    var state: EngineState { get }
    var isPlaying: Bool { get }
    var currentTime: Double { get }
    var duration: Double { get }
    var isSeekable: Bool { get }
    func seek(to seconds: Double)

    var volume: Int { get set }        // 0...100
    var isMuted: Bool { get set }
    var rate: Float { get set }

    var audioTracks: [MediaTrack] { get }
    var currentAudioTrack: Int32 { get set }
    var subtitleTracks: [MediaTrack] { get }
    var currentSubtitleTrack: Int32 { get set }
    func addExternalSubtitle(_ url: URL) -> Bool
    var subtitleDelayMs: Int { get set }
    var supportsExternalSubtitles: Bool { get }

    func setAspect(_ mode: AspectMode)
    var videoSize: CGSize { get }
    var codecDescription: String? { get }
    func snapshot(to url: URL) -> Bool
}

enum EngineFactory {

    /// VLCKit is the primary engine on both arm64 and x86_64 (universal framework). AVFoundation is
    /// kept as a compatibility fallback if VLCKit is missing from a build.
    static func make(configuration: EngineConfiguration) -> PlaybackEngine {
        #if canImport(VLCKit)
        return VLCPlaybackEngine(configuration: configuration)
        #else
        return AVPlaybackEngine()
        #endif
    }

    static var primaryEngineName: String {
        #if canImport(VLCKit)
        return "VLCKit"
        #else
        return "AVFoundation"
        #endif
    }

    static var primaryEngineVersion: String {
        #if canImport(VLCKit)
        return VLCPlaybackEngine.libraryVersion
        #else
        return ProcessInfo.processInfo.operatingSystemVersionString
        #endif
    }
}
