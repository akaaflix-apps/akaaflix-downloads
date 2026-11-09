import Foundation

enum AppLanguage: String, Codable, CaseIterable { case arabic = "ar", english = "en" }
enum LaunchScreen: String, Codable, CaseIterable { case home, live, movies, series, lastScreen }
enum AppearanceMode: String, Codable, CaseIterable { case dark, light, system }
enum SubtitleSize: String, Codable, CaseIterable { case small, medium, large, extraLarge }
enum SubtitleColor: String, Codable, CaseIterable { case white, yellow, green, cyan }
enum BufferMode: String, Codable, CaseIterable { case auto, low, high }
enum HardwareDecoding: String, Codable, CaseIterable { case auto, software }
enum PreferredPlayer: String, Codable, CaseIterable { case akaaPlayer, vlcApp, iina, quickTime }

/// Default playback quality for adaptive (HLS/DASH) sources. Progressive files have one quality.
enum PlaybackQuality: String, Codable, CaseIterable {
    case uhd4K, fhd1080, hd720, sd480, low360

    var maxHeight: Int {
        switch self {
        case .uhd4K: return 2160
        case .fhd1080: return 1080
        case .hd720: return 720
        case .sd480: return 480
        case .low360: return 360
        }
    }
    var label: String {
        switch self {
        case .uhd4K: return "4K Ultra HD"
        case .fhd1080: return "1080p FHD"
        case .hd720: return "720p HD"
        case .sd480: return "480p"
        case .low360: return "360p"
        }
    }
}

/// Every user preference. Note: there is deliberately **no** field for the live-stream order —
/// MPEG-TS then HLS is fixed by `LiveStreamPolicy` and cannot be changed by the user.
struct AppSettings: Codable, Equatable {
    // General
    var language: AppLanguage = .arabic
    var launchScreen: LaunchScreen = .home
    var lastScreen: LaunchScreen = .home
    var autoPlayNextEpisode = true
    var resumePlayback = true
    var nextEpisodeCountdown = 10

    // Display quality
    var defaultQuality: PlaybackQuality = .uhd4K
    var autoQuality = true

    // Audio & subtitles
    var preferredAudioLanguage = "ar"
    var preferredSubtitleLanguage = "ar"
    var subtitlesEnabled = true
    var subtitleSize: SubtitleSize = .medium
    var subtitleColor: SubtitleColor = .white
    var subtitleBackground = true
    var subtitleDelayMs = 0
    var subtitleEncoding = "UTF-8"

    // Player
    var preferredPlayer: PreferredPlayer = .akaaPlayer
    var showPlaybackInfo = true
    var enabledCompatibilityFormats: Set<LiveStreamFormat> = []
    var reconnectTimeoutSeconds = 10
    var bufferSeconds = 20
    var bufferMode: BufferMode = .auto
    var hardwareDecoding: HardwareDecoding = .auto
    var defaultVolume = 80
    var seekStepSeconds = 10

    // Appearance
    var appearance: AppearanceMode = .dark
    var reduceMotion = false
    var showClock = true

    // Parental control
    var parentalPINHash: String?
    var hideAdultContent = true
    var maxAgeRating = 0
    var lockSettings = false

    // Notifications & data
    var notifyNewContent = true
    var notifyExpiry = true
    var autoRefreshHours = 24
    var lastAutoRefresh: Date?

    init() {}

