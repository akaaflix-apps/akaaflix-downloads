import AppKit
import SwiftUI

enum SettingsSection: String, CaseIterable, Hashable {
    case general, account, playlists, player, audioSubtitles, quality, appearance, parental, backup, about

    var title: String {
        switch self {
        case .general: return tr("الإعدادات", "Settings")
        case .account: return tr("الحساب", "Account")
        case .playlists: return tr("إدارة القوائم", "Playlists")
        case .player: return tr("المشغل", "Player")
        case .audioSubtitles: return tr("الصوت والترجمة", "Audio & Subtitles")
        case .quality: return tr("جودة العرض", "Display Quality")
        case .appearance: return tr("المظهر والواجهة", "Appearance")
        case .parental: return tr("التحكم الأبوي", "Parental Control")
        case .backup: return tr("النسخ الاحتياطي", "Backup")
        case .about: return tr("حول التطبيق", "About")
        }
    }

    var systemImage: String {
        switch self {
        case .general: return "gearshape"
        case .account: return "person"
        case .playlists: return "square.stack.3d.up"
        case .player: return "play.circle"
        case .audioSubtitles: return "captions.bubble"
        case .quality: return "sparkles.rectangle.stack"
        case .appearance: return "paintbrush"
        case .parental: return "lock.shield"
        case .backup: return "icloud.and.arrow.up"
        case .about: return "info.circle"
        }
    }
}

enum Route: Equatable {
    case home, live, movies, series
    case movieDetails(Movie)
    case seriesDetails(Series)
    case settings(SettingsSection)
    case playlistForm(Int64?)
    case search, favorites, watchLater, downloads, history, notifications

    var tab: Int? {
        switch self {
        case .home: return 0
        case .live: return 1
        case .movies, .movieDetails: return 2
        case .series, .seriesDetails: return 3
        default: return nil
        }
    }
}

struct PINRequest: Identifiable {
    let id = UUID()
    let reason: String
    let onSuccess: () -> Void
}

/// Root application state: data stores, active playlist, navigation, playback and settings.
final class AppModel: ObservableObject {

    // Services
    let paths: AppPaths
    let database: AppDatabase
    let secrets: SecretStore
    let client: XtreamClient
    private let settingsFile: SettingsFile

    // State
    @Published private(set) var settings: AppSettings
    @Published private(set) var playlists: [Playlist] = []
    @Published private(set) var activePlaylist: Playlist?
    @Published var route: Route = .home
    @Published private(set) var backStack: [Route] = []
    @Published var player: PlayerController?
    @Published var toast: Toast?
    @Published var pinRequest: PINRequest?
    @Published private(set) var isSyncing = false
    @Published private(set) var syncStage = ""
    @Published private(set) var catalogVersion = 0
    @Published private(set) var userDataVersion = 0
    @Published private(set) var unreadNotifications = 0
    @Published var parentalUnlocked = false

    private var toastWork: DispatchWorkItem?
    private var syncTask: Task<Void, Never>?

    init(paths: AppPaths, secrets: SecretStore, inMemory: Bool = false) throws {
        self.paths = paths
        try paths.prepare()
        self.database = try (inMemory ? AppDatabase.inMemory() : AppDatabase(path: paths.database.path))
        self.secrets = secrets
        self.client = XtreamClient()
        self.settingsFile = SettingsFile(url: paths.settings)
        self.settings = settingsFile.load()
        Lang.current = settings.language
        reloadPlaylists()
        switch settings.launchScreen {
        case .home: route = .home
        case .live: route = .live
        case .movies: route = .movies
        case .series: route = .series
        case .lastScreen:
            switch settings.lastScreen {
            case .live: route = .live
            case .movies: route = .movies
            case .series: route = .series
            default: route = .home
            }
        }
        if activePlaylist == nil { route = .playlistForm(nil) }
        unreadNotifications = database.userData.unreadNotificationCount()
    }

    // MARK: Settings

    func updateSettings(_ change: (inout AppSettings) -> Void) {
        var s = settings
        change(&s)
        guard s != settings else { return }
        let languageChanged = s.language != settings.language
        settings = s
        Lang.current = s.language
        try? settingsFile.save(s)
        if languageChanged { objectWillChange.send() }
    }

    // MARK: Playlists

    func reloadPlaylists() {
        playlists = (try? database.playlists.all()) ?? []
        if let active = activePlaylist, let fresh = playlists.first(where: { $0.id == active.id }), fresh.isEnabled {
            activePlaylist = fresh
        } else {
            activePlaylist = playlists.first(where: { $0.isDefault && $0.isEnabled }) ?? playlists.first(where: { $0.isEnabled })
        }
    }

