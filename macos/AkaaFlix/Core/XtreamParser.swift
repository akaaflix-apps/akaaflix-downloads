import Foundation

/// Pure functions turning `player_api.php` JSON into models. Kept free of networking so they
/// can be unit tested against fixtures, including the deliberately inconsistent shapes that
/// real panels return.
enum XtreamParser {

    private typealias J = LenientJSON

    static func date(_ value: Any?) -> Date? {
        guard let t = J.int(value), t > 0 else { return nil }
        return Date(timeIntervalSince1970: TimeInterval(t))
    }

    // MARK: Authentication

    static func account(_ json: Any, baseURL: String) throws -> AccountInfo {
        guard let root = json as? [String: Any], let user = root["user_info"] as? [String: Any] else {
            // `[]`, `false` or anything but an object with user_info means bad credentials or not Xtream.
            if json is [Any] || json is NSNumber { throw AkaaError.invalidCredentials }
            throw AkaaError.notXtreamServer
        }
        guard J.int(user["auth"]) == 1 else { throw AkaaError.invalidCredentials }
        let status = AccountStatus.parse(J.string(user["status"]))
        switch status {
        case .expired: throw AkaaError.accountExpired
        case .banned: throw AkaaError.accountBanned
        case .disabled: throw AkaaError.accountDisabled
        default: break
        }
        let server = root["server_info"] as? [String: Any]
        return AccountInfo(
            status: status,
            rawStatus: J.string(user["status"]),
            expiresAt: date(user["exp_date"]),
            createdAt: date(user["created_at"]),
            isTrial: J.int(user["is_trial"]) == 1,
            activeConnections: J.int(user["active_cons"]),
            maxConnections: J.int(user["max_connections"]),
            allowedOutputFormats: J.stringArray(user["allowed_output_formats"]).map { $0.lowercased() },
            message: J.string(user["message"]),
            serverTimezone: J.string(server?["timezone"]),
            serverProtocol: J.string(server?["server_protocol"]),
            rtmpPort: J.string(server?["rtmp_port"]),
            httpsPort: J.string(server?["https_port"]),
            serverTimeNow: date(server?["timestamp_now"]),
            resolvedBaseURL: baseURL
        )
    }

    // MARK: Lists (some panels answer {}, null or false for empty lists)

    static func listItems(_ json: Any) -> [[String: Any]] {
        if let arr = json as? [Any] { return arr.compactMap { $0 as? [String: Any] } }
        // A few panels return an object keyed by index.
        if let obj = json as? [String: Any], obj.keys.allSatisfy({ Int($0) != nil }) {
            return obj.keys.sorted { (Int($0) ?? 0) < (Int($1) ?? 0) }.compactMap { obj[$0] as? [String: Any] }
        }
        return []
    }

    static func categories(_ json: Any, type: ContentType) -> [ContentCategory] {
        var seen = Set<String>()
        return listItems(json).enumerated().compactMap { index, item in
            guard let id = J.string(item["category_id"]), seen.insert(id).inserted else { return nil }
            return ContentCategory(id: id, name: J.string(item["category_name"]) ?? id, type: type, sortIndex: index)
        }
    }

    static func channels(_ json: Any) -> [Channel] {
        var seen = Set<Int>()
        return listItems(json).enumerated().compactMap { index, item in
            guard let id = J.int(item["stream_id"]), seen.insert(id).inserted else { return nil }
            let name = J.string(item["name"]) ?? "#\(id)"
            return Channel(
                id: id,
                number: J.int(item["num"]) ?? index + 1,
                name: name,
                icon: J.string(item["stream_icon"]).flatMap(validImageURL),
                epgChannelID: J.string(item["epg_channel_id"]),
                categoryID: J.string(item["category_id"]),
                added: date(item["added"]),
                isAdult: J.int(item["is_adult"]) == 1,
                hasArchive: J.int(item["tv_archive"]) == 1
            )
        }
    }

    static func movies(_ json: Any) -> [Movie] {
        var seen = Set<Int>()
        return listItems(json).enumerated().compactMap { index, item in
            guard let id = J.int(item["stream_id"]), seen.insert(id).inserted else { return nil }
            let name = J.string(item["name"]) ?? J.string(item["title"]) ?? "#\(id)"
            let rating = J.double(item["rating"]).flatMap(validRating) ?? J.double(item["rating_5based"]).flatMap { validRating($0 * 2) }
            return Movie(
                id: id,
                number: J.int(item["num"]) ?? index + 1,
                name: name,
                poster: J.string(item["stream_icon"]).flatMap(validImageURL),
                rating: rating,
                added: date(item["added"]),
                categoryID: J.string(item["category_id"]),
                containerExtension: J.string(item["container_extension"]),
                year: J.int(item["year"]).flatMap { (1900...2100).contains($0) ? $0 : nil }
                    ?? ContentText.yearOf(J.string(item["release_date"]), J.string(item["releasedate"]), name),
                genre: J.string(item["genre"]),
                plot: J.string(item["plot"]),
                isAdult: J.int(item["is_adult"]) == 1
            )
        }
    }

