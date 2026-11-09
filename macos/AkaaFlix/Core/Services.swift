import Foundation

// MARK: - Next episode

/// Resolves the episode that follows (or precedes) the current one, crossing season boundaries.
enum EpisodeNavigator {
    static func next(after current: Episode, in details: SeriesDetails) -> Episode? {
        neighbour(of: current, in: details, offset: 1)
    }

    static func previous(before current: Episode, in details: SeriesDetails) -> Episode? {
        neighbour(of: current, in: details, offset: -1)
    }

    private static func neighbour(of current: Episode, in details: SeriesDetails, offset: Int) -> Episode? {
        let all = details.allEpisodes
        guard let index = all.firstIndex(where: { $0.id == current.id }) else { return nil }
        let target = index + offset
        return all.indices.contains(target) ? all[target] : nil
    }

    /// Episode to start when the user presses "Continue watching" on a series.
    static func resumeTarget(details: SeriesDetails, progress: [WatchProgress]) -> (Episode, Double?)? {
        let all = details.allEpisodes
        guard !all.isEmpty else { return nil }
        guard let latest = progress.max(by: { $0.updatedAt < $1.updatedAt }),
              let episode = all.first(where: { $0.id == latest.itemID }) else { return (all[0], nil) }
        if latest.isFinished {
            if let next = next(after: episode, in: details) { return (next, nil) }
            return (episode, nil)
        }
        return (episode, ContinueWatchingRules.resumePosition(position: latest.position, duration: latest.duration))
    }

    /// Watched episodes count (finished or with meaningful progress) for the "12 of 132" banner.
    static func watchedCount(details: SeriesDetails, progress: [WatchProgress]) -> Int {
        let ids = Set(details.allEpisodes.map { $0.id })
        return Set(progress.filter { ids.contains($0.itemID) && ($0.isFinished || $0.position >= 60) }.map { $0.itemID }).count
    }
}

// MARK: - Parental rules

struct ParentalContext {
    var pinHash: String?
    var maxAge: Int
    var locks: Set<String>
    var unlockedForSession: Bool

    var isEnabled: Bool { pinHash != nil }

    func requiresPIN(type: ContentType, itemID: String, categoryID: String?, ageRating: String? = nil) -> Bool {
        guard isEnabled, !unlockedForSession else { return false }
        if locks.contains("item|\(type.rawValue):\(itemID)") { return true }
        if let c = categoryID, locks.contains("category|\(c)") { return true }
        if AgeRating.isRestricted(ageRating, maxAllowedAge: maxAge) { return true }
        return false
    }
}

// MARK: - Catalog sync

/// Downloads all three catalog sections and stores them. A failing section keeps its previous
/// content and is reported, so a flaky panel never wipes the user's library.
final class CatalogSyncer {
    let client: XtreamClient
    let database: AppDatabase

    init(client: XtreamClient, database: AppDatabase) {
        self.client = client
        self.database = database
    }

    func sync(playlist: Playlist, credentials: XtreamCredentials, progress: ((String) -> Void)? = nil) async throws -> SyncSummary {
        var categories: [ContentType: [ContentCategory]] = [:]
        var failed: [ContentType] = []
        var firstError: Error?

        progress?("live")
        async let liveCats = try? client.categories(credentials, type: .live)
        async let vodCats = try? client.categories(credentials, type: .movie)
        async let seriesCats = try? client.categories(credentials, type: .series)

        var channels: [Channel]?
        do { channels = try await client.liveStreams(credentials) } catch { failed.append(.live); firstError = firstError ?? error }
        progress?("movies")
        var movies: [Movie]?
        do { movies = try await client.vodStreams(credentials) } catch { failed.append(.movie); firstError = firstError ?? error }
        progress?("series")
        var series: [Series]?
        do { series = try await client.series(credentials) } catch { failed.append(.series); firstError = firstError ?? error }

        if let c = await liveCats { categories[.live] = c }
        if let c = await vodCats { categories[.movie] = c }
        if let c = await seriesCats { categories[.series] = c }

        if failed.count == 3, let error = firstError { throw AkaaError.from(error) }
        try Task.checkCancellation()

        var summary = try database.catalog.replace(playlistID: playlist.id, categories: categories, channels: channels, movies: movies, series: series)
        summary.failedSections = failed

        var updated = try database.playlists.get(playlist.id) ?? playlist
        updated.liveCount = summary.counts.live
        updated.movieCount = summary.counts.movies
        updated.seriesCount = summary.counts.series
        updated.lastSync = Date()
        try database.playlists.update(updated)
        return summary
    }
}

// MARK: - Backup & restore

