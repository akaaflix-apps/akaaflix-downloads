import XCTest

/// Core logic tests. The test bundle compiles the Foundation-only `Core` sources directly,
/// so these run without launching the app or loading the playback engine.
final class LiveStreamPolicyTests: XCTestCase {

    private let creds = XtreamCredentials(baseURL: "http://example.com:8080/", username: "user", password: "p@ss/1")

    func testMandatoryOrderIsMpegTsThenHls() {
        XCTAssertEqual(LiveStreamPolicy.mandatoryOrder, [.mpegTS, .hls])
    }

    func testCompatibilityFormatsAreOnlyAppendedAfterMandatoryOrder() {
        let order = LiveStreamPolicy.attemptOrder(enabledCompatibilityFormats: [.http, .dash, .rtmp, .rtsp], rtmpPort: "1935")
        XCTAssertEqual(Array(order.prefix(2)), [.mpegTS, .hls])
        XCTAssertEqual(order, [.mpegTS, .hls, .dash, .rtmp, .rtsp, .http])
    }

    func testSettingsCannotInjectMandatoryFormatsOutOfOrder() throws {
        // Even a hand-edited settings file listing HLS as a "compatibility" format cannot move it.
        let json = #"{"enabledCompatibilityFormats":["m3u8","ts","mpd"]}"#.data(using: .utf8)!
        let settings = try JSONDecoder.akaa.decode(AppSettings.self, from: json)
        XCTAssertEqual(settings.enabledCompatibilityFormats, [.dash])
        let order = LiveStreamPolicy.attemptOrder(enabledCompatibilityFormats: settings.enabledCompatibilityFormats, rtmpPort: nil)
        XCTAssertEqual(order, [.mpegTS, .hls, .dash])
    }

    func testRtmpSkippedWithoutUsablePort() {
        XCTAssertEqual(LiveStreamPolicy.attemptOrder(enabledCompatibilityFormats: [.rtmp], rtmpPort: "0"), [.mpegTS, .hls])
        XCTAssertEqual(LiveStreamPolicy.attemptOrder(enabledCompatibilityFormats: [.rtmp], rtmpPort: nil), [.mpegTS, .hls])
    }

    func testLiveCandidateURLs() {
        let candidates = StreamURLBuilder.liveCandidates(creds, streamID: 42, enabledCompatibilityFormats: [], rtmpPort: nil)
        XCTAssertEqual(candidates.map { $0.url.absoluteString }, [
            "http://example.com:8080/live/user/p@ss%2F1/42.ts",
            "http://example.com:8080/live/user/p@ss%2F1/42.m3u8",
        ])
        XCTAssertFalse(candidates[0].isAdaptive)
        XCTAssertTrue(candidates[1].isAdaptive)
        XCTAssertFalse(candidates[0].description.contains("p@ss"))
    }

    func testVodAndEpisodeURLs() {
        XCTAssertEqual(StreamURLBuilder.movie(creds, streamID: 7, containerExtension: ".MKV")?.url.absoluteString, "http://example.com:8080/movie/user/p@ss%2F1/7.mkv")
        XCTAssertEqual(StreamURLBuilder.movie(creds, streamID: 7, containerExtension: "bad ext!")?.url.absoluteString, "http://example.com:8080/movie/user/p@ss%2F1/7.mp4")
        XCTAssertEqual(StreamURLBuilder.episode(creds, episodeID: "991", containerExtension: "mp4")?.url.absoluteString, "http://example.com:8080/series/user/p@ss%2F1/991.mp4")
    }

    func testRedactRemovesCredentials() {
        let url = StreamURLBuilder.live(creds, streamID: 1, format: .mpegTS, rtmpPort: nil)!.url
        let redacted = StreamURLBuilder.redact(url, creds: creds)
        XCTAssertFalse(redacted.contains("p@ss"))
        XCTAssertFalse(redacted.contains("/user/"))
    }
}

final class FailoverPlannerTests: XCTestCase {

    private func candidates() -> [StreamCandidate] {
        let creds = XtreamCredentials(baseURL: "http://h.example/", username: "u", password: "p")
        return StreamURLBuilder.liveCandidates(creds, streamID: 1, enabledCompatibilityFormats: [], rtmpPort: nil)
    }

