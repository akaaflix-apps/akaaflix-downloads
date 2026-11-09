import AppKit
import SwiftUI

enum LibraryKind { case favorites, watchLater, history }

final class LibraryViewModel: ObservableObject {
    @Published var channels: [Channel] = []
    @Published var movies: [Movie] = []
    @Published var series: [Series] = []
    @Published var history: [WatchProgress] = []

    func load(_ model: AppModel, kind: LibraryKind) {
        guard let pid = model.activePlaylist?.id else { return }
        let db = model.database
        var f = model.baseFilter()
        func ids(_ t: ContentType) -> [String] {
            switch kind {
            case .favorites: return (try? db.userData.favoriteIDs(pid, t)) ?? []
            case .watchLater:
                guard let g = model.watchLaterGroup() else { return [] }
                return (try? db.userData.groupItemIDs(g.id, t)) ?? []
            case .history: return []
            }
        }
        if kind == .history {
            history = (try? db.userData.history(pid, limit: 200)) ?? []
            return
        }
        f.onlyIDs = ids(.live)
        channels = (try? db.catalog.channels(playlistID: pid, filter: f)) ?? []
        f.onlyIDs = ids(.movie)
        movies = (try? db.catalog.movies(playlistID: pid, filter: f)) ?? []
        f.onlyIDs = ids(.series)
        series = (try? db.catalog.series(playlistID: pid, filter: f)) ?? []
    }
}

struct LibraryView: View {
    @EnvironmentObject var model: AppModel
    @StateObject private var vm = LibraryViewModel()
    let kind: LibraryKind

    private var title: String {
        switch kind {
        case .favorites: return tr("المفضلة", "Favorites")
        case .watchLater: return tr("المشاهدة لاحقاً", "Watch Later")
        case .history: return tr("سجل المشاهدة", "Watch History")
        }
    }

