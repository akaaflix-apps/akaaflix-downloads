import Foundation

/// Filters applied when browsing a catalog section.
struct BrowseFilter: Equatable {
    var categoryID: String?
    var search: String = ""
    var sort: SortOption = .providerOrder
    var year: Int?
    var genre: String?
    var quality: String?
    /// Restrict to these item IDs (favorites, custom groups, most watched…).
    var onlyIDs: [String]?
    var excludeAdult: Bool = true
    var hidden: HiddenContent = HiddenContent()
    var limit: Int?
    var addedSince: Date?
}

/// Content the user hid (categories, items, keywords) — removed from browsing and search.
struct HiddenContent: Equatable, Codable {
    var categoryIDs: Set<String> = []
    var itemIDs: Set<String> = []
    var keywords: [String] = []
    var hiddenSections: Set<ContentType> = []
}

struct CatalogCounts: Equatable {
    var live = 0, movies = 0, series = 0
}

struct SyncSummary: Equatable {
    var counts = CatalogCounts()
    var newChannels = 0, newMovies = 0, newSeries = 0
    var failedSections: [ContentType] = []
}

final class CatalogStore {
    private let db: SQLiteDatabase
    init(db: SQLiteDatabase) { self.db = db }

    // MARK: Writing

    /// Replaces a playlist's catalog atomically and reports how many items are new.
    /// Sections passed as nil keep their previous content (partial sync failures).
    func replace(playlistID pid: Int64,
                 categories: [ContentType: [ContentCategory]],
                 channels: [Channel]?, movies: [Movie]?, series: [Series]?) throws -> SyncSummary {
        var summary = SyncSummary()
        let p = SQLValue.int(pid)
        try db.transaction {
            for (type, cats) in categories {
                try db.run("DELETE FROM categories WHERE playlist_id = ? AND type = ?", [p, .text(type.rawValue)])
                try db.runBatch("INSERT OR REPLACE INTO categories(playlist_id, type, category_id, name, sort) VALUES(?,?,?,?,?)",
                                cats.map { [p, .text(type.rawValue), .text($0.id), .text($0.name), .int(Int64($0.sortIndex))] })
            }
            if let channels = channels {
                let existing = Set(try db.query("SELECT stream_id FROM channels WHERE playlist_id = ?", [p]) { $0.int(0) })
                summary.newChannels = existing.isEmpty ? 0 : channels.filter { !existing.contains($0.id) }.count
                try db.run("DELETE FROM channels WHERE playlist_id = ?", [p])
                try db.runBatch("""
                    INSERT OR REPLACE INTO channels(playlist_id, stream_id, num, name, name_norm, icon, epg_id, category_id, added, is_adult, archive, quality)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                    """, channels.map { c in
                        [p, .int(Int64(c.id)), .int(Int64(c.number)), .text(c.name), .text(ContentText.normalize(c.name)),
                         SQLValue(c.icon), SQLValue(c.epgChannelID), SQLValue(c.categoryID), SQLValue(c.added),
                         SQLValue(c.isAdult), SQLValue(c.hasArchive), SQLValue(c.quality)]
                    })
            }
            if let movies = movies {
                let existing = Set(try db.query("SELECT stream_id FROM movies WHERE playlist_id = ?", [p]) { $0.int(0) })
                summary.newMovies = existing.isEmpty ? 0 : movies.filter { !existing.contains($0.id) }.count
                try db.run("DELETE FROM movies WHERE playlist_id = ?", [p])
                try db.runBatch("""
                    INSERT OR REPLACE INTO movies(playlist_id, stream_id, num, name, name_norm, poster, rating, added, category_id, ext, year, genre, plot, is_adult, quality)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, movies.map { m in
                        [p, .int(Int64(m.id)), .int(Int64(m.number)), .text(m.name), .text(ContentText.normalize(m.name)),
                         SQLValue(m.poster), SQLValue(m.rating), SQLValue(m.added), SQLValue(m.categoryID), SQLValue(m.containerExtension),
                         SQLValue(m.year), SQLValue(m.genre), SQLValue(m.plot), SQLValue(m.isAdult), SQLValue(m.quality)]
                    })
            }
            if let series = series {
                let existing = Set(try db.query("SELECT series_id FROM series WHERE playlist_id = ?", [p]) { $0.int(0) })
                summary.newSeries = existing.isEmpty ? 0 : series.filter { !existing.contains($0.id) }.count
                try db.run("DELETE FROM series WHERE playlist_id = ?", [p])
                try db.runBatch("""
                    INSERT OR REPLACE INTO series(playlist_id, series_id, num, name, name_norm, cover, plot, cast_names, director, genre, release_date, last_modified, rating, backdrop, run_time, category_id, year, is_adult, quality)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, series.map { s in
                        [p, .int(Int64(s.id)), .int(Int64(s.number)), .text(s.name), .text(ContentText.normalize(s.name)),
                         SQLValue(s.cover), SQLValue(s.plot), SQLValue(s.cast), SQLValue(s.director), SQLValue(s.genre),
                         SQLValue(s.releaseDate), SQLValue(s.lastModified), SQLValue(s.rating), SQLValue(s.backdrop),
                         SQLValue(s.episodeRunTime), SQLValue(s.categoryID), SQLValue(s.year), SQLValue(s.isAdult), SQLValue(s.quality)]
                    })
            }
        }
        summary.counts = try counts(playlistID: pid)
        return summary
    }

