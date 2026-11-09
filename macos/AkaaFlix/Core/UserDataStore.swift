import Foundation

struct DownloadRecord: Identifiable, Hashable {
    enum Status: String { case queued, downloading, paused, completed, failed }
    var id: Int64
    var type: ContentType
    var itemID: String
    var title: String
    var image: String?
    var filePath: String?
    var status: Status
    var progress: Double
    var totalBytes: Int64
    var createdAt: Date
    var containerExtension: String?
    var seriesID: Int?
    var season: Int?
    var episodeNumber: Int?
}

/// Favorites, watch progress, custom groups, hidden/locked content, notifications and downloads.
final class UserDataStore {
    private let db: SQLiteDatabase
    init(db: SQLiteDatabase) { self.db = db }

    // MARK: Favorites

    func isFavorite(_ pid: Int64, _ type: ContentType, _ id: String) -> Bool {
        ((try? db.scalarInt("SELECT COUNT(*) FROM favorites WHERE playlist_id=? AND type=? AND item_id=?", [.int(pid), .text(type.rawValue), .text(id)])) ?? 0) > 0
    }

    /// Toggles and returns the new state.
    @discardableResult
    func toggleFavorite(_ pid: Int64, _ type: ContentType, _ id: String) throws -> Bool {
        if isFavorite(pid, type, id) {
            try db.run("DELETE FROM favorites WHERE playlist_id=? AND type=? AND item_id=?", [.int(pid), .text(type.rawValue), .text(id)])
            return false
        }
        try db.run("INSERT OR REPLACE INTO favorites(playlist_id, type, item_id, added_at) VALUES(?,?,?,?)", [.int(pid), .text(type.rawValue), .text(id), SQLValue(Date())])
        return true
    }

    func favoriteIDs(_ pid: Int64, _ type: ContentType) throws -> [String] {
        try db.query("SELECT item_id FROM favorites WHERE playlist_id=? AND type=? ORDER BY added_at DESC", [.int(pid), .text(type.rawValue)]) { $0.string(0) }
    }

    func allFavorites(_ pid: Int64) throws -> [FavoriteRef] {
        try db.query("SELECT type, item_id FROM favorites WHERE playlist_id=? ORDER BY added_at DESC", [.int(pid)]) {
            FavoriteRef(type: ContentType(rawValue: $0.string(0)) ?? .movie, itemID: $0.string(1))
        }
    }

    // MARK: Progress / history

    func saveProgress(_ pid: Int64, _ p: WatchProgress, countPlay: Bool = false) throws {
        try db.run("""
            INSERT INTO progress(playlist_id, type, item_id, title, image, position, duration, updated_at, series_id, season, episode_num, ext, play_count)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(playlist_id, type, item_id) DO UPDATE SET title=excluded.title, image=COALESCE(excluded.image, progress.image),
              position=excluded.position, duration=CASE WHEN excluded.duration > 0 THEN excluded.duration ELSE progress.duration END,
              updated_at=excluded.updated_at, series_id=excluded.series_id, season=excluded.season, episode_num=excluded.episode_num,
              ext=COALESCE(excluded.ext, progress.ext), play_count=progress.play_count + ?
            """, [.int(pid), .text(p.type.rawValue), .text(p.itemID), .text(p.title), SQLValue(p.image), .double(p.position), .double(p.duration),
                  SQLValue(p.updatedAt), SQLValue(p.seriesID), SQLValue(p.season), SQLValue(p.episodeNumber), SQLValue(p.containerExtension),
                  .int(countPlay ? 1 : 0), .int(countPlay ? 1 : 0)])
    }

    private static let progressColumns = "type, item_id, title, image, position, duration, updated_at, series_id, season, episode_num, ext, play_count"

    private static func mapProgress(_ r: SQLRow) -> WatchProgress {
        WatchProgress(type: ContentType(rawValue: r.string(0)) ?? .movie, itemID: r.string(1), title: r.string(2), image: r.optString(3),
                      position: r.double(4), duration: r.double(5), updatedAt: r.date(6) ?? Date(), seriesID: r.optInt(7),
                      season: r.optInt(8), episodeNumber: r.optInt(9), containerExtension: r.optString(10), playCount: r.int(11))
    }