    // Decoding tolerates missing keys so older settings files keep working after updates.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let d = AppSettings()
        func v<T: Decodable>(_ key: CodingKeys, _ fallback: T) -> T { (try? c.decodeIfPresent(T.self, forKey: key)) ?? fallback }
        language = v(.language, d.language)
        launchScreen = v(.launchScreen, d.launchScreen)
        lastScreen = v(.lastScreen, d.lastScreen)
        autoPlayNextEpisode = v(.autoPlayNextEpisode, d.autoPlayNextEpisode)
        resumePlayback = v(.resumePlayback, d.resumePlayback)
        nextEpisodeCountdown = v(.nextEpisodeCountdown, d.nextEpisodeCountdown)
        defaultQuality = v(.defaultQuality, d.defaultQuality)
        autoQuality = v(.autoQuality, d.autoQuality)
        preferredAudioLanguage = v(.preferredAudioLanguage, d.preferredAudioLanguage)
        preferredSubtitleLanguage = v(.preferredSubtitleLanguage, d.preferredSubtitleLanguage)
        subtitlesEnabled = v(.subtitlesEnabled, d.subtitlesEnabled)
        subtitleSize = v(.subtitleSize, d.subtitleSize)
        subtitleColor = v(.subtitleColor, d.subtitleColor)
        subtitleBackground = v(.subtitleBackground, d.subtitleBackground)
        subtitleDelayMs = v(.subtitleDelayMs, d.subtitleDelayMs)
        subtitleEncoding = v(.subtitleEncoding, d.subtitleEncoding)
        preferredPlayer = v(.preferredPlayer, d.preferredPlayer)
        showPlaybackInfo = v(.showPlaybackInfo, d.showPlaybackInfo)
        enabledCompatibilityFormats = v(.enabledCompatibilityFormats, d.enabledCompatibilityFormats)
            .subtracting(LiveStreamPolicy.mandatoryOrder)
        reconnectTimeoutSeconds = min(60, max(3, v(.reconnectTimeoutSeconds, d.reconnectTimeoutSeconds)))
        bufferSeconds = min(120, max(1, v(.bufferSeconds, d.bufferSeconds)))
        bufferMode = v(.bufferMode, d.bufferMode)
        hardwareDecoding = v(.hardwareDecoding, d.hardwareDecoding)
        defaultVolume = min(100, max(0, v(.defaultVolume, d.defaultVolume)))
        seekStepSeconds = min(60, max(5, v(.seekStepSeconds, d.seekStepSeconds)))
        appearance = v(.appearance, d.appearance)
        reduceMotion = v(.reduceMotion, d.reduceMotion)
        showClock = v(.showClock, d.showClock)
        parentalPINHash = v(.parentalPINHash, d.parentalPINHash)
        hideAdultContent = v(.hideAdultContent, d.hideAdultContent)
        maxAgeRating = v(.maxAgeRating, d.maxAgeRating)
        lockSettings = v(.lockSettings, d.lockSettings)
        notifyNewContent = v(.notifyNewContent, d.notifyNewContent)
        notifyExpiry = v(.notifyExpiry, d.notifyExpiry)
        autoRefreshHours = v(.autoRefreshHours, d.autoRefreshHours)
        lastAutoRefresh = v(.lastAutoRefresh, d.lastAutoRefresh)
    }

    /// Network cache handed to the player (milliseconds).
    var networkCachingMs: Int {
        switch bufferMode {
        case .auto: return min(30_000, max(1_500, bufferSeconds * 1000 / 4))
        case .low: return 1_000
        case .high: return min(60_000, bufferSeconds * 1000)
        }
    }
}

/// Persists settings as JSON (atomic writes).
final class SettingsFile {
    let url: URL
    init(url: URL) { self.url = url }

    func load() -> AppSettings {
        guard let data = try? Data(contentsOf: url) else { return AppSettings() }
        return (try? JSONDecoder.akaa.decode(AppSettings.self, from: data)) ?? AppSettings()
    }

    func save(_ settings: AppSettings) throws {
        let data = try JSONEncoder.akaa.encode(settings)
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try data.write(to: url, options: .atomic)
    }
}

/// Resolves where data lives. Overridable (self-test / tests) with a custom root.
struct AppPaths {
    let root: URL

    static func standard() -> AppPaths {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSHomeDirectory()).appendingPathComponent("Library/Application Support")
        return AppPaths(root: base.appendingPathComponent("Akaa Flix", isDirectory: true))
    }

    var database: URL { root.appendingPathComponent("akaaflix.sqlite") }
    var settings: URL { root.appendingPathComponent("settings.json") }
    var imageCache: URL { root.appendingPathComponent("ImageCache", isDirectory: true) }
    var snapshots: URL {
        let pictures = FileManager.default.urls(for: .picturesDirectory, in: .userDomainMask).first ?? root
        return pictures.appendingPathComponent("Akaa Flix", isDirectory: true)
    }
    var downloads: URL {
        let movies = FileManager.default.urls(for: .moviesDirectory, in: .userDomainMask).first ?? root
        return movies.appendingPathComponent("Akaa Flix", isDirectory: true)
    }

    func prepare() throws {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        try FileManager.default.createDirectory(at: imageCache, withIntermediateDirectories: true)
    }
}

/// Stores playlist passwords outside the database (Keychain in the app, memory in tests).
protocol SecretStore: AnyObject {
    func password(for playlistID: Int64) -> String?
    func setPassword(_ password: String, for playlistID: Int64) throws
    func removePassword(for playlistID: Int64)
}

final class InMemorySecretStore: SecretStore {
    private var values: [Int64: String] = [:]
    private let lock = NSLock()
    func password(for playlistID: Int64) -> String? { lock.lock(); defer { lock.unlock() }; return values[playlistID] }
    func setPassword(_ password: String, for playlistID: Int64) throws { lock.lock(); values[playlistID] = password; lock.unlock() }
    func removePassword(for playlistID: Int64) { lock.lock(); values[playlistID] = nil; lock.unlock() }
}