    static func series(_ json: Any) -> [Series] {
        var seen = Set<Int>()
        return listItems(json).enumerated().compactMap { index, item in
            guard let id = J.int(item["series_id"]), seen.insert(id).inserted else { return nil }
            let name = J.string(item["name"]) ?? "#\(id)"
            let release = J.firstString(item, "releaseDate", "release_date", "releasedate")
            let rating = J.double(item["rating"]).flatMap(validRating) ?? J.double(item["rating_5based"]).flatMap { validRating($0 * 2) }
            return Series(
                id: id,
                number: J.int(item["num"]) ?? index + 1,
                name: name,
                cover: J.string(item["cover"]).flatMap(validImageURL),
                plot: J.string(item["plot"]),
                cast: J.string(item["cast"]),
                director: J.string(item["director"]),
                genre: J.string(item["genre"]),
                releaseDate: release,
                lastModified: date(item["last_modified"]),
                rating: rating,
                backdrop: J.stringArray(item["backdrop_path"]).first(where: { $0.hasPrefix("http") }),
                episodeRunTime: J.string(item["episode_run_time"]),
                categoryID: J.string(item["category_id"]),
                year: ContentText.yearOf(release, name),
                isAdult: J.int(item["is_adult"]) == 1
            )
        }
    }

    // MARK: Details

    static func movieDetails(_ json: Any, base movie: Movie) -> MovieDetails {
        let root = json as? [String: Any]
        let info = root?["info"] as? [String: Any]
        let data = root?["movie_data"] as? [String: Any]
        var m = movie
        if let ext = J.string(data?["container_extension"]) { m.containerExtension = ext }
        if m.poster == nil { m.poster = J.firstString(info, "movie_image", "cover_big").flatMap(validImageURL) }
        if m.rating == nil { m.rating = J.double(info?["rating"]).flatMap(validRating) }
        let release = J.firstString(info, "releasedate", "release_date", "releaseDate")
        if m.year == nil { m.year = ContentText.yearOf(release) }
        if m.genre == nil { m.genre = J.string(info?["genre"]) }
        let duration = J.int(info?["duration_secs"]).flatMap { $0 > 0 ? $0 : nil } ?? TimeFormat.parseDuration(J.firstString(info, "duration", "episode_run_time"))
        let video = info?["video"] as? [String: Any]
        var resolution: String?
        if let w = J.int(video?["width"]), let h = J.int(video?["height"]), w > 0, h > 0 { resolution = "\(w)x\(h)" }
        let audio = info?["audio"] as? [String: Any]
        let audioLang = (audio?["tags"] as? [String: Any]).flatMap { J.string($0["language"]) }
        return MovieDetails(
            movie: m,
            originalName: J.firstString(info, "o_name", "original_name", "name").flatMap { $0 == movie.name ? nil : $0 },
            plot: J.firstString(info, "plot", "description", "overview") ?? movie.plot,
            genre: J.string(info?["genre"]) ?? movie.genre,
            cast: J.firstString(info, "cast", "actors"),
            director: J.string(info?["director"]),
            releaseDate: release,
            durationSeconds: duration,
            backdrop: J.stringArray(info?["backdrop_path"]).first(where: { $0.hasPrefix("http") }) ?? J.firstString(info, "cover_big", "movie_image"),
            ageRating: J.firstString(info, "age", "mpaa_rating", "mpaa", "certification", "rating_mpaa"),
            country: J.string(info?["country"]),
            trailer: J.firstString(info, "youtube_trailer", "trailer"),
            videoResolution: resolution,
            audioLanguages: audioLang.map { [$0] } ?? []
        )
    }

    static func seriesDetails(_ json: Any, base series: Series) -> SeriesDetails {
        let root = json as? [String: Any]
        let info = root?["info"] as? [String: Any]
        let episodes = parseEpisodes(seriesID: series.id, root?["episodes"])
        let seasons = parseSeasons(root?["seasons"], episodes: episodes)
        var s = series
        if s.plot == nil { s.plot = J.firstString(info, "plot", "description") }
        if s.genre == nil { s.genre = J.string(info?["genre"]) }
        if s.backdrop == nil { s.backdrop = J.stringArray(info?["backdrop_path"]).first(where: { $0.hasPrefix("http") }) }
        if s.cover == nil { s.cover = J.string(info?["cover"]).flatMap(validImageURL) }
        if s.episodeRunTime == nil { s.episodeRunTime = J.string(info?["episode_run_time"]) }
        if s.rating == nil { s.rating = J.double(info?["rating"]).flatMap(validRating) }
        let release = J.firstString(info, "releaseDate", "release_date", "releasedate")
        if s.year == nil { s.year = ContentText.yearOf(release) }
        return SeriesDetails(
            series: s,
            originalName: J.firstString(info, "o_name", "original_name").flatMap { $0 == series.name ? nil : $0 },
            cast: J.firstString(info, "cast", "actors"),
            director: J.string(info?["director"]),
            releaseDate: release,
            ageRating: J.firstString(info, "age", "mpaa_rating", "certification"),
            country: J.string(info?["country"]),
            language: J.string(info?["language"]),
            seasons: seasons,
            episodesBySeason: episodes
        )
    }