    func testFailureBeforePlaybackMovesTsToHls() {
        let planner = FailoverPlanner(candidates: candidates())
        guard case .play(let first, _) = planner.start() else { return XCTFail("no start") }
        XCTAssertEqual(first.format, .mpegTS)
        guard case .play(let second, let delay) = planner.didFail() else { return XCTFail("no fallback") }
        XCTAssertEqual(second.format, .hls)
        XCTAssertEqual(delay, 0)
        XCTAssertEqual(planner.didFail(), .giveUp)
        XCTAssertEqual(planner.trail, "MPEG-TS ✕ → HLS ✕")
    }

    func testDroppedStreamReconnectsFromTopOfPriority() {
        let planner = FailoverPlanner(candidates: candidates(), maxReconnects: 2)
        _ = planner.start()
        _ = planner.didFail()                 // TS failed → HLS
        planner.didConfirmPlayback()          // HLS plays
        guard case .play(let again, let delay) = planner.didFail() else { return XCTFail("no reconnect") }
        XCTAssertEqual(again.format, .mpegTS, "reconnect must restart at MPEG-TS")
        XCTAssertEqual(delay, 1)
        planner.didConfirmPlayback()
        _ = planner.didFail()
        planner.didConfirmPlayback()
        XCTAssertEqual(planner.reconnects, 0, "a confirmed playback resets the reconnect budget")
    }

    func testReconnectBudgetIsBounded() {
        let planner = FailoverPlanner(candidates: candidates(), maxReconnects: 1)
        _ = planner.start()
        planner.didConfirmPlayback()
        guard case .play = planner.didFail() else { return XCTFail() }
        // TS fails, HLS fails → give up.
        guard case .play(let hls, _) = planner.didFail() else { return XCTFail() }
        XCTAssertEqual(hls.format, .hls)
        XCTAssertEqual(planner.didFail(), .giveUp)
    }
}

final class ServerURLTests: XCTestCase {
    func testNormalisesCommonFormats() {
        XCTAssertEqual(ServerURL.normalize("host.example.com:8080")?.baseURL, "http://host.example.com:8080/")
        XCTAssertEqual(ServerURL.normalize("host.example.com:8080")?.schemeWasGuessed, true)
        XCTAssertEqual(ServerURL.normalize("HTTP://Host.example.com:8080/player_api.php?username=a&password=b")?.baseURL, "http://host.example.com:8080/")
        XCTAssertEqual(ServerURL.normalize("https://a.b.c/sub/get.php?x=1")?.baseURL, "https://a.b.c/sub/")
        XCTAssertEqual(ServerURL.normalize("http://1.2.3.4:25461")?.baseURL, "http://1.2.3.4:25461/")
        XCTAssertEqual(ServerURL.normalize("  http://host.tv/  ")?.baseURL, "http://host.tv/")
    }

    func testRejectsInvalid() {
        XCTAssertNil(ServerURL.normalize(""))
        XCTAssertNil(ServerURL.normalize("ftp://host.com"))
        XCTAssertNil(ServerURL.normalize("not a url"))
        XCTAssertNil(ServerURL.normalize("http://999.1.1.1"))
        XCTAssertNil(ServerURL.normalize("justtext"))
    }

    func testDisplayHostHidesPathAndDefaultPort() {
        XCTAssertEqual(ServerURL.displayHost("http://host.tv:80/sub/"), "http://host.tv")
        XCTAssertEqual(ServerURL.displayHost("http://host.tv:8080/"), "http://host.tv:8080")
    }

    func testPlusSignInPasswordIsEncodedInApiURL() {
        let url = XtreamClient.apiURL(XtreamCredentials(baseURL: "http://h.tv/", username: "a b", password: "x+y&z"), action: "get_live_streams")!
        XCTAssertTrue(url.absoluteString.contains("password=x%2By%26z"), url.absoluteString)
        XCTAssertTrue(url.absoluteString.contains("action=get_live_streams"))
    }
}