    func credentials(for playlist: Playlist) -> XtreamCredentials? {
        guard let pw = secrets.password(for: playlist.id) else { return nil }
        return XtreamCredentials(baseURL: playlist.serverURL, username: playlist.username, password: pw)
    }

    var activeCredentials: XtreamCredentials? { activePlaylist.flatMap { credentials(for: $0) } }

    func activate(_ playlist: Playlist) {
        activePlaylist = playlist
        catalogVersion += 1
        userDataVersion += 1
        if playlist.lastSync == nil { sync(playlist) }
    }

    /// Tests a connection with the four approved fields; returns normalised credentials + account.
    func testConnection(_ input: PlaylistInput) async throws -> (XtreamCredentials, AccountInfo) {
        try await client.authenticate(serverInput: input.url, username: input.username.trimmingCharacters(in: .whitespaces), password: input.password)
    }

    @discardableResult
    func addPlaylist(_ input: PlaylistInput) async throws -> Playlist {
        let (creds, account) = try await testConnection(input)
        let p = try database.playlists.insert(name: input.name.trimmingCharacters(in: .whitespaces), serverURL: creds.baseURL, username: creds.username, account: account, status: .connected)
        try secrets.setPassword(creds.password, for: p.id)
        await MainActor.run {
            reloadPlaylists()
            if activePlaylist == nil || p.isDefault { activePlaylist = p }
        }
        return p
    }

    func updatePlaylist(_ playlist: Playlist, input: PlaylistInput) async throws {
        let password = input.password.isEmpty ? (secrets.password(for: playlist.id) ?? "") : input.password
        let (creds, account) = try await testConnection(PlaylistInput(name: input.name, username: input.username, password: password, url: input.url))
        var p = playlist
        p.name = input.name.trimmingCharacters(in: .whitespaces)
        p.serverURL = creds.baseURL
        p.username = creds.username
        p.account = account
        p.status = .connected
        try database.playlists.update(p)
        try secrets.setPassword(creds.password, for: p.id)
        await MainActor.run { reloadPlaylists() }
    }

    func duplicatePlaylist(_ playlist: Playlist) {
        do {
            let copy = try database.playlists.insert(name: playlist.name + tr(" (نسخة)", " (copy)"), serverURL: playlist.serverURL, username: playlist.username, account: playlist.account, status: playlist.status)
            if let pw = secrets.password(for: playlist.id) { try secrets.setPassword(pw, for: copy.id) }
            reloadPlaylists()
            showToast(tr("تم نسخ القائمة", "Playlist duplicated"), .success)
        } catch { showError(error) }
    }

    func deletePlaylist(_ playlist: Playlist) {
        do {
            try database.playlists.delete(playlist.id)
            secrets.removePassword(for: playlist.id)
            let wasActive = activePlaylist?.id == playlist.id
            reloadPlaylists()
            if wasActive { catalogVersion += 1; userDataVersion += 1 }
            if playlists.isEmpty { route = .playlistForm(nil); backStack = [] }
            showToast(tr("تم حذف القائمة", "Playlist deleted"), .success)
        } catch { showError(error) }
    }

    func setDefault(_ playlist: Playlist) {
        try? database.playlists.setDefault(playlist.id)
        reloadPlaylists()
        if let p = playlists.first(where: { $0.id == playlist.id }) { activate(p) }
    }

    func setEnabled(_ playlist: Playlist, _ enabled: Bool) {
        var p = playlist
        p.isEnabled = enabled
        try? database.playlists.update(p)
        reloadPlaylists()
        catalogVersion += 1
    }

    func reorderPlaylists(_ ids: [Int64]) {
        try? database.playlists.reorder(ids)
        reloadPlaylists()
    }

    // MARK: Sync

    func sync(_ playlist: Playlist? = nil, silent: Bool = false) {
        guard let target = playlist ?? activePlaylist, let creds = credentials(for: target) else { return }
        syncTask?.cancel()
        isSyncing = true
        syncStage = ""
        syncTask = Task { [weak self] in
            guard let self = self else { return }
            do {
                // Refresh the account first so expiry/status shown in the UI comes from the server.
                var p = target
                do {
                    p.account = try await self.client.authenticate(creds)
                    p.status = .connected
                } catch let e as AkaaError where e == .accountExpired {
                    p.status = .expired
                } catch {
                    p.status = .failed
                }
                try? self.database.playlists.update(p)
                let syncer = CatalogSyncer(client: self.client, database: self.database)
                let summary = try await syncer.sync(playlist: p, credentials: creds) { stage in
                    DispatchQueue.main.async { self.syncStage = stage }
                }
                await MainActor.run {
                    self.isSyncing = false
                    self.reloadPlaylists()
                    self.catalogVersion += 1
                    self.recordSyncNotifications(summary, playlist: p)
                    if !silent {
                        let msg = summary.failedSections.isEmpty
                            ? tr("تم تحديث المحتوى", "Content updated")
                            : tr("تم التحديث جزئياً", "Partially updated")
                        self.showToast(msg, summary.failedSections.isEmpty ? .success : .info)
                    }
                }
            } catch {
                await MainActor.run {
                    self.isSyncing = false
                    self.reloadPlaylists()
                    if !silent { self.showError(error) }
                }
            }
        }
    }