    private var icon: String {
        switch kind {
        case .favorites: return "star.fill"
        case .watchLater: return "clock"
        case .history: return "clock.arrow.circlepath"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 16) {
                BackButton()
                ScreenTitle(systemImage: icon, title: title, subtitle: kind == .favorites ? "Favorites" : kind == .watchLater ? "Watch Later" : "History")
                Spacer()
                if kind == .history && !vm.history.isEmpty {
                    NeonButton(title: tr("مسح السجل", "Clear history"), systemImage: "trash", kind: .outlineRed, height: 44, fontSize: 15) {
                        if let pid = model.activePlaylist?.id { try? model.database.userData.clearHistory(pid) }
                        vm.load(model, kind: kind)
                    }
                }
            }
            .padding(.horizontal, 28)
            NeonPanel(accent: .mixed, padding: 18) {
                ScrollView(.vertical, showsIndicators: true) {
                    VStack(alignment: .leading, spacing: 20) {
                        if kind == .history { historyList } else { collections }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 20)
        }
        .onAppear { vm.load(model, kind: kind) }
        .onChange(of: model.userDataVersion) { _ in vm.load(model, kind: kind) }
    }

    @ViewBuilder private var collections: some View {
        if vm.channels.isEmpty && vm.movies.isEmpty && vm.series.isEmpty {
            EmptyStateView(systemImage: icon, title: tr("لا يوجد شيء هنا بعد", "Nothing here yet"),
                           message: kind == .favorites ? tr("أضف قنوات وأفلام ومسلسلات إلى المفضلة لتظهر هنا", "Add channels, movies and series to favorites to see them here")
                                                       : tr("استخدم قائمة الخيارات على أي فيلم أو مسلسل لإضافته", "Use the context menu on any movie or series to add it"))
                .frame(height: 360)
        }
        if !vm.channels.isEmpty {
            SectionTitle(title: tr("القنوات", "Channels"))
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 300), spacing: 12)], spacing: 10) {
                ForEach(vm.channels) { c in
                    ChannelRow(channel: c, isSelected: false, isFavorite: model.isFavorite(.live, String(c.id)),
                               onSelect: { model.guarded(type: .live, id: String(c.id), categoryID: c.categoryID) { model.play(.live(c, list: vm.channels)) } },
                               onFavorite: { model.toggleFavorite(.live, String(c.id)) })
                }
            }
        }
        if !vm.movies.isEmpty {
            SectionTitle(title: tr("الأفلام", "Movies"))
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 170, maximum: 230), spacing: 18)], spacing: 20) {
                ForEach(Array(vm.movies.enumerated()), id: \.element.id) { i, m in
                    PosterCard(title: m.name, imageURL: m.poster, quality: m.quality, rating: m.rating, accentIndex: i) { model.open(.movieDetails(m)) }
                        .contextMenu { removeButton(.movie, String(m.id)) }
                }
            }
        }
        if !vm.series.isEmpty {
            SectionTitle(title: tr("المسلسلات", "Series"))
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 170, maximum: 230), spacing: 18)], spacing: 20) {
                ForEach(Array(vm.series.enumerated()), id: \.element.id) { i, s in
                    PosterCard(title: s.name, imageURL: s.cover, quality: s.quality, rating: s.rating, accentIndex: i + 1) { model.open(.seriesDetails(s)) }
                        .contextMenu { removeButton(.series, String(s.id)) }
                }
            }
        }
    }

    private func removeButton(_ t: ContentType, _ id: String) -> some View {
        Button(tr("إزالة", "Remove")) {
            if kind == .favorites { model.toggleFavorite(t, id) } else { model.toggleWatchLater(t, id) }
        }
    }

    @ViewBuilder private var historyList: some View {
        if vm.history.isEmpty {
            EmptyStateView(systemImage: icon, title: tr("لا يوجد سجل مشاهدة", "No watch history")).frame(height: 360)
        }
        ForEach(vm.history, id: \.self) { p in
            HStack(spacing: 14) {
                RemoteImage(url: p.image, contentMode: .fill, placeholder: p.type == .live ? .channel : .backdrop, maxPixel: 300, title: p.title)
                    .frame(width: 140, height: 80).clipShape(RoundedRectangle(cornerRadius: 10))
                VStack(alignment: .leading, spacing: 6) {
                    Text(p.title).font(.system(size: 18, weight: .bold)).foregroundColor(.white).lineLimit(1)
                    Text(typeLabel(p.type) + " · " + RelativeDateTimeFormatter().localizedString(for: p.updatedAt, relativeTo: Date()))
                        .font(.system(size: 13, weight: .medium)).foregroundColor(Theme.textSecondary)
                    if p.duration > 0 { ProgressBar(fraction: p.fraction, height: 4).frame(width: 240) }
                }
                Spacer()
                NeonButton(title: tr("تشغيل", "Play"), systemImage: "play.fill", kind: .red, height: 40, fontSize: 14) { play(p) }
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.04)))
            .contentDirection()
        }
    }

    private func typeLabel(_ t: ContentType) -> String {
        switch t {
        case .live: return tr("قناة", "Channel")
        case .movie: return tr("فيلم", "Movie")
        case .series, .episode: return tr("حلقة", "Episode")
        }
    }

    private func play(_ p: WatchProgress) {
        guard let pid = model.activePlaylist?.id else { return }
        let db = model.database.catalog
        switch p.type {
        case .live:
            if let id = Int(p.itemID), let c = try? db.channel(playlistID: pid, id: id) { model.play(.live(c, list: [c])) }
        case .movie:
            if let id = Int(p.itemID), let m = try? db.movie(playlistID: pid, id: id) {
                model.play(.movie(m, details: nil, resumeAt: ContinueWatchingRules.resumePosition(position: p.position, duration: p.duration)))
            }
        case .episode, .series:
            if let sid = p.seriesID, let s = try? db.series(playlistID: pid, id: sid) {
                let ep = Episode(id: p.itemID, seriesID: sid, season: p.season ?? 1, episodeNumber: p.episodeNumber ?? 1, title: p.title, plot: nil, thumbnail: p.image, durationSeconds: Int(p.duration), containerExtension: p.containerExtension, rating: nil, releaseDate: nil)
                model.play(.episode(ep, series: s, details: nil, resumeAt: ContinueWatchingRules.resumePosition(position: p.position, duration: p.duration)))
            }
        }
    }
}

