import Foundation

/// Opens the app database and applies schema migrations (tracked with `PRAGMA user_version`).
final class AppDatabase {

    static let schemaVersion = 1

    let db: SQLiteDatabase
    let playlists: PlaylistStore
    let catalog: CatalogStore
    let userData: UserDataStore

    init(path: String) throws {
        db = try SQLiteDatabase(path: path)
        try AppDatabase.migrate(db)
        playlists = PlaylistStore(db: db)
        catalog = CatalogStore(db: db)
        userData = UserDataStore(db: db)
    }

    /// In-memory database for tests and the self-test harness.
    static func inMemory() throws -> AppDatabase { try AppDatabase(path: ":memory:") }

    static func migrate(_ db: SQLiteDatabase) throws {
        let current = db.userVersion
        if current < 1 {
            try db.execute("""
            CREATE TABLE IF NOT EXISTS playlists(
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL, server_url TEXT NOT NULL, username TEXT NOT NULL,
              created_at REAL NOT NULL, updated_at REAL NOT NULL, last_sync REAL,
              enabled INTEGER NOT NULL DEFAULT 1, is_default INTEGER NOT NULL DEFAULT 0,
              sort_order INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'unknown',
              live_count INTEGER NOT NULL DEFAULT 0, movie_count INTEGER NOT NULL DEFAULT 0,
              series_count INTEGER NOT NULL DEFAULT 0, account_json TEXT);
            CREATE TABLE IF NOT EXISTS categories(
              playlist_id INTEGER NOT NULL, type TEXT NOT NULL, category_id TEXT NOT NULL,
              name TEXT NOT NULL, sort INTEGER NOT NULL, PRIMARY KEY(playlist_id, type, category_id));
            CREATE TABLE IF NOT EXISTS channels(
              playlist_id INTEGER NOT NULL, stream_id INTEGER NOT NULL, num INTEGER NOT NULL,
              name TEXT NOT NULL, name_norm TEXT NOT NULL, icon TEXT, epg_id TEXT, category_id TEXT,
              added REAL, is_adult INTEGER NOT NULL DEFAULT 0, archive INTEGER NOT NULL DEFAULT 0, quality TEXT,
              PRIMARY KEY(playlist_id, stream_id));
            CREATE INDEX IF NOT EXISTS idx_channels_cat ON channels(playlist_id, category_id);
            CREATE TABLE IF NOT EXISTS movies(
              playlist_id INTEGER NOT NULL, stream_id INTEGER NOT NULL, num INTEGER NOT NULL,
              name TEXT NOT NULL, name_norm TEXT NOT NULL, poster TEXT, rating REAL, added REAL,
              category_id TEXT, ext TEXT, year INTEGER, genre TEXT, plot TEXT,
              is_adult INTEGER NOT NULL DEFAULT 0, quality TEXT, PRIMARY KEY(playlist_id, stream_id));
            CREATE INDEX IF NOT EXISTS idx_movies_cat ON movies(playlist_id, category_id);
            CREATE INDEX IF NOT EXISTS idx_movies_added ON movies(playlist_id, added);
            CREATE TABLE IF NOT EXISTS series(
              playlist_id INTEGER NOT NULL, series_id INTEGER NOT NULL, num INTEGER NOT NULL,
              name TEXT NOT NULL, name_norm TEXT NOT NULL, cover TEXT, plot TEXT, cast_names TEXT,
              director TEXT, genre TEXT, release_date TEXT, last_modified REAL, rating REAL, backdrop TEXT,
              run_time TEXT, category_id TEXT, year INTEGER, is_adult INTEGER NOT NULL DEFAULT 0, quality TEXT,
              PRIMARY KEY(playlist_id, series_id));
            CREATE INDEX IF NOT EXISTS idx_series_cat ON series(playlist_id, category_id);
            CREATE TABLE IF NOT EXISTS favorites(
              playlist_id INTEGER NOT NULL, type TEXT NOT NULL, item_id TEXT NOT NULL, added_at REAL NOT NULL,
              PRIMARY KEY(playlist_id, type, item_id));
            CREATE TABLE IF NOT EXISTS progress(
              playlist_id INTEGER NOT NULL, type TEXT NOT NULL, item_id TEXT NOT NULL, title TEXT NOT NULL,
              image TEXT, position REAL NOT NULL DEFAULT 0, duration REAL NOT NULL DEFAULT 0, updated_at REAL NOT NULL,
              series_id INTEGER, season INTEGER, episode_num INTEGER, ext TEXT, play_count INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY(playlist_id, type, item_id));
            CREATE INDEX IF NOT EXISTS idx_progress_updated ON progress(playlist_id, updated_at);
            CREATE TABLE IF NOT EXISTS custom_groups(
              id INTEGER PRIMARY KEY AUTOINCREMENT, playlist_id INTEGER NOT NULL, name TEXT NOT NULL, created_at REAL NOT NULL);
            CREATE TABLE IF NOT EXISTS group_items(
              group_id INTEGER NOT NULL REFERENCES custom_groups(id) ON DELETE CASCADE,
              type TEXT NOT NULL, item_id TEXT NOT NULL, PRIMARY KEY(group_id, type, item_id));
            CREATE TABLE IF NOT EXISTS hidden(
              playlist_id INTEGER NOT NULL, kind TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(playlist_id, kind, value));
            CREATE TABLE IF NOT EXISTS locks(
              playlist_id INTEGER NOT NULL, kind TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(playlist_id, kind, value));
            CREATE TABLE IF NOT EXISTS notifications(
              id INTEGER PRIMARY KEY AUTOINCREMENT, playlist_id INTEGER, title TEXT NOT NULL, body TEXT NOT NULL,
              created_at REAL NOT NULL, is_read INTEGER NOT NULL DEFAULT 0);
            CREATE TABLE IF NOT EXISTS downloads(
              id INTEGER PRIMARY KEY AUTOINCREMENT, playlist_id INTEGER NOT NULL, type TEXT NOT NULL, item_id TEXT NOT NULL,
              title TEXT NOT NULL, image TEXT, file_path TEXT, status TEXT NOT NULL, progress REAL NOT NULL DEFAULT 0,
              total_bytes INTEGER NOT NULL DEFAULT 0, created_at REAL NOT NULL, ext TEXT,
              series_id INTEGER, season INTEGER, episode_num INTEGER);
            """)
            db.userVersion = 1
        }
    }
}

