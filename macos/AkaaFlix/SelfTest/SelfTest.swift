import AppKit
import SwiftUI

/// Command-line options for the automated self-test used by CI on every runner (Intel and Apple
/// Silicon). Example:
///   "Akaa Flix.app/Contents/MacOS/Akaa Flix" --self-test --self-test-server=http://127.0.0.1:8765/ \
///       --self-test-report=report.json --self-test-data=/tmp/akaa --self-test-fixtures=macos/SelfTest/fixtures
struct SelfTestArguments {
    var server: String
    var reportPath: String
    var dataDirectory: URL
    var screenshotDirectory: URL?
    var fixtures: URL?

    init?(arguments: [String]) {
        guard arguments.contains("--self-test") else { return nil }
        func value(_ name: String) -> String? {
            arguments.first(where: { $0.hasPrefix("--\(name)=") }).map { String($0.dropFirst(name.count + 3)) }
        }
        server = value("self-test-server") ?? "http://127.0.0.1:8765/"
        reportPath = value("self-test-report") ?? NSTemporaryDirectory() + "akaaflix-selftest.json"
        dataDirectory = URL(fileURLWithPath: value("self-test-data") ?? NSTemporaryDirectory() + "akaaflix-selftest-\(ProcessInfo.processInfo.processIdentifier)")
        screenshotDirectory = value("self-test-screenshots").map { URL(fileURLWithPath: $0) }
        fixtures = value("self-test-fixtures").map { URL(fileURLWithPath: $0) }
    }
}

/// Runs end-to-end checks inside the real app bundle and writes a JSON report.
final class SelfTestRunner {

    struct Check: Codable {
        var name: String
        var passed: Bool
        var detail: String
        var seconds: Double
        var required: Bool
    }

    let args: SelfTestArguments
    let model: AppModel
    private var checks: [Check] = []
    private var environment: [String: String] = [:]
    private let started = Date()

    init(arguments: SelfTestArguments, model: AppModel) {
        self.args = arguments
        self.model = model
    }

    private func log(_ s: String) {
        FileHandle.standardError.write(("[self-test] " + s + "\n").data(using: .utf8)!)
    }

    func run() {
        Task { await runAll() }
    }

    // MARK: Helpers

    private func record(_ name: String, required: Bool = true, _ body: () async throws -> String) async {
        let t0 = Date()
        do {
            let detail = try await body()
            checks.append(Check(name: name, passed: true, detail: detail, seconds: Date().timeIntervalSince(t0), required: required))
            log("PASS \(name) — \(detail)")
        } catch {
            let detail = (error as? SelfTestFailure)?.message ?? AkaaError.from(error).message
            checks.append(Check(name: name, passed: false, detail: detail, seconds: Date().timeIntervalSince(t0), required: required))
            log("FAIL \(name) — \(detail)")
        }
    }

    struct SelfTestFailure: Error { let message: String }

    private func fail(_ m: String) -> SelfTestFailure { SelfTestFailure(message: m) }

    private func sleep(_ seconds: Double) async {
        try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
    }