    func refreshAll() {
        Task {
            for p in playlists where p.isEnabled {
                guard let creds = credentials(for: p) else { continue }
                _ = try? await CatalogSyncer(client: client, database: database).sync(playlist: p, credentials: creds)
            }
            await MainActor.run {
                reloadPlaylists()
                catalogVersion += 1
                showToast(tr("تم تحديث جميع القوائم", "All playlists refreshed"), .success)
            }
        }
    }

    func autoRefreshIfNeeded() {
        guard let p = activePlaylist else { return }
        let hours = Double(max(1, settings.autoRefreshHours))
        if p.lastSync == nil || Date().timeIntervalSince(p.lastSync!) > hours * 3600 {
            sync(p, silent: p.lastSync != nil)
        }
    }

    private func recordSyncNotifications(_ summary: SyncSummary, playlist: Playlist) {
        let data = database.userData
        if settings.notifyNewContent, summary.newMovies + summary.newSeries + summary.newChannels > 0 {
            let body = tr("أفلام جديدة: \(summary.newMovies) · مسلسلات جديدة: \(summary.newSeries) · قنوات جديدة: \(summary.newChannels)",
                          "New movies: \(summary.newMovies) · New series: \(summary.newSeries) · New channels: \(summary.newChannels)")
            try? data.addNotification(playlist.id, title: tr("محتوى جديد في ", "New content in ") + playlist.name, body: body)
            LocalNotifier.post(title: "Akaa Flix", body: body)
        }
        if settings.notifyExpiry, let days = playlist.account?.remainingDays(), days <= 7 {
            let body = tr("ينتهي اشتراك \(playlist.name) خلال \(days) يوم", "\(playlist.name) expires in \(days) day(s)")
            try? data.addNotification(playlist.id, title: tr("تنبيه انتهاء الاشتراك", "Subscription expiry"), body: body)
        }
        unreadNotifications = data.unreadNotificationCount()
    }

    func markNotificationsRead() {
        try? database.userData.markNotificationsRead()
        unreadNotifications = 0
    }

    // MARK: Navigation

    func open(_ r: Route) {
        guard r != route else { return }
        if case .settings = r, settings.lockSettings, settings.parentalPINHash != nil, !parentalUnlocked {
            requestPIN(tr("الإعدادات محمية", "Settings are locked")) { [weak self] in self?.push(r) }
            return
        }
        push(r)
    }

    private func push(_ r: Route) {
        if r.tab != nil, r == .home || r == .live || r == .movies || r == .series {
            backStack.removeAll()
            switch r {
            case .live: updateSettings { $0.lastScreen = .live }
            case .movies: updateSettings { $0.lastScreen = .movies }
            case .series: updateSettings { $0.lastScreen = .series }
            default: updateSettings { $0.lastScreen = .home }
            }
        } else {
            backStack.append(route)
            if backStack.count > 30 { backStack.removeFirst() }
        }
        route = r
    }

    func back() {
        if let last = backStack.popLast() { route = last } else { route = .home }
    }

    var canGoBack: Bool { !backStack.isEmpty }

    // MARK: Favorites / groups

    func isFavorite(_ type: ContentType, _ id: String) -> Bool {
        guard let p = activePlaylist else { return false }
        return database.userData.isFavorite(p.id, type, id)
    }

    func toggleFavorite(_ type: ContentType, _ id: String) {
        guard let p = activePlaylist else { return }
        let now = (try? database.userData.toggleFavorite(p.id, type, id)) ?? false
        userDataVersion += 1
        showToast(now ? tr("أضيف إلى المفضلة", "Added to favorites") : tr("أزيل من المفضلة", "Removed from favorites"), .success)
    }

    static let watchLaterGroupName = "__watch_later__"

    func watchLaterGroup() -> CustomGroup? {
        guard let p = activePlaylist else { return nil }
        if let g = try? database.userData.groups(p.id).first(where: { $0.name == AppModel.watchLaterGroupName }) { return g }
        return try? database.userData.createGroup(p.id, name: AppModel.watchLaterGroupName)
    }

    func isInWatchLater(_ type: ContentType, _ id: String) -> Bool {
        guard let p = activePlaylist, let g = watchLaterGroup() else { return false }
        return (try? database.userData.groupsContaining(p.id, type, id).contains(g.id)) ?? false
    }