// MARK: - Playlists

final class PlaylistStore {
    private let db: SQLiteDatabase
    init(db: SQLiteDatabase) { self.db = db }

    private static let columns = "id, name, server_url, username, created_at, updated_at, last_sync, enabled, is_default, sort_order, status, live_count, movie_count, series_count, account_json"

    private func map(_ r: SQLRow) -> Playlist {
        var account: AccountInfo?
        if let json = r.optString(14), let data = json.data(using: .utf8) {
            account = try? JSONDecoder.akaa.decode(AccountInfo.self, from: data)
        }
        return Playlist(
            id: r.int64(0), name: r.string(1), serverURL: r.string(2), username: r.string(3),
            createdAt: r.date(4) ?? Date(), updatedAt: r.date(5) ?? Date(), lastSync: r.date(6),
            isEnabled: r.bool(7), isDefault: r.bool(8), sortOrder: r.int(9),
            status: PlaylistConnectionStatus(rawValue: r.string(10)) ?? .unknown,
            liveCount: r.int(11), movieCount: r.int(12), seriesCount: r.int(13), account: account)
    }

    func all() throws -> [Playlist] {
        try db.query("SELECT \(PlaylistStore.columns) FROM playlists ORDER BY sort_order, id", [], map)
    }

    func get(_ id: Int64) throws -> Playlist? {
        try db.query("SELECT \(PlaylistStore.columns) FROM playlists WHERE id = ?", [.int(id)], map).first
    }