    // MARK: Reading

    func counts(playlistID pid: Int64) throws -> CatalogCounts {
        CatalogCounts(
            live: try db.scalarInt("SELECT COUNT(*) FROM channels WHERE playlist_id = ?", [.int(pid)]),
            movies: try db.scalarInt("SELECT COUNT(*) FROM movies WHERE playlist_id = ?", [.int(pid)]),
            series: try db.scalarInt("SELECT COUNT(*) FROM series WHERE playlist_id = ?", [.int(pid)]))
    }

    private func table(_ type: ContentType) -> (name: String, id: String) {
        switch type {
        case .live: return ("channels", "stream_id")
        case .movie: return ("movies", "stream_id")
        case .series, .episode: return ("series", "series_id")
        }
    }

    /// Categories with item counts, in provider order.
    func categories(playlistID pid: Int64, type: ContentType, hidden: HiddenContent = HiddenContent(), excludeAdult: Bool = true) throws -> [ContentCategory] {
        let t = table(type)
        let typeKey = type == .episode ? ContentType.series.rawValue : type.rawValue
        let rows = try db.query("""
            SELECT c.category_id, c.name, c.sort, (SELECT COUNT(*) FROM \(t.name) i WHERE i.playlist_id = c.playlist_id AND i.category_id = c.category_id)
            FROM categories c WHERE c.playlist_id = ? AND c.type = ? ORDER BY c.sort
            """, [.int(pid), .text(typeKey)]) { r in
            ContentCategory(id: r.string(0), name: r.string(1), type: type, sortIndex: r.int(2), count: r.int(3))
        }
        return rows.filter { cat in
            !hidden.categoryIDs.contains(cat.id) && !(excludeAdult && cat.isAdult)
                && !hidden.keywords.contains(where: { !$0.isEmpty && ContentText.normalize(cat.name).contains(ContentText.normalize($0)) })
        }
    }

    private func adultCategoryIDs(_ pid: Int64, _ type: ContentType) throws -> [String] {
        let typeKey = type == .episode ? ContentType.series.rawValue : type.rawValue
        return try db.query("SELECT category_id, name FROM categories WHERE playlist_id = ? AND type = ?", [.int(pid), .text(typeKey)]) {
            ($0.string(0), $0.string(1))
        }.filter { CategoryClassifier.isAdult($0.1) }.map { $0.0 }
    }

