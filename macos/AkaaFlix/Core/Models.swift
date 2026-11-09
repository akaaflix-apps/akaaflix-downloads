import Foundation

// MARK: - Errors

enum AkaaError: Error, Equatable {
    case invalidURL
    case invalidCredentials
    case accountExpired
    case accountBanned
    case accountDisabled
    case notXtreamServer
    case server(Int)
    case timeout
    case offline
    case unreachable
    case tls
    case invalidResponse
    case notFound
    case noActivePlaylist
    case cancelled
    case storage(String)
    case playback(String)
    case unknown(String)

    static func from(_ error: Error) -> AkaaError {
        if let e = error as? AkaaError { return e }
        if error is CancellationError { return .cancelled }
        let ns = error as NSError
        guard ns.domain == NSURLErrorDomain else { return .unknown(ns.localizedDescription) }
        switch ns.code {
        case NSURLErrorCancelled: return .cancelled
        case NSURLErrorTimedOut: return .timeout
        case NSURLErrorNotConnectedToInternet, NSURLErrorDataNotAllowed, NSURLErrorInternationalRoamingOff: return .offline
        case NSURLErrorCannotFindHost, NSURLErrorCannotConnectToHost, NSURLErrorDNSLookupFailed,
             NSURLErrorNetworkConnectionLost, NSURLErrorResourceUnavailable: return .unreachable
        case NSURLErrorSecureConnectionFailed, NSURLErrorServerCertificateUntrusted, NSURLErrorServerCertificateHasBadDate,
             NSURLErrorServerCertificateNotYetValid, NSURLErrorServerCertificateHasUnknownRoot,
             NSURLErrorClientCertificateRejected, NSURLErrorClientCertificateRequired, NSURLErrorAppTransportSecurityRequiresSecureConnection:
            return .tls
        case NSURLErrorBadURL, NSURLErrorUnsupportedURL: return .invalidURL
        case NSURLErrorBadServerResponse, NSURLErrorCannotParseResponse, NSURLErrorCannotDecodeContentData,
             NSURLErrorCannotDecodeRawData, NSURLErrorZeroByteResource: return .invalidResponse
        default: return .unknown(ns.localizedDescription)
        }
    }
}

// MARK: - Content

enum ContentType: String, Codable, CaseIterable, Hashable {
    case live, movie, series, episode
}

struct XtreamCredentials: Equatable, Hashable {
    /// Normalised base URL ending with "/".
    var baseURL: String
    var username: String
    var password: String

    var base: String { baseURL.hasSuffix("/") ? baseURL : baseURL + "/" }
}

extension XtreamCredentials: CustomStringConvertible {
    /// Never prints the password.
    var description: String { "XtreamCredentials(host: \(ServerURL.displayHost(baseURL)), user: \(username))" }
}

enum AccountStatus: String, Codable {
    case active, expired, banned, disabled, trial, unknown

    static func parse(_ raw: String?) -> AccountStatus {
        switch (raw ?? "").trimmingCharacters(in: .whitespaces).lowercased() {
        case "active": return .active
        case "expired": return .expired
        case "banned": return .banned
        case "disabled": return .disabled
        case "trial": return .trial
        default: return .unknown
        }
    }
}

struct AccountInfo: Codable, Equatable {
    var status: AccountStatus
    var rawStatus: String?
    var expiresAt: Date?
    var createdAt: Date?
    var isTrial: Bool
    var activeConnections: Int?
    var maxConnections: Int?
    var allowedOutputFormats: [String]
    var message: String?
    var serverTimezone: String?
    var serverProtocol: String?
    var rtmpPort: String?
    var httpsPort: String?
    var serverTimeNow: Date?
    var resolvedBaseURL: String

    func remainingDays(now: Date = Date()) -> Int? {
        guard let exp = expiresAt else { return nil }
        return max(0, Int(ceil(exp.timeIntervalSince(now) / 86_400)))
    }
}

struct ContentCategory: Identifiable, Hashable, Codable {
    var id: String
    var name: String
    var type: ContentType
    var sortIndex: Int
    var count: Int = 0

    var kind: CategoryKind { CategoryClassifier.classify(name) }
    var isAdult: Bool { CategoryClassifier.isAdult(name) }
}

struct Channel: Identifiable, Hashable, Codable {
    var id: Int            // stream_id
    var number: Int
    var name: String
    var icon: String?
    var epgChannelID: String?
    var categoryID: String?
    var added: Date?
    var isAdult: Bool
    var hasArchive: Bool

    var quality: String? { ContentText.qualityOf(name) }
}

struct Movie: Identifiable, Hashable, Codable {
    var id: Int            // stream_id
    var number: Int
    var name: String
    var poster: String?
    var rating: Double?
    var added: Date?
    var categoryID: String?
    var containerExtension: String?
    var year: Int?
    var genre: String?
    var plot: String?
    var isAdult: Bool

    var quality: String? { ContentText.qualityOf(name) }
    var isHDR: Bool { ContentText.isHDR(name) }
}

struct Series: Identifiable, Hashable, Codable {
    var id: Int            // series_id
    var number: Int
    var name: String
    var cover: String?
    var plot: String?
    var cast: String?
    var director: String?
    var genre: String?
    var releaseDate: String?
    var lastModified: Date?
    var rating: Double?
    var backdrop: String?
    var episodeRunTime: String?
    var categoryID: String?
    var year: Int?
    var isAdult: Bool

    var quality: String? { ContentText.qualityOf(name) }
}

struct MovieDetails: Hashable {
    var movie: Movie
    var originalName: String?
    var plot: String?
    var genre: String?
    var cast: String?
    var director: String?
    var releaseDate: String?
    var durationSeconds: Int?
    var backdrop: String?
    var ageRating: String?
    var country: String?
    var trailer: String?
    var videoResolution: String?
    var audioLanguages: [String] = []