    /// `episodes` is usually `{ "1": [ ... ], "2": [ ... ] }` but some panels send a list
    /// (or a list of lists), or a map of maps.
    static func parseEpisodes(seriesID: Int, _ element: Any?) -> [Int: [Episode]] {
        var raw: [(Int?, [String: Any])] = []
        if let obj = element as? [String: Any] {
            for (key, value) in obj {
                let season: Int? = Int(key)
                var items: [Any] = []
                if let arr = value as? [Any] {
                    items = arr
                } else if let map = value as? [String: Any] {
                    items = Array(map.values)
                }
                for case let ep as [String: Any] in items { raw.append((season, ep)) }
            }
        } else if let arr = element as? [Any] {
            for item in arr {
                if let inner = item as? [Any] {
                    for case let ep as [String: Any] in inner { raw.append((nil, ep)) }
                } else if let ep = item as? [String: Any] {
                    raw.append((nil, ep))
                }
            }
        }
        var seen = Set<String>()
        var result: [Int: [Episode]] = [:]
        for (seasonKey, ep) in raw {
            guard let id = J.string(ep["id"]), seen.insert(id).inserted else { continue }
            let info = ep["info"] as? [String: Any]
            let season = J.int(ep["season"]) ?? seasonKey ?? 1
            let number = J.int(ep["episode_num"]) ?? 0
            let episode = Episode(
                id: id,
                seriesID: seriesID,
                season: season,
                episodeNumber: number,
                title: J.string(ep["title"]) ?? J.string(info?["name"]) ?? "E\(number)",
                plot: J.firstString(info, "plot", "overview"),
                thumbnail: J.firstString(info, "movie_image", "cover_big").flatMap(validImageURL),
                durationSeconds: J.int(info?["duration_secs"]).flatMap { $0 > 0 ? $0 : nil } ?? TimeFormat.parseDuration(J.string(info?["duration"])),
                containerExtension: J.string(ep["container_extension"]),
                rating: J.double(info?["rating"]).flatMap(validRating),
                releaseDate: J.firstString(info, "releasedate", "air_date")
            )
            result[season, default: []].append(episode)
        }
        for key in result.keys { result[key]?.sort { ($0.episodeNumber, $0.id) < ($1.episodeNumber, $1.id) } }
        return result
    }

    static func parseSeasons(_ element: Any?, episodes: [Int: [Episode]]) -> [Season] {
        var declared: [Int: Season] = [:]
        for item in J.array(element) {
            guard let s = item as? [String: Any], let number = J.int(s["season_number"]) else { continue }
            declared[number] = Season(
                number: number,
                name: J.string(s["name"]) ?? "S\(number)",
                cover: J.firstString(s, "cover_big", "cover").flatMap(validImageURL),
                episodeCount: episodes[number]?.count ?? J.int(s["episode_count"]) ?? 0,
                overview: J.string(s["overview"])
            )
        }
        // Only keep seasons with playable episodes; add seasons that appear only in episodes.
        return episodes.keys.sorted().map { n in
            if var s = declared[n] { s.episodeCount = episodes[n]?.count ?? 0; return s }
            return Season(number: n, name: "S\(n)", cover: nil, episodeCount: episodes[n]?.count ?? 0, overview: nil)
        }
    }

    // MARK: EPG

    static func shortEPG(_ json: Any) -> [EPGEntry] {
        guard let root = json as? [String: Any] else { return [] }
        let listings = J.array(root["epg_listings"]).compactMap { $0 as? [String: Any] }
        let parser = DateFormatter()
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.timeZone = TimeZone(identifier: "UTC")
        parser.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return listings.compactMap { item in
            let start = date(item["start_timestamp"]) ?? J.string(item["start"]).flatMap { parser.date(from: $0) }
            let end = date(item["stop_timestamp"]) ?? J.string(item["end"]).flatMap { parser.date(from: $0) } ?? J.string(item["stop"]).flatMap { parser.date(from: $0) }
            guard let s = start, let e = end, e > s else { return nil }
            let title = Base64Text.decodeIfBase64(J.string(item["title"])) ?? ""
            return EPGEntry(title: title, description: Base64Text.decodeIfBase64(J.string(item["description"])), start: s, end: e)
        }.sorted { $0.start < $1.start }
    }

    // MARK: Helpers

    static func validRating(_ value: Double) -> Double? {
        (0.1...10.0).contains(value) ? (value * 10).rounded() / 10 : nil
    }

    static func validImageURL(_ value: String) -> String? {
        let lower = value.lowercased()
        return lower.hasPrefix("http://") || lower.hasPrefix("https://") ? value : nil
    }
}