    private func whereClause(_ pid: Int64, _ type: ContentType, _ f: BrowseFilter) throws -> (String, [SQLValue]) {
        let t = table(type)
        var parts = ["playlist_id = ?"]
        var args: [SQLValue] = [.int(pid)]
        if let cat = f.categoryID { parts.append("category_id = ?"); args.append(.text(cat)) }
        let q = f.search.trimmingCharacters(in: .whitespaces)
        if !q.isEmpty { parts.append("name_norm LIKE ? ESCAPE '\\'"); args.append(.text(ContentText.likePattern(q))) }
        if let year = f.year, type != .live { parts.append("year = ?"); args.append(.int(Int64(year))) }
        if let genre = f.genre, type != .live, !genre.isEmpty { parts.append("genre LIKE ? ESCAPE '\\'"); args.append(.text("%\(genre.replacingOccurrences(of: "%", with: "\\%"))%")) }
        if let quality = f.quality { parts.append("quality = ?"); args.append(.text(quality)) }
        if let since = f.addedSince {
            parts.append(type == .series ? "last_modified >= ?" : "added >= ?")
            args.append(.double(since.timeIntervalSince1970))
        }
        if let ids = f.onlyIDs {
            if ids.isEmpty { parts.append("0") } else {
                parts.append("CAST(\(t.id) AS TEXT) IN (\(Array(repeating: "?", count: ids.count).joined(separator: ",")))")
                args += ids.map { SQLValue.text($0) }
            }
        }
        if f.excludeAdult {
            parts.append("is_adult = 0")
            let adult = try adultCategoryIDs(pid, type)
            if !adult.isEmpty {
                parts.append("COALESCE(category_id, '') NOT IN (\(Array(repeating: "?", count: adult.count).joined(separator: ",")))")
                args += adult.map { SQLValue.text($0) }
            }
        }
        if !f.hidden.categoryIDs.isEmpty {
            parts.append("COALESCE(category_id, '') NOT IN (\(Array(repeating: "?", count: f.hidden.categoryIDs.count).joined(separator: ",")))")
            args += f.hidden.categoryIDs.sorted().map { SQLValue.text($0) }
        }
        let hiddenItems = f.hidden.itemIDs.compactMap { key -> String? in
            let pieces = key.split(separator: ":", maxSplits: 1).map(String.init)
            return pieces.count == 2 && pieces[0] == type.rawValue ? pieces[1] : nil
        }
        if !hiddenItems.isEmpty {
            parts.append("CAST(\(t.id) AS TEXT) NOT IN (\(Array(repeating: "?", count: hiddenItems.count).joined(separator: ",")))")
            args += hiddenItems.map { SQLValue.text($0) }
        }
        for keyword in f.hidden.keywords where !keyword.trimmingCharacters(in: .whitespaces).isEmpty {
            parts.append("name_norm NOT LIKE ? ESCAPE '\\'")
            args.append(.text(ContentText.likePattern(keyword)))
        }
        return (parts.joined(separator: " AND "), args)
    }

    private func orderClause(_ type: ContentType, _ sort: SortOption) -> String {
        switch sort {
        case .providerOrder: return "num, name"
        case .recentlyAdded: return type == .series ? "COALESCE(last_modified, 0) DESC, num" : "COALESCE(added, 0) DESC, num"
        case .name: return "name_norm"
        case .rating: return type == .live ? "num" : "COALESCE(rating, 0) DESC, name_norm"
        case .year: return type == .live ? "num" : "COALESCE(year, 0) DESC, name_norm"
        }
    }

    private func limitClause(_ f: BrowseFilter) -> String { f.limit.map { " LIMIT \($0)" } ?? "" }

    func channels(playlistID pid: Int64, filter f: BrowseFilter) throws -> [Channel] {
        let (w, args) = try whereClause(pid, .live, f)
        return try db.query("SELECT stream_id, num, name, icon, epg_id, category_id, added, is_adult, archive FROM channels WHERE \(w) ORDER BY \(orderClause(.live, f.sort))\(limitClause(f))", args, CatalogStore.mapChannel)
    }

    func movies(playlistID pid: Int64, filter f: BrowseFilter) throws -> [Movie] {
        let (w, args) = try whereClause(pid, .movie, f)
        return try db.query("SELECT \(CatalogStore.movieColumns) FROM movies WHERE \(w) ORDER BY \(orderClause(.movie, f.sort))\(limitClause(f))", args, CatalogStore.mapMovie)
    }