    func progress(_ pid: Int64, _ type: ContentType, _ id: String) -> WatchProgress? {
        (try? db.query("SELECT \(UserDataStore.progressColumns) FROM progress WHERE playlist_id=? AND type=? AND item_id=?",
                       [.int(pid), .text(type.rawValue), .text(id)], UserDataStore.mapProgress))?.first
    }

    func progressForSeries(_ pid: Int64, seriesID: Int) throws -> [WatchProgress] {
        try db.query("SELECT \(UserDataStore.progressColumns) FROM progress WHERE playlist_id=? AND type='episode' AND series_id=? ORDER BY updated_at DESC",
                     [.int(pid), .int(Int64(seriesID))], UserDataStore.mapProgress)
    }

    /// Unfinished movies and episodes (latest episode per series), newest first.
    func continueWatching(_ pid: Int64, limit: Int = 20) throws -> [WatchProgress] {
        let rows = try db.query("SELECT \(UserDataStore.progressColumns) FROM progress WHERE playlist_id=? AND type IN ('movie','episode') AND position > 0 ORDER BY updated_at DESC LIMIT 200",
                                [.int(pid)], UserDataStore.mapProgress)
        var seenSeries = Set<Int>()
        var out: [WatchProgress] = []
        for p in rows {
            if p.type == .episode, let s = p.seriesID {
                if !seenSeries.insert(s).inserted { continue }
            }
            if ContinueWatchingRules.resumePosition(position: p.position, duration: p.duration) != nil { out.append(p) }
            if out.count >= limit { break }
        }
        return out
    }

    func history(_ pid: Int64, limit: Int = 100) throws -> [WatchProgress] {
        try db.query("SELECT \(UserDataStore.progressColumns) FROM progress WHERE playlist_id=? ORDER BY updated_at DESC LIMIT ?", [.int(pid), .int(Int64(limit))], UserDataStore.mapProgress)
    }

    /// Item IDs ordered by local play count ("Most watched" — Xtream exposes no popularity data).
    func mostWatchedIDs(_ pid: Int64, _ type: ContentType, limit: Int = 50) throws -> [String] {
        try db.query("SELECT item_id FROM progress WHERE playlist_id=? AND type=? AND play_count > 0 ORDER BY play_count DESC, updated_at DESC LIMIT ?",
                     [.int(pid), .text(type.rawValue), .int(Int64(limit))]) { $0.string(0) }
    }

    func mostWatchedSeriesIDs(_ pid: Int64, limit: Int = 50) throws -> [String] {
        try db.query("SELECT CAST(series_id AS TEXT) FROM progress WHERE playlist_id=? AND type='episode' AND series_id IS NOT NULL GROUP BY series_id ORDER BY SUM(play_count) DESC, MAX(updated_at) DESC LIMIT ?",
                     [.int(pid), .int(Int64(limit))]) { $0.string(0) }
    }

    func removeProgress(_ pid: Int64, _ type: ContentType, _ id: String) throws {
        try db.run("DELETE FROM progress WHERE playlist_id=? AND type=? AND item_id=?", [.int(pid), .text(type.rawValue), .text(id)])
    }

    func clearHistory(_ pid: Int64) throws {
        try db.run("DELETE FROM progress WHERE playlist_id=?", [.int(pid)])
    }

    // MARK: Custom groups

    func groups(_ pid: Int64) throws -> [CustomGroup] {
        try db.query("SELECT id, name, created_at FROM custom_groups WHERE playlist_id=? ORDER BY created_at", [.int(pid)]) {
            CustomGroup(id: $0.int64(0), name: $0.string(1), createdAt: $0.date(2) ?? Date())
        }
    }