    /// Polls a main-thread condition until true or timeout.
    private func waitFor(_ timeout: Double, _ condition: @escaping () -> Bool) async -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            let ok = await MainActor.run { condition() }
            if ok { return true }
            await sleep(0.25)
        }
        return await MainActor.run { condition() }
    }

    @discardableResult
    private func onMain<T>(_ work: @escaping () -> T) async -> T {
        await MainActor.run { work() }
    }

    private func snapshot(_ name: String) async {
        guard let dir = args.screenshotDirectory else { return }
        await onMain {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            guard let window = NSApp.windows.first(where: { $0.isVisible && $0.styleMask.contains(.titled) }),
                  let view = window.contentView, let rep = view.bitmapImageRepForCachingDisplay(in: view.bounds) else { return }
            view.cacheDisplay(in: view.bounds, to: rep)
            if let data = rep.representation(using: .jpeg, properties: [.compressionFactor: 0.82]) {
                try? data.write(to: dir.appendingPathComponent("\(name).jpg"))
            }
        }
    }

    private func navigate(_ route: Route, settle: Double = 2.5, shot: String? = nil) async {
        await onMain { self.model.route = route }
        await sleep(settle)
        if let s = shot { await snapshot(s) }
    }

    // MARK: Checks

    private func runAll() async {
        environment = await onMain {
            [
                "binaryArchitecture": SystemInfo.binaryArchitecture,
                "hardwareArchitecture": SystemInfo.hardwareArchitecture,
                "rosetta": SystemInfo.isTranslatedByRosetta ? "yes" : "no",
                "macOS": SystemInfo.macOSVersion,
                "macOSName": SystemInfo.macOSName,
                "model": SystemInfo.modelIdentifier,
                "cpu": SystemInfo.cpuName,
                "memoryGB": String(SystemInfo.physicalMemoryGB),
                "engine": EngineFactory.primaryEngineName,
                "engineVersion": EngineFactory.primaryEngineVersion,
                "appVersion": BrandInfo.appVersion,
                "build": BrandInfo.build,
            ]
        }
        log("environment: \(environment)")

        await onMain {
            self.model.updateSettings {
                $0.language = .arabic
                $0.autoPlayNextEpisode = true
                $0.nextEpisodeCountdown = 3
                $0.reconnectTimeoutSeconds = 8
                $0.showPlaybackInfo = true
            }
            NSApp.activate(ignoringOtherApps: true)
            if let w = NSApp.windows.first(where: { $0.styleMask.contains(.titled) }) {
                w.setFrame(NSRect(x: 0, y: 0, width: 1670, height: 980), display: true)
                w.center()
                w.makeKeyAndOrderFront(nil)
            }
        }
        await sleep(1)
        await snapshot("00-welcome-add-playlist")

        await record("Application startup") {
            let ok = await onMain { NSApp.windows.contains { $0.isVisible } }
            guard ok else { throw fail("no visible window") }
            return "window visible, data dir \(args.dataDirectory.path)"
        }

        await record("Native architecture (no Rosetta)") {
            guard !SystemInfo.isTranslatedByRosetta else { throw fail("running under Rosetta translation") }
            return "\(SystemInfo.binaryArchitecture) slice on \(SystemInfo.hardwareArchitecture) hardware"
        }

        // Xtream Codes authentication + playlist management
        var playlist: Playlist?
        await record("Xtream Codes authentication") {
            let p = try await model.addPlaylist(PlaylistInput(name: "Nova IPTV", username: "test", password: "test", url: args.server))
            playlist = p
            guard p.account?.status == .active else { throw fail("unexpected status \(String(describing: p.account?.status))") }
            return "account active, max connections \(p.account?.maxConnections ?? 0)"
        }
        await record("Wrong credentials are rejected") {
            do {
                _ = try await model.testConnection(PlaylistInput(name: "x", username: "test", password: "wrong", url: args.server))
                throw fail("wrong password accepted")
            } catch let e as AkaaError {
                guard e == .invalidCredentials else { throw fail("unexpected error \(e)") }
                return "invalid credentials reported"
            }
        }
        guard let p = playlist else { return await finish() }

        await record("Catalog sync (live, movies, series)") {
            guard let creds = model.credentials(for: p) else { throw fail("no credentials stored") }
            let summary = try await CatalogSyncer(client: model.client, database: model.database).sync(playlist: p, credentials: creds)
            await onMain { self.model.reloadPlaylists(); self.model.activate(self.model.playlists.first { $0.id == p.id } ?? p) }
            guard summary.counts.live > 0, summary.counts.movies > 0, summary.counts.series > 0 else { throw fail("empty catalog \(summary.counts)") }
            return "\(summary.counts.live) channels, \(summary.counts.movies) movies, \(summary.counts.series) series"
        }

        await record("Database persistence (reopen from disk)") {
            try model.database.userData.toggleFavorite(p.id, .movie, "101")
            try model.database.userData.saveProgress(p.id, WatchProgress(type: .movie, itemID: "102", title: "probe", image: nil, position: 120, duration: 600, updatedAt: Date(), seriesID: nil, season: nil, episodeNumber: nil, containerExtension: "mp4", playCount: 0))
            let reopened = try AppDatabase(path: model.paths.database.path)
            defer { reopened.db.close() }
            guard reopened.userData.isFavorite(p.id, .movie, "101") else { throw fail("favorite not persisted") }
            guard reopened.userData.progress(p.id, .movie, "102")?.position == 120 else { throw fail("progress not persisted") }
            guard try reopened.catalog.counts(playlistID: p.id).movies > 0 else { throw fail("catalog not persisted") }
            try model.database.userData.removeProgress(p.id, .movie, "102")
            return "favorites, progress and catalog survive reopening \(model.paths.database.lastPathComponent)"
        }

        // UI screenshots of every approved screen with mock data.
        await navigate(.home, settle: 4, shot: "01-home")
        await navigate(.live, settle: 3, shot: "02-live-tv")
        await record("Live TV plays MPEG-TS first") {
            guard let c = try model.database.catalog.channel(playlistID: p.id, id: 1) else { throw fail("channel 1 missing") }
            let list = try model.database.catalog.channels(playlistID: p.id, filter: BrowseFilter())
            await onMain { self.model.play(.live(c, list: list)) }
            let ok = await waitFor(25) { self.model.player?.confirmedAt != nil }
            let format = await onMain { self.model.player?.activeFormat }
            let trail = await onMain { self.model.player?.attemptTrail ?? "" }
            guard ok else { throw fail("live stream did not start (\(trail))") }
            guard format == .mpegTS else { throw fail("expected MPEG-TS, got \(format?.displayName ?? "nil")") }
            return "playing as MPEG-TS · \(trail)"
        }
        await onMain { self.model.closePlayer() }

        await record("HLS fallback when MPEG-TS fails") {
            guard let c = try model.database.catalog.channel(playlistID: p.id, id: 2) else { throw fail("channel 2 missing") }
            await onMain { self.model.play(.live(c, list: [c])) }
            let ok = await waitFor(35) { self.model.player?.confirmedAt != nil }
            let format = await onMain { self.model.player?.activeFormat }
            let trail = await onMain { self.model.player?.attemptTrail ?? "" }
            guard ok else { throw fail("fallback did not play (\(trail))") }
            guard format == .hls, trail.hasPrefix("MPEG-TS ✕") else { throw fail("expected TS failure then HLS, got \(trail)") }
            return trail
        }
        await sleep(1.5)
        await onMain { self.model.player?.pokeControls() }
        await sleep(0.5)
        await snapshot("03-live-player")

        await record("Live channel switching") {
            let before = await onMain { self.model.player?.currentChannel?.id }
            await onMain {
                if let c = try? self.model.database.catalog.channel(playlistID: p.id, id: 1),
                   let list = try? self.model.database.catalog.channels(playlistID: p.id, filter: BrowseFilter()) {
                    self.model.play(.live(c, list: list))
                    self.model.player?.switchChannel(by: 1)
                }
            }
            let ok = await waitFor(25) { self.model.player?.confirmedAt != nil }
            let after = await onMain { self.model.player?.currentChannel?.id }
            guard ok, after != nil, after != before || before == nil else { throw fail("channel switch failed") }
            return "switched to channel \(after ?? 0)"
        }
        await onMain { self.model.closePlayer() }

        await navigate(.movies, settle: 3, shot: "04-movies")
        if let m = try? model.database.catalog.movie(playlistID: p.id, id: 101) {
            await navigate(.movieDetails(m), settle: 3.5, shot: "05-movie-details")
        }

        await record("Movie playback, audio tracks and subtitles") {
            guard let m = try model.database.catalog.movie(playlistID: p.id, id: 101) else { throw fail("movie missing") }
            await onMain { self.model.play(.movie(m, details: nil, resumeAt: nil)) }
            let ok = await waitFor(25) { self.model.player?.confirmedAt != nil }
            let startTrail = await onMain { self.model.player?.attemptTrail ?? "" }
            guard ok else { throw fail("movie did not start: \(startTrail)") }
            await sleep(2)
            let audio = await onMain { self.model.player?.engine.audioTracks.count ?? 0 }
            var subtitleDetail = "no fixture"
            if let srt = args.fixtures?.appendingPathComponent("movie.srt") {
                let added = await onMain { self.model.player?.engine.addExternalSubtitle(srt) ?? false }
                await sleep(1.5)
                let subs = await onMain { self.model.player?.engine.subtitleTracks.count ?? 0 }
                guard added || subs > 0 else { throw fail("external subtitle not loaded") }
                subtitleDetail = "\(subs) subtitle track(s)"
            }
            guard audio >= 2 else { throw fail("expected 2 audio tracks, found \(audio)") }
            let second = await onMain { self.model.player?.engine.audioTracks.last?.id ?? -1 }
            await onMain { self.model.player?.selectAudio(second) }
            await sleep(0.5)
            let selected = await onMain { self.model.player?.selectedAudio ?? -2 }
            guard selected == second else { throw fail("audio selection failed") }
            return "\(audio) audio tracks (switched), \(subtitleDetail)"
        }
        await record("Seek and Continue Watching") {
            await onMain { self.model.player?.seek(to: 40) }
            let moved = await waitFor(10) { (self.model.player?.currentTime ?? 0) >= 35 }
            await onMain { self.model.player?.persistProgress() }
            let cw = try model.database.userData.continueWatching(p.id)
            guard moved else { throw fail("seek did not move playback") }
            guard cw.contains(where: { $0.type == .movie && $0.itemID == "101" }) else { throw fail("movie missing from Continue Watching") }
            let position = await onMain { self.model.player?.currentTime ?? 0 }
            return "position \(Int(position))s saved; \(cw.count) item(s) in Continue Watching"
        }
        await onMain { self.model.player?.pokeControls(); self.model.player?.openMenu = .quality }
        await sleep(0.8)
        await snapshot("06-movie-player")
        await onMain { self.model.player?.openMenu = nil }

        await record("Full-screen mode", required: false) {
            await onMain { self.model.player?.toggleFullScreen() }
            let entered = await waitFor(6) { self.model.player?.isFullScreen ?? false }
            await onMain { if self.model.player?.isFullScreen == true { self.model.player?.toggleFullScreen() } }
            _ = await waitFor(6) { !(self.model.player?.isFullScreen ?? false) }
            guard entered else { throw fail("window did not enter full screen (CI display limitation possible)") }
            return "entered and left full screen"
        }
        await onMain { self.model.closePlayer() }

        await navigate(.series, settle: 3, shot: "07-series")
        if let s = try? model.database.catalog.series(playlistID: p.id, id: 501) {
            await navigate(.seriesDetails(s), settle: 4, shot: "08-series-details")
        }

        await record("Series playback and automatic next episode") {
            guard let s = try model.database.catalog.series(playlistID: p.id, id: 501), let creds = model.credentials(for: p) else { throw fail("series missing") }
            let details = try await model.client.seriesDetails(creds, series: s)
            guard let first = details.allEpisodes.first, let second = EpisodeNavigator.next(after: first, in: details) else { throw fail("episodes missing") }
            await onMain { self.model.play(.episode(first, series: s, details: details, resumeAt: nil)) }
            guard await waitFor(25, { self.model.player?.confirmedAt != nil }) else { throw fail("episode did not start") }
            await onMain { self.model.player?.showEpisodes = true; self.model.player?.pokeControls() }
            await sleep(1)
            await snapshot("09-series-player")
            await onMain { self.model.player?.showEpisodes = false; self.model.player?.simulateEndForTesting() }
            let advanced = await waitFor(15) { self.model.player?.currentEpisode?.id == second.id && self.model.player?.confirmedAt != nil }
            guard advanced else { throw fail("did not advance to \(second.id)") }
            return "advanced \(first.id) → \(second.id) after countdown"
        }
        await onMain { self.model.closePlayer() }

        await navigate(.settings(.general), shot: "10-settings")
        await navigate(.settings(.player), shot: "11-settings-player")
        await navigate(.settings(.playlists), shot: "12-playlists")
        await navigate(.playlistForm(nil), shot: "13-add-playlist")
        await navigate(.playlistForm(p.id), shot: "14-edit-playlist")
        await navigate(.settings(.about), shot: "15-about")
        await navigate(.settings(.account), shot: "16-account")
        await onMain { self.model.updateSettings { $0.language = .english } }
        await navigate(.home, settle: 3, shot: "17-home-english")
        await onMain { self.model.updateSettings { $0.language = .arabic } }

        await record("Window resizing and small screens") {
            await onMain {
                if let w = NSApp.windows.first(where: { $0.styleMask.contains(.titled) }) {
                    w.setFrame(NSRect(x: 0, y: 0, width: 1100, height: 680), display: true)
                }
                self.model.route = .movies
            }
            await sleep(2)
            await snapshot("18-movies-1100x680")
            let size = await onMain { NSApp.windows.first(where: { $0.styleMask.contains(.titled) })?.frame.size ?? .zero }
            guard size.width >= 1099 else { throw fail("window did not resize (\(size))") }
            return "rendered at \(Int(size.width))×\(Int(size.height))"
        }

        await record("Shutdown and resource cleanup") {
            await onMain {
                self.model.closePlayer()
                self.model.player = nil
            }
            try model.database.db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
            return "player released, database checkpointed"
        }
        await finish()
    }

    private func finish() async {
        let failedRequired = checks.filter { $0.required && !$0.passed }
        struct Report: Codable {
            var environment: [String: String]
            var checks: [Check]
            var passed: Bool
            var durationSeconds: Double
        }
        let report = Report(environment: environment, checks: checks, passed: failedRequired.isEmpty, durationSeconds: Date().timeIntervalSince(started))
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        if let data = try? encoder.encode(report) {
            try? data.write(to: URL(fileURLWithPath: args.reportPath))
        }
        log(failedRequired.isEmpty ? "ALL REQUIRED CHECKS PASSED" : "FAILED: \(failedRequired.map { $0.name })")
        await onMain {
            self.model.database.db.close()
            exit(failedRequired.isEmpty ? 0 : 1)
        }
    }
}