    func defaultPlaylist() throws -> Playlist? {
        let list = try all().filter { $0.isEnabled }
        return list.first(where: { $0.isDefault }) ?? list.first
    }

    @discardableResult
    func insert(name: String, serverURL: String, username: String, account: AccountInfo?, status: PlaylistConnectionStatus) throws -> Playlist {
        let now = Date()
        let count = try db.scalarInt("SELECT COUNT(*) FROM playlists")
        let order = try db.scalarInt("SELECT COALESCE(MAX(sort_order), -1) + 1 FROM playlists")
        try db.run("""
            INSERT INTO playlists(name, server_url, username, created_at, updated_at, enabled, is_default, sort_order, status, account_json)
            VALUES(?,?,?,?,?,1,?,?,?,?)
            """, [.text(name), .text(serverURL), .text(username), SQLValue(now), SQLValue(now),
                  SQLValue(count == 0), .int(Int64(order)), .text(status.rawValue), SQLValue(encode(account))])
        let id = db.lastInsertRowID
        guard let created = try get(id) else { throw AkaaError.storage("insert failed") }
        return created
    }

    func update(_ p: Playlist) throws {
        try db.run("""
            UPDATE playlists SET name=?, server_url=?, username=?, updated_at=?, last_sync=?, enabled=?, sort_order=?,
            status=?, live_count=?, movie_count=?, series_count=?, account_json=? WHERE id=?
            """, [.text(p.name), .text(p.serverURL), .text(p.username), SQLValue(Date()), SQLValue(p.lastSync),
                  SQLValue(p.isEnabled), .int(Int64(p.sortOrder)), .text(p.status.rawValue),
                  .int(Int64(p.liveCount)), .int(Int64(p.movieCount)), .int(Int64(p.seriesCount)),
                  SQLValue(encode(p.account)), .int(p.id)])
    }

    func setDefault(_ id: Int64) throws {
        try db.transaction {
            try db.run("UPDATE playlists SET is_default = 0")
            try db.run("UPDATE playlists SET is_default = 1, enabled = 1 WHERE id = ?", [.int(id)])
        }
    }

    func reorder(_ ids: [Int64]) throws {
        try db.transaction {
            for (i, id) in ids.enumerated() {
                try db.run("UPDATE playlists SET sort_order = ? WHERE id = ?", [.int(Int64(i)), .int(id)])
            }
        }
    }

    func delete(_ id: Int64) throws {
        try db.transaction {
            for table in ["channels", "movies", "series", "categories", "favorites", "progress", "hidden", "locks", "notifications", "downloads"] {
                try db.run("DELETE FROM \(table) WHERE playlist_id = ?", [.int(id)])
            }
            try db.run("DELETE FROM group_items WHERE group_id IN (SELECT id FROM custom_groups WHERE playlist_id = ?)", [.int(id)])
            try db.run("DELETE FROM custom_groups WHERE playlist_id = ?", [.int(id)])
            let wasDefault = try db.scalarInt("SELECT is_default FROM playlists WHERE id = ?", [.int(id)]) == 1
            try db.run("DELETE FROM playlists WHERE id = ?", [.int(id)])
            if wasDefault {
                try db.run("UPDATE playlists SET is_default = 1 WHERE id = (SELECT id FROM playlists ORDER BY sort_order, id LIMIT 1)")
            }
        }
    }

    private func encode(_ account: AccountInfo?) -> String? {
        guard let account = account, let data = try? JSONEncoder.akaa.encode(account) else { return nil }
        return String(data: data, encoding: .utf8)
    }
}

extension JSONEncoder {
    static var akaa: JSONEncoder {
        let e = JSONEncoder()
        e.dateEncodingStrategy = .secondsSince1970
        e.outputFormatting = [.sortedKeys]
        return e
    }
}

extension JSONDecoder {
    static var akaa: JSONDecoder {
        let d = JSONDecoder()
        d.dateDecodingStrategy = .secondsSince1970
        return d
    }
}