    @discardableResult
    func createGroup(_ pid: Int64, name: String) throws -> CustomGroup {
        try db.run("INSERT INTO custom_groups(playlist_id, name, created_at) VALUES(?,?,?)", [.int(pid), .text(name), SQLValue(Date())])
        return CustomGroup(id: db.lastInsertRowID, name: name, createdAt: Date())
    }

    func renameGroup(_ id: Int64, name: String) throws {
        try db.run("UPDATE custom_groups SET name=? WHERE id=?", [.text(name), .int(id)])
    }

    func deleteGroup(_ id: Int64) throws {
        try db.run("DELETE FROM group_items WHERE group_id=?", [.int(id)])
        try db.run("DELETE FROM custom_groups WHERE id=?", [.int(id)])
    }

    func setInGroup(_ groupID: Int64, _ type: ContentType, _ itemID: String, _ member: Bool) throws {
        if member {
            try db.run("INSERT OR IGNORE INTO group_items(group_id, type, item_id) VALUES(?,?,?)", [.int(groupID), .text(type.rawValue), .text(itemID)])
        } else {
            try db.run("DELETE FROM group_items WHERE group_id=? AND type=? AND item_id=?", [.int(groupID), .text(type.rawValue), .text(itemID)])
        }
    }

    func groupItemIDs(_ groupID: Int64, _ type: ContentType) throws -> [String] {
        try db.query("SELECT item_id FROM group_items WHERE group_id=? AND type=?", [.int(groupID), .text(type.rawValue)]) { $0.string(0) }
    }

    func groupsContaining(_ pid: Int64, _ type: ContentType, _ itemID: String) throws -> Set<Int64> {
        Set(try db.query("SELECT gi.group_id FROM group_items gi JOIN custom_groups g ON g.id = gi.group_id WHERE g.playlist_id=? AND gi.type=? AND gi.item_id=?",
                         [.int(pid), .text(type.rawValue), .text(itemID)]) { $0.int64(0) })
    }

    // MARK: Hidden & locked content

    func hiddenContent(_ pid: Int64) throws -> HiddenContent {
        var h = HiddenContent()
        for (kind, value) in try db.query("SELECT kind, value FROM hidden WHERE playlist_id=?", [.int(pid)], { ($0.string(0), $0.string(1)) }) {
            switch kind {
            case "category": h.categoryIDs.insert(value)
            case "item": h.itemIDs.insert(value)
            case "keyword": h.keywords.append(value)
            case "section": if let t = ContentType(rawValue: value) { h.hiddenSections.insert(t) }
            default: break
            }
        }
        return h
    }

    /// kind: "category" (value = category id), "item" (value = "type:id"), "keyword", "section".
    func setHidden(_ pid: Int64, kind: String, value: String, hidden: Bool) throws {
        if hidden {
            try db.run("INSERT OR IGNORE INTO hidden(playlist_id, kind, value) VALUES(?,?,?)", [.int(pid), .text(kind), .text(value)])
        } else {
            try db.run("DELETE FROM hidden WHERE playlist_id=? AND kind=? AND value=?", [.int(pid), .text(kind), .text(value)])
        }
    }

    func clearHidden(_ pid: Int64) throws {
        try db.run("DELETE FROM hidden WHERE playlist_id=?", [.int(pid)])
    }

    /// Parental locks. kind: "category" or "item" (value "type:id").
    func locks(_ pid: Int64) throws -> Set<String> {
        Set(try db.query("SELECT kind || '|' || value FROM locks WHERE playlist_id=?", [.int(pid)]) { $0.string(0) })
    }

    func setLocked(_ pid: Int64, kind: String, value: String, locked: Bool) throws {
        if locked {
            try db.run("INSERT OR IGNORE INTO locks(playlist_id, kind, value) VALUES(?,?,?)", [.int(pid), .text(kind), .text(value)])
        } else {
            try db.run("DELETE FROM locks WHERE playlist_id=? AND kind=? AND value=?", [.int(pid), .text(kind), .text(value)])
        }
    }

    // MARK: Notifications