struct DownloadsView: View {
    @EnvironmentObject var model: AppModel
    @ObservedObject private var manager = DownloadManager.shared
    @State private var records: [DownloadRecord] = []
    @State private var timer: Timer?

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 16) {
                BackButton()
                ScreenTitle(systemImage: "arrow.down.to.line", title: tr("التنزيلات", "Downloads"), subtitle: "Downloads")
                Spacer()
                NeonButton(title: tr("فتح المجلد", "Open folder"), systemImage: "folder", kind: .dark, height: 44, fontSize: 15) {
                    try? FileManager.default.createDirectory(at: model.paths.downloads, withIntermediateDirectories: true)
                    NSWorkspace.shared.open(model.paths.downloads)
                }
            }
            .padding(.horizontal, 28)
            NeonPanel(accent: .blue, padding: 18) {
                ScrollView {
                    VStack(spacing: 10) {
                        if records.isEmpty {
                            EmptyStateView(systemImage: "arrow.down.to.line", title: tr("لا توجد تنزيلات", "No downloads"),
                                           message: tr("استخدم زر \"تحميل\" في صفحة الفيلم أو الحلقة. يعتمد التحميل على سماح مزود الخدمة بذلك.",
                                                       "Use the \"Download\" button on a movie or episode. Downloading depends on your provider allowing it."))
                                .frame(height: 360)
                        }
                        ForEach(records) { r in row(r) }
                    }
                }
            }
            .padding(.horizontal, 24).padding(.bottom, 20)
        }
        .onAppear {
            reload()
            timer = Timer.scheduledTimer(withTimeInterval: 1.5, repeats: true) { _ in reload() }
        }
        .onDisappear { timer?.invalidate() }
    }

    private func reload() {
        guard let pid = model.activePlaylist?.id else { return }
        records = (try? model.database.userData.downloads(pid)) ?? []
    }

    private func row(_ r: DownloadRecord) -> some View {
        HStack(spacing: 14) {
            RemoteImage(url: r.image, contentMode: .fill, placeholder: .poster, maxPixel: 200, title: r.title)
                .frame(width: 70, height: 100).clipShape(RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 8) {
                Text(r.title).font(.system(size: 18, weight: .bold)).foregroundColor(.white).lineLimit(1)
                Text(statusText(r)).font(.system(size: 13, weight: .semibold)).foregroundColor(Theme.textSecondary)
                if r.status == .downloading || r.status == .queued {
                    ProgressBar(fraction: manager.active[r.id] ?? r.progress, height: 5).frame(maxWidth: 360)
                }
            }
            Spacer()
            if r.status == .completed, let path = r.filePath {
                NeonButton(title: tr("تشغيل", "Play"), systemImage: "play.fill", kind: .red, height: 40, fontSize: 14) {
                    model.play(.file(URL(fileURLWithPath: path), title: r.title, type: r.type, itemID: r.itemID))
                }
                Button(action: { NSWorkspace.shared.activateFileViewerSelecting([URL(fileURLWithPath: path)]) }) {
                    Image(systemName: "magnifyingglass.circle").font(.system(size: 24)).foregroundColor(.white)
                }.buttonStyle(PlainButtonStyle())
            }
            if r.status == .downloading {
                Button(action: { manager.cancel(r); reload() }) { Image(systemName: "pause.circle").font(.system(size: 24)).foregroundColor(.white) }.buttonStyle(PlainButtonStyle())
            }
            Button(action: { manager.delete(r, model: model); reload() }) {
                Image(systemName: "trash").font(.system(size: 20)).foregroundColor(Theme.redBright)
            }.buttonStyle(PlainButtonStyle())
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(0.04)))
        .contentDirection()
    }

    private func statusText(_ r: DownloadRecord) -> String {
        let size = r.totalBytes > 0 ? " · " + ByteCountFormatter.string(fromByteCount: r.totalBytes, countStyle: .file) : ""
        switch r.status {
        case .queued: return tr("في الانتظار", "Queued")
        case .downloading: return tr("جارٍ التحميل \(Int((manager.active[r.id] ?? r.progress) * 100))%", "Downloading \(Int((manager.active[r.id] ?? r.progress) * 100))%") + size
        case .paused: return tr("متوقف", "Stopped")
        case .completed: return tr("مكتمل", "Completed") + size
        case .failed: return tr("فشل التحميل", "Failed")
        }
    }
}

struct NotificationsView: View {
    @EnvironmentObject var model: AppModel
    @State private var items: [AppNotification] = []

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 16) {
                BackButton()
                ScreenTitle(systemImage: "bell", title: tr("الإشعارات", "Notifications"), subtitle: "Notifications")
                Spacer()
                if !items.isEmpty {
                    NeonButton(title: tr("مسح الكل", "Clear all"), systemImage: "trash", kind: .outlineRed, height: 44, fontSize: 15) {
                        try? model.database.userData.clearNotifications()
                        items = []
                        model.markNotificationsRead()
                    }
                }
            }
            .padding(.horizontal, 28)
            NeonPanel(accent: .mixed, padding: 18) {
                ScrollView {
                    VStack(spacing: 10) {
                        if items.isEmpty {
                            EmptyStateView(systemImage: "bell.slash", title: tr("لا توجد إشعارات", "No notifications"),
                                           message: tr("ستظهر هنا تنبيهات المحتوى الجديد وقرب انتهاء الاشتراك", "New content and expiry reminders appear here"))
                                .frame(height: 360)
                        }
                        ForEach(items) { n in
                            HStack(alignment: .top, spacing: 14) {
                                Image(systemName: "bell.badge").font(.system(size: 24)).foregroundColor(n.isRead ? .white : Theme.redBright)
                                VStack(alignment: .leading, spacing: 6) {
                                    Text(n.title).font(.system(size: 17, weight: .bold)).foregroundColor(.white)
                                    Text(n.body).font(.system(size: 14, weight: .medium)).foregroundColor(Theme.textSecondary)
                                    Text(TimeFormat.dayMonthYear.string(from: n.createdAt) + " " + TimeFormat.hourMinute.string(from: n.createdAt))
                                        .font(.system(size: 12)).foregroundColor(Theme.textTertiary).fixedLTR()
                                }
                                Spacer()
                            }
                            .padding(14)
                            .background(RoundedRectangle(cornerRadius: 14).fill(Color.white.opacity(n.isRead ? 0.03 : 0.07)))
                            .contentDirection()
                        }
                    }
                }
            }
            .padding(.horizontal, 24).padding(.bottom, 20)
        }
        .onAppear {
            items = (try? model.database.userData.notifications()) ?? []
            model.markNotificationsRead()
        }
    }
}