    func toggleWatchLater(_ type: ContentType, _ id: String) {
        guard let g = watchLaterGroup() else { return }
        let member = !isInWatchLater(type, id)
        try? database.userData.setInGroup(g.id, type, id, member)
        userDataVersion += 1
        showToast(member ? tr("أضيف إلى المشاهدة لاحقاً", "Added to Watch Later") : tr("أزيل من المشاهدة لاحقاً", "Removed from Watch Later"), .success)
    }

    func customGroups() -> [CustomGroup] {
        guard let p = activePlaylist else { return [] }
        return ((try? database.userData.groups(p.id)) ?? []).filter { !$0.name.hasPrefix("__") }
    }

    func createGroup(named name: String) {
        guard let p = activePlaylist, !name.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        _ = try? database.userData.createGroup(p.id, name: name.trimmingCharacters(in: .whitespaces))
        userDataVersion += 1
    }

    func hiddenContent() -> HiddenContent {
        guard let p = activePlaylist else { return HiddenContent() }
        return (try? database.userData.hiddenContent(p.id)) ?? HiddenContent()
    }

    func setHidden(kind: String, value: String, hidden: Bool) {
        guard let p = activePlaylist else { return }
        try? database.userData.setHidden(p.id, kind: kind, value: value, hidden: hidden)
        catalogVersion += 1
    }

    func baseFilter() -> BrowseFilter {
        var f = BrowseFilter()
        f.excludeAdult = settings.hideAdultContent
        f.hidden = hiddenContent()
        return f
    }

    // MARK: Parental control

    func parentalContext() -> ParentalContext {
        let locks = activePlaylist.flatMap { try? database.userData.locks($0.id) } ?? []
        return ParentalContext(pinHash: settings.parentalPINHash, maxAge: settings.maxAgeRating, locks: locks, unlockedForSession: parentalUnlocked)
    }

    func requestPIN(_ reason: String, then action: @escaping () -> Void) {
        guard settings.parentalPINHash != nil else { action(); return }
        pinRequest = PINRequest(reason: reason, onSuccess: action)
    }

    func guarded(type: ContentType, id: String, categoryID: String?, ageRating: String? = nil, _ action: @escaping () -> Void) {
        if parentalContext().requiresPIN(type: type, itemID: id, categoryID: categoryID, ageRating: ageRating) {
            requestPIN(tr("هذا المحتوى محمي بالرمز السري", "This content is PIN-protected"), then: action)
        } else {
            action()
        }
    }

    // MARK: Playback

    func play(_ request: PlaybackRequest) {
        guard let playlist = activePlaylist, let creds = credentials(for: playlist) else {
            showError(AkaaError.noActivePlaylist)
            return
        }
        if settings.preferredPlayer != .akaaPlayer, let url = request.candidates(creds: creds, settings: settings, account: playlist.account).first?.url {
            ExternalPlayer.open(url, with: settings.preferredPlayer)
            return
        }
        player?.stop()
        let controller = PlayerController(model: self, request: request, playlist: playlist, credentials: creds)
        player = controller
        controller.start()
    }

    func closePlayer() {
        if let p = player, p.returnsToPreview, !p.isMini {
            if p.isFloating { p.setFloating(false) }
            player = nil
            return
        }
        player?.stop()
        player = nil
        userDataVersion += 1
    }

    // MARK: Toasts

    func showToast(_ message: String, _ kind: Toast.Kind = .info) {
        toastWork?.cancel()
        withAnimation { toast = Toast(message: message, kind: kind) }
        let work = DispatchWorkItem { [weak self] in withAnimation { self?.toast = nil } }
        toastWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.2, execute: work)
    }

    func showError(_ error: Error) {
        let e = AkaaError.from(error)
        if e == .cancelled { return }
        showToast(e.message, .error)
    }
}

/// Opens a stream in an external player chosen in Settings → Player.
enum ExternalPlayer {
    static func bundleID(_ player: PreferredPlayer) -> String? {
        switch player {
        case .akaaPlayer: return nil
        case .vlcApp: return "org.videolan.vlc"
        case .iina: return "com.colliderli.iina"
        case .quickTime: return "com.apple.QuickTimePlayerX"
        }
    }

    static func isInstalled(_ player: PreferredPlayer) -> Bool {
        guard let id = bundleID(player) else { return true }
        return NSWorkspace.shared.urlForApplication(withBundleIdentifier: id) != nil
    }

    static func open(_ url: URL, with player: PreferredPlayer) {
        guard let id = bundleID(player), let app = NSWorkspace.shared.urlForApplication(withBundleIdentifier: id) else {
            NSWorkspace.shared.open(url)
            return
        }
        let config = NSWorkspace.OpenConfiguration()
        NSWorkspace.shared.open([url], withApplicationAt: app, configuration: config, completionHandler: nil)
    }
}