    func addNotification(_ pid: Int64?, title: String, body: String) throws {
        try db.run("INSERT INTO notifications(playlist_id, title, body, created_at, is_read) VALUES(?,?,?,?,0)", [SQLValue(pid), .text(title), .text(body), SQLValue(Date())])
    }

    func notifications(limit: Int = 100) throws -> [AppNotification] {
        try db.query("SELECT id, title, body, created_at, is_read FROM notifications ORDER BY created_at DESC LIMIT ?", [.int(Int64(limit))]) {
            AppNotification(id: $0.int64(0), title: $0.string(1), body: $0.string(2), createdAt: $0.date(3) ?? Date(), isRead: $0.bool(4))
        }
    }

    func unreadNotificationCount() -> Int {
        (try? db.scalarInt("SELECT COUNT(*) FROM notifications WHERE is_read = 0")) ?? 0
    }

    func markNotificationsRead() throws { try db.run("UPDATE notifications SET is_read = 1") }
    func clearNotifications() throws { try db.run("DELETE FROM notifications") }

    // MARK: Downloads

    private static let downloadColumns = "id, type, item_id, title, image, file_path, status, progress, total_bytes, created_at, ext, series_id, season, episode_num"

    private static func mapDownload(_ r: SQLRow) -> DownloadRecord {
        DownloadRecord(id: r.int64(0), type: ContentType(rawValue: r.string(1)) ?? .movie, itemID: r.string(2), title: r.string(3),
                       image: r.optString(4), filePath: r.optString(5), status: DownloadRecord.Status(rawValue: r.string(6)) ?? .failed,
                       progress: r.double(7), totalBytes: r.int64(8), createdAt: r.date(9) ?? Date(), containerExtension: r.optString(10),
                       seriesID: r.optInt(11), season: r.optInt(12), episodeNumber: r.optInt(13))
    }

    func downloads(_ pid: Int64) throws -> [DownloadRecord] {
        try db.query("SELECT \(UserDataStore.downloadColumns) FROM downloads WHERE playlist_id=? ORDER BY created_at DESC", [.int(pid)], UserDataStore.mapDownload)
    }

    func download(_ id: Int64) -> DownloadRecord? {
        (try? db.query("SELECT \(UserDataStore.downloadColumns) FROM downloads WHERE id=?", [.int(id)], UserDataStore.mapDownload))?.first
    }

    func findDownload(_ pid: Int64, _ type: ContentType, _ itemID: String) -> DownloadRecord? {
        (try? db.query("SELECT \(UserDataStore.downloadColumns) FROM downloads WHERE playlist_id=? AND type=? AND item_id=?", [.int(pid), .text(type.rawValue), .text(itemID)], UserDataStore.mapDownload))?.first
    }

    @discardableResult
    func addDownload(_ pid: Int64, type: ContentType, itemID: String, title: String, image: String?, ext: String?, seriesID: Int?, season: Int?, episode: Int?) throws -> Int64 {
        try db.run("""
            INSERT INTO downloads(playlist_id, type, item_id, title, image, status, progress, total_bytes, created_at, ext, series_id, season, episode_num)
            VALUES(?,?,?,?,?,'queued',0,0,?,?,?,?,?)
            """, [.int(pid), .text(type.rawValue), .text(itemID), .text(title), SQLValue(image), SQLValue(Date()), SQLValue(ext),
                  SQLValue(seriesID), SQLValue(season), SQLValue(episode)])
        return db.lastInsertRowID
    }

    func updateDownload(_ id: Int64, status: DownloadRecord.Status, progress: Double, totalBytes: Int64? = nil, filePath: String? = nil) throws {
        try db.run("UPDATE downloads SET status=?, progress=?, total_bytes=COALESCE(?, total_bytes), file_path=COALESCE(?, file_path) WHERE id=?",
                   [.text(status.rawValue), .double(progress), SQLValue(totalBytes), SQLValue(filePath), .int(id)])
    }

    func deleteDownload(_ id: Int64) throws {
        try db.run("DELETE FROM downloads WHERE id=?", [.int(id)])
    }
}