/// JSON backup of playlists (credentials only when explicitly included), favorites, groups,
/// hidden content and settings. Never includes the parental PIN hash.
struct BackupDocument: Codable {
    struct PlaylistEntry: Codable {
        var name: String
        var serverURL: String
        var username: String
        var password: String?
        var isDefault: Bool
        var favorites: [FavoriteRef]
        var groups: [GroupEntry]
        var hidden: HiddenContent
    }
    struct GroupEntry: Codable {
        var name: String
        var items: [FavoriteRef]
    }

    var format = "akaaflix-backup"
    var version = 1
    var appVersion = AppInfo.version
    var platform = "macOS"
    var createdAt = Date()
    var playlists: [PlaylistEntry]
    var settings: AppSettings
}

enum BackupManager {

    static func export(database: AppDatabase, secrets: SecretStore, settings: AppSettings, includeCredentials: Bool) throws -> Data {
        var entries: [BackupDocument.PlaylistEntry] = []
        for p in try database.playlists.all() {
            let groups = try database.userData.groups(p.id).map { g -> BackupDocument.GroupEntry in
                var items: [FavoriteRef] = []
                for type in [ContentType.live, .movie, .series] {
                    items += try database.userData.groupItemIDs(g.id, type).map { FavoriteRef(type: type, itemID: $0) }
                }
                return BackupDocument.GroupEntry(name: g.name, items: items)
            }
            entries.append(BackupDocument.PlaylistEntry(
                name: p.name, serverURL: p.serverURL, username: p.username,
                password: includeCredentials ? secrets.password(for: p.id) : nil,
                isDefault: p.isDefault,
                favorites: try database.userData.allFavorites(p.id),
                groups: groups,
                hidden: try database.userData.hiddenContent(p.id)))
        }
        var s = settings
        s.parentalPINHash = nil
        let doc = BackupDocument(playlists: entries, settings: s)
        let encoder = JSONEncoder.akaa
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(doc)
    }

    struct RestoreResult { var playlistsAdded: Int; var playlistsMerged: Int; var settings: AppSettings }

    /// Restores into the database. Existing playlists (same host + username) are merged.
    static func restore(_ data: Data, database: AppDatabase, secrets: SecretStore, currentSettings: AppSettings, restoreSettings: Bool) throws -> RestoreResult {
        let doc: BackupDocument
        do { doc = try JSONDecoder.akaa.decode(BackupDocument.self, from: data) } catch { throw AkaaError.invalidResponse }
        guard doc.format == "akaaflix-backup" else { throw AkaaError.invalidResponse }
        var added = 0, merged = 0
        for entry in doc.playlists {
            guard let normalized = ServerURL.normalize(entry.serverURL) else { continue }
            let existing = try database.playlists.all().first {
                $0.username == entry.username && ServerURL.displayHost($0.serverURL) == ServerURL.displayHost(normalized.baseURL)
            }
            let playlist: Playlist
            if let e = existing {
                playlist = e
                merged += 1
            } else {
                playlist = try database.playlists.insert(name: entry.name, serverURL: normalized.baseURL, username: entry.username, account: nil, status: .unknown)
                added += 1
            }
            if let pw = entry.password, !pw.isEmpty { try secrets.setPassword(pw, for: playlist.id) }
            if entry.isDefault { try database.playlists.setDefault(playlist.id) }
            for f in entry.favorites where !database.userData.isFavorite(playlist.id, f.type, f.itemID) {
                try database.userData.toggleFavorite(playlist.id, f.type, f.itemID)
            }
            let existingGroups = try database.userData.groups(playlist.id)
            for g in entry.groups {
                let group = try existingGroups.first(where: { $0.name == g.name }) ?? database.userData.createGroup(playlist.id, name: g.name)
                for item in g.items { try database.userData.setInGroup(group.id, item.type, item.itemID, true) }
            }
            for c in entry.hidden.categoryIDs { try database.userData.setHidden(playlist.id, kind: "category", value: c, hidden: true) }
            for i in entry.hidden.itemIDs { try database.userData.setHidden(playlist.id, kind: "item", value: i, hidden: true) }
            for k in entry.hidden.keywords { try database.userData.setHidden(playlist.id, kind: "keyword", value: k, hidden: true) }
            for s in entry.hidden.hiddenSections { try database.userData.setHidden(playlist.id, kind: "section", value: s.rawValue, hidden: true) }
        }
        var settings = currentSettings
        if restoreSettings {
            settings = doc.settings
            settings.parentalPINHash = currentSettings.parentalPINHash
        }
        return RestoreResult(playlistsAdded: added, playlistsMerged: merged, settings: settings)
    }
}