struct SearchView: View {
    @EnvironmentObject var model: AppModel
    @State private var query = ""
    @State private var channels: [Channel] = []
    @State private var movies: [Movie] = []
    @State private var series: [Series] = []
    @State private var token = 0

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 16) {
                BackButton()
                SearchField(placeholder: tr("ابحث في القنوات والأفلام والمسلسلات...", "Search channels, movies and series..."), text: $query, accent: .red, height: 56)
                    .onChange(of: query) { _ in search() }
            }
            .padding(.horizontal, 28)
            NeonPanel(accent: .mixed, padding: 18) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 18) {
                        if query.trimmingCharacters(in: .whitespaces).count < 2 {
                            EmptyStateView(systemImage: "magnifyingglass", title: tr("اكتب حرفين على الأقل للبحث", "Type at least two characters")).frame(height: 360)
                        } else if channels.isEmpty && movies.isEmpty && series.isEmpty {
                            EmptyStateView(systemImage: "magnifyingglass", title: tr("لا توجد نتائج", "No results")).frame(height: 360)
                        }
                        if !channels.isEmpty {
                            SectionTitle(title: tr("القنوات", "Channels") + " (\(channels.count))")
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 300), spacing: 12)], spacing: 10) {
                                ForEach(channels) { c in
                                    ChannelRow(channel: c, isSelected: false, isFavorite: model.isFavorite(.live, String(c.id)),
                                               onSelect: { model.guarded(type: .live, id: String(c.id), categoryID: c.categoryID) { model.play(.live(c, list: channels)) } },
                                               onFavorite: { model.toggleFavorite(.live, String(c.id)) })
                                }
                            }
                        }
                        if !movies.isEmpty {
                            SectionTitle(title: tr("الأفلام", "Movies") + " (\(movies.count))")
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 160, maximum: 220), spacing: 16)], spacing: 18) {
                                ForEach(Array(movies.enumerated()), id: \.element.id) { i, m in
                                    PosterCard(title: m.name, imageURL: m.poster, quality: m.quality, rating: m.rating, accentIndex: i) { model.open(.movieDetails(m)) }
                                }
                            }
                        }
                        if !series.isEmpty {
                            SectionTitle(title: tr("المسلسلات", "Series") + " (\(series.count))")
                            LazyVGrid(columns: [GridItem(.adaptive(minimum: 160, maximum: 220), spacing: 16)], spacing: 18) {
                                ForEach(Array(series.enumerated()), id: \.element.id) { i, s in
                                    PosterCard(title: s.name, imageURL: s.cover, quality: s.quality, rating: s.rating, accentIndex: i + 1) { model.open(.seriesDetails(s)) }
                                }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(.horizontal, 24).padding(.bottom, 20)
        }
    }

    private func search() {
        guard let pid = model.activePlaylist?.id else { return }
        let q = query.trimmingCharacters(in: .whitespaces)
        guard q.count >= 2 else { channels = []; movies = []; series = []; return }
        var f = model.baseFilter()
        f.search = q
        f.limit = 60
        let db = model.database
        token += 1
        let t = token
        Task {
            let r = try? await runInBackground { (try db.catalog.channels(playlistID: pid, filter: f), try db.catalog.movies(playlistID: pid, filter: f), try db.catalog.series(playlistID: pid, filter: f)) }
            await MainActor.run {
                guard t == token, let r = r else { return }
                channels = r.0; movies = r.1; series = r.2
            }
        }
    }
}