    func series(playlistID pid: Int64, filter f: BrowseFilter) throws -> [Series] {
        let (w, args) = try whereClause(pid, .series, f)
        return try db.query("SELECT \(CatalogStore.seriesColumns) FROM series WHERE \(w) ORDER BY \(orderClause(.series, f.sort))\(limitClause(f))", args, CatalogStore.mapSeries)
    }

    func count(playlistID pid: Int64, type: ContentType, filter f: BrowseFilter) throws -> Int {
        let (w, args) = try whereClause(pid, type, f)
        return try db.scalarInt("SELECT COUNT(*) FROM \(table(type).name) WHERE \(w)", args)
    }

    func channel(playlistID pid: Int64, id: Int) throws -> Channel? {
        try db.query("SELECT stream_id, num, name, icon, epg_id, category_id, added, is_adult, archive FROM channels WHERE playlist_id = ? AND stream_id = ?", [.int(pid), .int(Int64(id))], CatalogStore.mapChannel).first
    }

    func movie(playlistID pid: Int64, id: Int) throws -> Movie? {
        try db.query("SELECT \(CatalogStore.movieColumns) FROM movies WHERE playlist_id = ? AND stream_id = ?", [.int(pid), .int(Int64(id))], CatalogStore.mapMovie).first
    }

    func series(playlistID pid: Int64, id: Int) throws -> Series? {
        try db.query("SELECT \(CatalogStore.seriesColumns) FROM series WHERE playlist_id = ? AND series_id = ?", [.int(pid), .int(Int64(id))], CatalogStore.mapSeries).first
    }

    /// Distinct years present in a section (for the year filter), newest first.
    func years(playlistID pid: Int64, type: ContentType) throws -> [Int] {
        try db.query("SELECT DISTINCT year FROM \(table(type).name) WHERE playlist_id = ? AND year IS NOT NULL ORDER BY year DESC", [.int(pid)]) { $0.int(0) }
    }

    /// Distinct genres (split from provider lists), most frequent first.
    func genres(playlistID pid: Int64, type: ContentType) throws -> [String] {
        let raw = try db.query("SELECT genre FROM \(table(type).name) WHERE playlist_id = ? AND genre IS NOT NULL AND genre <> ''", [.int(pid)]) { $0.string(0) }
        var freq: [String: Int] = [:]
        for g in raw { for item in ContentText.splitList(g) { freq[item, default: 0] += 1 } }
        return freq.sorted { $0.value == $1.value ? $0.key < $1.key : $0.value > $1.value }.prefix(60).map { $0.key }
    }

    // MARK: Row mapping

    static let movieColumns = "stream_id, num, name, poster, rating, added, category_id, ext, year, genre, plot, is_adult"
    static let seriesColumns = "series_id, num, name, cover, plot, cast_names, director, genre, release_date, last_modified, rating, backdrop, run_time, category_id, year, is_adult"

    static func mapChannel(_ r: SQLRow) -> Channel {
        Channel(id: r.int(0), number: r.int(1), name: r.string(2), icon: r.optString(3), epgChannelID: r.optString(4),
                categoryID: r.optString(5), added: r.date(6), isAdult: r.bool(7), hasArchive: r.bool(8))
    }

    static func mapMovie(_ r: SQLRow) -> Movie {
        Movie(id: r.int(0), number: r.int(1), name: r.string(2), poster: r.optString(3), rating: r.optDouble(4), added: r.date(5),
              categoryID: r.optString(6), containerExtension: r.optString(7), year: r.optInt(8), genre: r.optString(9),
              plot: r.optString(10), isAdult: r.bool(11))
    }

    static func mapSeries(_ r: SQLRow) -> Series {
        Series(id: r.int(0), number: r.int(1), name: r.string(2), cover: r.optString(3), plot: r.optString(4), cast: r.optString(5),
               director: r.optString(6), genre: r.optString(7), releaseDate: r.optString(8), lastModified: r.date(9),
               rating: r.optDouble(10), backdrop: r.optString(11), episodeRunTime: r.optString(12), categoryID: r.optString(13),
               year: r.optInt(14), isAdult: r.bool(15))
    }
}