final class XtreamParserTests: XCTestCase {

    func json(_ s: String) -> Any { try! LenientJSON.parse(s.data(using: .utf8)!) }

    func testAccountParsingWithInconsistentTypes() throws {
        let account = try XtreamParser.account(json("""
        {"user_info":{"username":"u","auth":"1","status":"Active","exp_date":"1893456000","is_trial":0,
          "active_cons":"1","max_connections":2,"allowed_output_formats":["m3u8","ts"]},
         "server_info":{"timezone":"Africa/Cairo","rtmp_port":"1935","timestamp_now":1700000000}}
        """), baseURL: "http://h/")
        XCTAssertEqual(account.status, .active)
        XCTAssertEqual(account.maxConnections, 2)
        XCTAssertEqual(account.activeConnections, 1)
        XCTAssertEqual(account.allowedOutputFormats, ["m3u8", "ts"])
        XCTAssertEqual(account.expiresAt, Date(timeIntervalSince1970: 1_893_456_000))
        XCTAssertEqual(account.rtmpPort, "1935")
    }

    func testAccountErrors() {
        XCTAssertThrowsError(try XtreamParser.account(json(#"{"user_info":{"auth":0}}"#), baseURL: "")) { XCTAssertEqual($0 as? AkaaError, .invalidCredentials) }
        XCTAssertThrowsError(try XtreamParser.account(json(#"{"user_info":{"auth":1,"status":"Expired"}}"#), baseURL: "")) { XCTAssertEqual($0 as? AkaaError, .accountExpired) }
        XCTAssertThrowsError(try XtreamParser.account(json("[]"), baseURL: "")) { XCTAssertEqual($0 as? AkaaError, .invalidCredentials) }
        XCTAssertThrowsError(try XtreamParser.account(json(#"{"hello":1}"#), baseURL: "")) { XCTAssertEqual($0 as? AkaaError, .notXtreamServer) }
    }

    func testEmptyListShapes() {
        for shape in ["{}", "null", "false", "[]"] {
            XCTAssertTrue(XtreamParser.channels(json(shape)).isEmpty, shape)
        }
    }

    func testStreamsWithStringNumbersAndDuplicates() {
        let channels = XtreamParser.channels(json("""
        [{"num":"1","name":"beIN SPORTS 1 HD","stream_id":"101","stream_icon":"http://img/1.png","category_id":5},
         {"num":2,"name":"Dup","stream_id":101},
         {"name":"No id"},
         {"num":3,"name":"MBC","stream_id":102.0,"stream_icon":"","is_adult":"0"}]
        """))
        XCTAssertEqual(channels.map { $0.id }, [101, 102])
        XCTAssertEqual(channels[0].categoryID, "5")
        XCTAssertEqual(channels[0].quality, "HD")
        XCTAssertNil(channels[1].icon)

        let movies = XtreamParser.movies(json("""
        [{"stream_id":7,"name":"Shadow of the Castle (2026) 4K","rating":"8.7","added":"1762646400","container_extension":"mkv"},
         {"stream_id":8,"name":"Five","rating_5based":4.1}]
        """))
        XCTAssertEqual(movies[0].year, 2026)
        XCTAssertEqual(movies[0].rating, 8.7)
        XCTAssertEqual(movies[0].quality, "4K")
        XCTAssertEqual(movies[1].rating, 8.2)
    }

    func testSeriesEpisodesBothShapes() {
        let mapShape = XtreamParser.parseEpisodes(seriesID: 1, json("""
        {"2":[{"id":"21","episode_num":"1","title":"S2E1","container_extension":"mp4","info":{"duration_secs":"2700"}}],
         "1":[{"id":"12","episode_num":2,"title":"S1E2"},{"id":"11","episode_num":1,"title":"S1E1"}]}
        """))
        XCTAssertEqual(mapShape.keys.sorted(), [1, 2])
        XCTAssertEqual(mapShape[1]?.map { $0.id }, ["11", "12"])
        XCTAssertEqual(mapShape[2]?.first?.durationSeconds, 2700)

        let listShape = XtreamParser.parseEpisodes(seriesID: 1, json("""
        [[{"id":"1","season":1,"episode_num":1}],[{"id":"2","season":2,"episode_num":1}]]
        """))
        XCTAssertEqual(listShape.keys.sorted(), [1, 2])

        let seasons = XtreamParser.parseSeasons(json(#"[{"season_number":1,"name":"Season 1"},{"season_number":9,"name":"Empty"}]"#), episodes: mapShape)
        XCTAssertEqual(seasons.map { $0.number }, [1, 2], "seasons without episodes are dropped, episode-only seasons added")
    }

    func testShortEPGDecodesBase64() {
        let title = Data("الدوري الأوروبي".utf8).base64EncodedString()
        let entries = XtreamParser.shortEPG(json("""
        {"epg_listings":[{"title":"\(title)","description":"","start_timestamp":"1700000000","stop_timestamp":"1700003600"}]}
        """))
        XCTAssertEqual(entries.first?.title, "الدوري الأوروبي")
        XCTAssertEqual(entries.first.map { $0.end.timeIntervalSince($0.start) }, 3600)
    }
}

final class TextAndRulesTests: XCTestCase {
    func testArabicNormalisation() {
        XCTAssertEqual(ContentText.normalize("  أفلامٌ   جديدة "), "افلام جديده")
        XCTAssertEqual(ContentText.normalize("Élan"), "elan")
    }

    func testCategoryClassifier() {
        XCTAssertEqual(CategoryClassifier.classify("AR | beIN Sports"), .sports)
        XCTAssertEqual(CategoryClassifier.classify("قنوات أطفال"), .kids)
        XCTAssertEqual(CategoryClassifier.classify("Egypt Channels"), .arabic)
        XCTAssertEqual(CategoryClassifier.classify("FR | Général"), .international)
        XCTAssertTrue(CategoryClassifier.isAdult("XXX Adults"))
        XCTAssertEqual(CategoryClassifier.flagCountry("قنوات مصر"), "EG")
        XCTAssertEqual(CategoryClassifier.flagEmoji("EG"), "🇪🇬")
    }

    func testAgeRatingAndDurations() {
        XCTAssertEqual(AgeRating.minimumAge("PG-13"), 13)
        XCTAssertEqual(AgeRating.minimumAge("16+"), 16)
        XCTAssertEqual(AgeRating.minimumAge("FSK 12"), 12)
        XCTAssertTrue(AgeRating.isRestricted("R", maxAllowedAge: 13))
        XCTAssertEqual(TimeFormat.parseDuration("02:14:36"), 8076)
        XCTAssertEqual(TimeFormat.parseDuration("45 min"), 2700)
        XCTAssertEqual(TimeFormat.clock(8076), "02:14:36")
        XCTAssertEqual(TimeFormat.clock(65), "01:05")
        XCTAssertEqual(TimeFormat.displayDate("2026-11-08"), "08-11-2026")
        XCTAssertEqual(TimeFormat.displayDate("2026-11-08 20:00:00"), "08-11-2026")
        XCTAssertEqual(TimeFormat.displayDate("Nov 2026"), "Nov 2026")
    }

    func testContinueWatchingRules() {
        XCTAssertNil(ContinueWatchingRules.resumePosition(position: 10, duration: 3000))
        XCTAssertEqual(ContinueWatchingRules.resumePosition(position: 600, duration: 3000), 600)
        XCTAssertTrue(ContinueWatchingRules.isFinished(position: 2950, duration: 3000))
    }

    func testDisplayTitles() {
        XCTAssertEqual(ContentText.displayTitles("AR | ظل القلعة - Shadow of the Castle").primary, "ظل القلعة")
        XCTAssertEqual(ContentText.displayTitles("Shadow of the Castle | ظل القلعة").secondary, "Shadow of the Castle")
    }

    func testPlaylistValidation() {
        let existing = [Playlist(id: 1, name: "A", serverURL: "http://h.tv:8080/", username: "u", createdAt: Date(), updatedAt: Date(), lastSync: nil, isEnabled: true, isDefault: true, sortOrder: 0, status: .connected, liveCount: 0, movieCount: 0, seriesCount: 0, account: nil)]
        XCTAssertEqual(PlaylistValidator.validate(PlaylistInput(name: "", username: "", password: "", url: "x"), existing: []), [.nameMissing, .usernameMissing, .passwordMissing, .urlInvalid])
        XCTAssertEqual(PlaylistValidator.validate(PlaylistInput(name: "B", username: "u", password: "p", url: "h.tv:8080"), existing: existing), [.duplicate])
        XCTAssertEqual(PlaylistValidator.validate(PlaylistInput(name: "B", username: "u", password: "", url: "h.tv:8080"), existing: existing, editingID: 1, passwordOptional: true), [])
    }
}

final class CryptoTests: XCTestCase {
    func testSHA256KnownVector() {
        XCTAssertEqual(SHA256Digest.hex(SHA256Digest.hash(Array("abc".utf8))), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        XCTAssertEqual(SHA256Digest.hex(SHA256Digest.hash([])), "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    }

    func testPBKDF2KnownVector() {
        // RFC 7914 §11, PBKDF2-HMAC-SHA256 (P="passwd", S="salt", c=1, dkLen=64)
        let dk = SHA256Digest.pbkdf2(password: Array("passwd".utf8), salt: Array("salt".utf8), iterations: 1, length: 64)
        XCTAssertEqual(SHA256Digest.hex(dk), "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783")
    }

    func testPinHashRoundTrip() {
        let stored = PinHasher.hash("1234", iterations: 500)
        XCTAssertTrue(PinHasher.verify("1234", stored: stored))
        XCTAssertFalse(PinHasher.verify("4321", stored: stored))
        XCTAssertFalse(stored.contains("1234"))
        XCTAssertTrue(PinHasher.isValidPIN("0000"))
        XCTAssertFalse(PinHasher.isValidPIN("12a4"))
    }
}

final class DatabaseTests: XCTestCase {

    private func makeDB() throws -> (AppDatabase, Playlist) {
        let db = try AppDatabase.inMemory()
        let p = try db.playlists.insert(name: "Nova IPTV", serverURL: "http://h.tv/", username: "u", account: nil, status: .connected)
        return (db, p)
    }

    private func movie(_ id: Int, _ name: String, cat: String = "1", rating: Double? = nil, adult: Bool = false) -> Movie {
        Movie(id: id, number: id, name: name, poster: nil, rating: rating, added: Date(timeIntervalSince1970: TimeInterval(1_700_000_000 + id)), categoryID: cat, containerExtension: "mp4", year: 2020 + id % 5, genre: "Action, Drama", plot: nil, isAdult: adult)
    }

    func testFirstPlaylistIsDefaultAndDeleteCascades() throws {
        let (db, p) = try makeDB()
        XCTAssertTrue(p.isDefault)
        let p2 = try db.playlists.insert(name: "Second", serverURL: "http://x.tv/", username: "v", account: nil, status: .unknown)
        XCTAssertFalse(p2.isDefault)
        _ = try db.catalog.replace(playlistID: p.id, categories: [:], channels: nil, movies: [movie(1, "A")], series: nil)
        try db.userData.toggleFavorite(p.id, .movie, "1")
        try db.playlists.delete(p.id)
        XCTAssertEqual(try db.playlists.defaultPlaylist()?.id, p2.id)
        XCTAssertEqual(try db.catalog.counts(playlistID: p.id).movies, 0)
        XCTAssertTrue(try db.userData.favoriteIDs(p.id, .movie).isEmpty)
    }

    func testCatalogQueriesFiltersAndNewContent() throws {
        let (db, p) = try makeDB()
        let cats: [ContentType: [ContentCategory]] = [.movie: [ContentCategory(id: "1", name: "أفلام أكشن", type: .movie, sortIndex: 0),
                                                             ContentCategory(id: "9", name: "Adults XXX", type: .movie, sortIndex: 1)]]
        var summary = try db.catalog.replace(playlistID: p.id, categories: cats, channels: nil,
                                             movies: [movie(1, "ظل القلعة 4K", rating: 8.7), movie(2, "Fire Line", rating: 8.2), movie(3, "Hidden", cat: "9", adult: false)], series: nil)
        XCTAssertEqual(summary.newMovies, 0, "first sync never reports everything as new")
        summary = try db.catalog.replace(playlistID: p.id, categories: cats, channels: nil,
                                         movies: [movie(1, "ظل القلعة 4K", rating: 8.7), movie(2, "Fire Line", rating: 8.2), movie(3, "Hidden", cat: "9"), movie(4, "New One")], series: nil)
        XCTAssertEqual(summary.newMovies, 1)

        var f = BrowseFilter()
        f.excludeAdult = true
        XCTAssertEqual(try db.catalog.movies(playlistID: p.id, filter: f).map { $0.id }, [1, 2, 4], "adult category excluded")
        f.search = "القلعه"
        XCTAssertEqual(try db.catalog.movies(playlistID: p.id, filter: f).map { $0.id }, [1], "normalised Arabic search")
        f.search = ""
        f.sort = .rating
        XCTAssertEqual(try db.catalog.movies(playlistID: p.id, filter: f).first?.id, 1)
        f.quality = "4K"
        XCTAssertEqual(try db.catalog.movies(playlistID: p.id, filter: f).map { $0.id }, [1])
        f.quality = nil
        f.hidden.keywords = ["fire"]
        XCTAssertEqual(try db.catalog.movies(playlistID: p.id, filter: f).map { $0.id }, [1, 4])

        let categories = try db.catalog.categories(playlistID: p.id, type: .movie)
        XCTAssertEqual(categories.map { $0.id }, ["1"])
        XCTAssertEqual(categories.first?.count, 3)
        XCTAssertEqual(try db.catalog.genres(playlistID: p.id, type: .movie).prefix(2).sorted(), ["Action", "Drama"])
    }

    func testProgressContinueWatchingAndMostWatched() throws {
        let (db, p) = try makeDB()
        let now = Date()
        try db.userData.saveProgress(p.id, WatchProgress(type: .movie, itemID: "1", title: "A", image: nil, position: 600, duration: 3000, updatedAt: now, seriesID: nil, season: nil, episodeNumber: nil, containerExtension: "mp4", playCount: 0), countPlay: true)
        try db.userData.saveProgress(p.id, WatchProgress(type: .episode, itemID: "11", title: "S1E1", image: nil, position: 300, duration: 2700, updatedAt: now.addingTimeInterval(-10), seriesID: 5, season: 1, episodeNumber: 1, containerExtension: nil, playCount: 0), countPlay: true)
        try db.userData.saveProgress(p.id, WatchProgress(type: .episode, itemID: "12", title: "S1E2", image: nil, position: 900, duration: 2700, updatedAt: now.addingTimeInterval(5), seriesID: 5, season: 1, episodeNumber: 2, containerExtension: nil, playCount: 0), countPlay: true)
        try db.userData.saveProgress(p.id, WatchProgress(type: .movie, itemID: "2", title: "Done", image: nil, position: 2990, duration: 3000, updatedAt: now, seriesID: nil, season: nil, episodeNumber: nil, containerExtension: nil, playCount: 0))
        let cw = try db.userData.continueWatching(p.id)
        XCTAssertEqual(cw.map { $0.itemID }, ["12", "1"], "one entry per series, finished items excluded")
        try db.userData.saveProgress(p.id, WatchProgress(type: .movie, itemID: "1", title: "A", image: nil, position: 700, duration: 0, updatedAt: now, seriesID: nil, season: nil, episodeNumber: nil, containerExtension: nil, playCount: 0), countPlay: true)
        XCTAssertEqual(db.userData.progress(p.id, .movie, "1")?.duration, 3000, "unknown duration keeps the stored one")
        XCTAssertEqual(db.userData.progress(p.id, .movie, "1")?.playCount, 2)
        XCTAssertEqual(try db.userData.mostWatchedIDs(p.id, .movie).first, "1")
        XCTAssertEqual(try db.userData.mostWatchedSeriesIDs(p.id), ["5"])
    }

    func testGroupsHiddenAndBackupRoundTrip() throws {
        let (db, p) = try makeDB()
        let secrets = InMemorySecretStore()
        try secrets.setPassword("secret", for: p.id)
        let g = try db.userData.createGroup(p.id, name: "Weekend")
        try db.userData.setInGroup(g.id, .live, "101", true)
        try db.userData.setInGroup(g.id, .movie, "7", true)
        try db.userData.toggleFavorite(p.id, .series, "5")
        try db.userData.setHidden(p.id, kind: "keyword", value: "news", hidden: true)
        var settings = AppSettings()
        settings.parentalPINHash = PinHasher.hash("1234", iterations: 10)
        settings.language = .english

        let withoutCreds = try BackupManager.export(database: db, secrets: secrets, settings: settings, includeCredentials: false)
        XCTAssertFalse(String(data: withoutCreds, encoding: .utf8)!.contains("secret"))
        XCTAssertFalse(String(data: withoutCreds, encoding: .utf8)!.contains("pbkdf2"))

        let data = try BackupManager.export(database: db, secrets: secrets, settings: settings, includeCredentials: true)
        let target = try AppDatabase.inMemory()
        let targetSecrets = InMemorySecretStore()
        let result = try BackupManager.restore(data, database: target, secrets: targetSecrets, currentSettings: AppSettings(), restoreSettings: true)
        XCTAssertEqual(result.playlistsAdded, 1)
        XCTAssertEqual(result.settings.language, .english)
        let restored = try target.playlists.all()[0]
        XCTAssertEqual(targetSecrets.password(for: restored.id), "secret")
        XCTAssertEqual(try target.userData.favoriteIDs(restored.id, .series), ["5"])
        let rg = try target.userData.groups(restored.id)
        XCTAssertEqual(rg.map { $0.name }, ["Weekend"])
        XCTAssertEqual(try target.userData.groupItemIDs(rg[0].id, .live), ["101"])
        XCTAssertEqual(try target.userData.hiddenContent(restored.id).keywords, ["news"])

        // Restoring twice merges instead of duplicating.
        let again = try BackupManager.restore(data, database: target, secrets: targetSecrets, currentSettings: AppSettings(), restoreSettings: false)
        XCTAssertEqual(again.playlistsMerged, 1)
        XCTAssertEqual(try target.playlists.all().count, 1)
    }

    func testEpisodeNavigatorCrossesSeasons() {
        let s = Series(id: 1, number: 1, name: "S", cover: nil, plot: nil, cast: nil, director: nil, genre: nil, releaseDate: nil, lastModified: nil, rating: nil, backdrop: nil, episodeRunTime: nil, categoryID: nil, year: nil, isAdult: false)
        func ep(_ id: String, _ season: Int, _ n: Int) -> Episode { Episode(id: id, seriesID: 1, season: season, episodeNumber: n, title: id, plot: nil, thumbnail: nil, durationSeconds: 2700, containerExtension: "mp4", rating: nil, releaseDate: nil) }
        let details = SeriesDetails(series: s, originalName: nil, cast: nil, director: nil, releaseDate: nil, ageRating: nil, country: nil, language: nil,
                                    seasons: [], episodesBySeason: [1: [ep("a", 1, 1), ep("b", 1, 2)], 2: [ep("c", 2, 1)]])
        XCTAssertEqual(EpisodeNavigator.next(after: ep("b", 1, 2), in: details)?.id, "c")
        XCTAssertNil(EpisodeNavigator.next(after: ep("c", 2, 1), in: details))
        XCTAssertEqual(EpisodeNavigator.previous(before: ep("c", 2, 1), in: details)?.id, "b")
        let finished = WatchProgress(type: .episode, itemID: "a", title: "a", image: nil, position: 2690, duration: 2700, updatedAt: Date(), seriesID: 1, season: 1, episodeNumber: 1, containerExtension: nil, playCount: 1)
        XCTAssertEqual(EpisodeNavigator.resumeTarget(details: details, progress: [finished])?.0.id, "b")
    }
}

/// Exercises XtreamClient end-to-end through a stub transport (no network).
final class XtreamClientTests: XCTestCase {

    final class StubTransport: HTTPTransport {
        var responses: [String: (String, Int)] = [:]
        var requested: [URL] = []
        func get(_ url: URL, timeout: TimeInterval) async throws -> (Data, Int) {
            requested.append(url)
            let action = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "action" })?.value ?? "auth"
            if url.scheme == "https" && responses["https"] == nil { throw AkaaError.unreachable }
            let (body, code) = responses[action] ?? ("[]", 200)
            return (Data(body.utf8), code)
        }
    }

    func testAuthenticateFallsBackToHttpWhenSchemeGuessed() async throws {
        let stub = StubTransport()
        stub.responses["auth"] = (#"{"user_info":{"auth":1,"status":"Active"},"server_info":{}}"#, 200)
        let client = XtreamClient(transport: stub)
        let (creds, account) = try await client.authenticate(serverInput: "panel.example:8080", username: "u", password: "p")
        XCTAssertEqual(creds.baseURL, "http://panel.example:8080/")
        XCTAssertEqual(account.status, .active)
        XCTAssertEqual(stub.requested.first?.scheme, "https", "HTTPS is attempted first")
    }

    func testHttpErrorsMapToFriendlyErrors() async {
        let stub = StubTransport()
        stub.responses["auth"] = ("", 403)
        let client = XtreamClient(transport: stub)
        do {
            _ = try await client.authenticate(XtreamCredentials(baseURL: "http://h/", username: "u", password: "p"))
            XCTFail("expected error")
        } catch {
            XCTAssertEqual(error as? AkaaError, .invalidCredentials)
        }
    }

    func testSyncKeepsSectionsThatFail() async throws {
        let stub = StubTransport()
        stub.responses["get_live_streams"] = (#"[{"stream_id":1,"name":"One"}]"#, 200)
        stub.responses["get_vod_streams"] = ("", 500)
        stub.responses["get_series"] = (#"[{"series_id":3,"name":"S"}]"#, 200)
        let db = try AppDatabase.inMemory()
        let p = try db.playlists.insert(name: "P", serverURL: "http://h/", username: "u", account: nil, status: .connected)
        _ = try db.catalog.replace(playlistID: p.id, categories: [:], channels: nil, movies: [Movie(id: 9, number: 1, name: "Old", poster: nil, rating: nil, added: nil, categoryID: nil, containerExtension: nil, year: nil, genre: nil, plot: nil, isAdult: false)], series: nil)
        let summary = try await CatalogSyncer(client: XtreamClient(transport: stub), database: db)
            .sync(playlist: p, credentials: XtreamCredentials(baseURL: "http://h/", username: "u", password: "p"))
        XCTAssertEqual(summary.failedSections, [.movie])
        XCTAssertEqual(summary.counts, CatalogCounts(live: 1, movies: 1, series: 1))
        XCTAssertEqual(try db.playlists.get(p.id)?.liveCount, 1)
    }
}

final class PlaylistLinkParserTests: XCTestCase {
    func testGetPhpLink() {
        let input = PlaylistLinkParser.parse("http://panel.example.com:8080/get.php?username=ali&password=p%2Bw&type=m3u_plus&output=ts")
        XCTAssertEqual(input?.username, "ali")
        XCTAssertEqual(input?.password, "p+w")
        XCTAssertEqual(input?.url, "http://panel.example.com:8080/")
        XCTAssertEqual(input?.name, "panel.example.com")
    }

    func testJSONAndPipeFormats() {
        let json = PlaylistLinkParser.parse(#"{"name":"Nova IPTV","url":"nova.example.tv:80","username":"u1","password":"p1"}"#)
        XCTAssertEqual(json, PlaylistInput(name: "Nova IPTV", username: "u1", password: "p1", url: "nova.example.tv:80"))
        let pipe = PlaylistLinkParser.parse("http://h.example.tv|user|pass")
        XCTAssertEqual(pipe?.username, "user")
        XCTAssertEqual(pipe?.password, "pass")
        XCTAssertNil(PlaylistLinkParser.parse("hello world"))
        XCTAssertNil(PlaylistLinkParser.parse("https://example.com/page"))
    }
}