    var castList: [String] { ContentText.splitList(cast) }
    var genres: [String] { ContentText.splitList(genre ?? movie.genre) }
}

struct Season: Identifiable, Hashable {
    var number: Int
    var name: String
    var cover: String?
    var episodeCount: Int
    var overview: String?
    var id: Int { number }
}

struct Episode: Identifiable, Hashable, Codable {
    var id: String          // episode stream id
    var seriesID: Int
    var season: Int
    var episodeNumber: Int
    var title: String
    var plot: String?
    var thumbnail: String?
    var durationSeconds: Int?
    var containerExtension: String?
    var rating: Double?
    var releaseDate: String?
}

struct SeriesDetails: Hashable {
    var series: Series
    var originalName: String?
    var cast: String?
    var director: String?
    var releaseDate: String?
    var ageRating: String?
    var country: String?
    var language: String?
    var seasons: [Season]
    var episodesBySeason: [Int: [Episode]]

    var allEpisodes: [Episode] {
        episodesBySeason.keys.sorted().flatMap { episodesBySeason[$0] ?? [] }
    }
    var episodeCount: Int { episodesBySeason.values.reduce(0) { $0 + $1.count } }
    var genres: [String] { ContentText.splitList(series.genre) }
    var castList: [String] { ContentText.splitList(cast ?? series.cast) }
}

struct EPGEntry: Identifiable, Hashable {
    var id: String { "\(start.timeIntervalSince1970)-\(title)" }
    var title: String
    var description: String?
    var start: Date
    var end: Date

    func isLive(at now: Date = Date()) -> Bool { start <= now && now < end }
    func progress(at now: Date = Date()) -> Double {
        let total = end.timeIntervalSince(start)
        guard total > 0 else { return 0 }
        return min(1, max(0, now.timeIntervalSince(start) / total))
    }
}

// MARK: - Playlists

enum PlaylistConnectionStatus: String, Codable {
    case unknown, connected, failed, expired
}

struct Playlist: Identifiable, Hashable, Codable {
    var id: Int64
    var name: String
    var serverURL: String
    var username: String
    var createdAt: Date
    var updatedAt: Date
    var lastSync: Date?
    var isEnabled: Bool
    var isDefault: Bool
    var sortOrder: Int
    var status: PlaylistConnectionStatus
    var liveCount: Int
    var movieCount: Int
    var seriesCount: Int
    var account: AccountInfo?

    var displayHost: String { ServerURL.displayHost(serverURL) }
}

/// Fields entered by the user when adding or editing a playlist (exactly four).
struct PlaylistInput: Equatable {
    var name: String
    var username: String
    var password: String
    var url: String
}

enum PlaylistValidationIssue: Equatable {
    case nameMissing, usernameMissing, passwordMissing, urlMissing, urlInvalid, duplicate
}

enum PlaylistValidator {
    static func validate(_ input: PlaylistInput, existing: [Playlist], editingID: Int64? = nil, passwordOptional: Bool = false) -> [PlaylistValidationIssue] {
        var issues: [PlaylistValidationIssue] = []
        if input.name.trimmingCharacters(in: .whitespaces).isEmpty { issues.append(.nameMissing) }
        if input.username.trimmingCharacters(in: .whitespaces).isEmpty { issues.append(.usernameMissing) }
        if input.password.isEmpty && !passwordOptional { issues.append(.passwordMissing) }
        let url = input.url.trimmingCharacters(in: .whitespaces)
        if url.isEmpty {
            issues.append(.urlMissing)
        } else if let normalized = ServerURL.normalize(url) {
            let host = ServerURL.displayHost(normalized.baseURL)
            let user = input.username.trimmingCharacters(in: .whitespaces)
            if existing.contains(where: { $0.id != editingID && $0.username == user && ServerURL.displayHost($0.serverURL) == host }) {
                issues.append(.duplicate)
            }
        } else {
            issues.append(.urlInvalid)
        }
        return issues
    }
}

// MARK: - User data

struct WatchProgress: Hashable, Codable {
    var type: ContentType
    var itemID: String
    var title: String
    var image: String?
    var position: Double
    var duration: Double
    var updatedAt: Date
    var seriesID: Int?
    var season: Int?
    var episodeNumber: Int?
    var containerExtension: String?
    var playCount: Int

    var fraction: Double { duration > 0 ? min(1, max(0, position / duration)) : 0 }
    var isFinished: Bool { ContinueWatchingRules.isFinished(position: position, duration: duration) }
}

enum ContinueWatchingRules {
    /// Positions below this are not worth resuming.
    static let minimumResumeSeconds: Double = 30
    /// Considered watched when at least 95% has been played or less than 2 minutes remain.
    static func isFinished(position: Double, duration: Double) -> Bool {
        guard duration > 0 else { return false }
        return position / duration >= 0.95 || (duration - position) < 120 && duration > 600
    }
    static func resumePosition(position: Double, duration: Double) -> Double? {
        guard position >= minimumResumeSeconds, !isFinished(position: position, duration: duration) else { return nil }
        return position
    }
}

struct CustomGroup: Identifiable, Hashable, Codable {
    var id: Int64
    var name: String
    var createdAt: Date
}

struct AppNotification: Identifiable, Hashable, Codable {
    var id: Int64
    var title: String
    var body: String
    var createdAt: Date
    var isRead: Bool
}

struct FavoriteRef: Hashable, Codable {
    var type: ContentType
    var itemID: String
}

enum SortOption: String, Codable, CaseIterable {
    case providerOrder, recentlyAdded, name, rating, year
}
