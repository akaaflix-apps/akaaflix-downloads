import SwiftUI

struct HeroItem: Identifiable, Equatable {
    enum Kind: Equatable { case movie(Movie), series(Series) }
    let kind: Kind
    var id: String {
        switch kind {
        case .movie(let m): return "m\(m.id)"
        case .series(let s): return "s\(s.id)"
        }
    }
    var title: String {
        switch kind {
        case .movie(let m): return m.name
        case .series(let s): return s.name
        }
    }
    var image: String? {
        switch kind {
        case .movie(let m): return m.poster
        case .series(let s): return s.backdrop ?? s.cover
        }
    }
    var plot: String? {
        switch kind {
        case .movie(let m): return m.plot
        case .series(let s): return s.plot
        }
    }
    var quality: String? {
        switch kind {
        case .movie(let m): return m.quality
        case .series(let s): return s.quality
        }
    }
    var isNew: Bool {
        let date: Date?
        switch kind {
        case .movie(let m): date = m.added
        case .series(let s): date = s.lastModified
        }
        return date.map { Date().timeIntervalSince($0) < 14 * 86_400 } ?? false
    }
}

final class HomeViewModel: ObservableObject {
    @Published var hero: [HeroItem] = []
    @Published var continueWatching: [WatchProgress] = []
    @Published var mostWatched: [HeroItem] = []
    @Published var latestMovies: [Movie] = []
    @Published var latestSeries: [Series] = []
    @Published var loaded = false

    func load(_ model: AppModel) {
        guard let pid = model.activePlaylist?.id else { return }
        let db = model.database
        var base = model.baseFilter()
        base.sort = .recentlyAdded
        Task {
            let result = try? await runInBackground { () -> ([HeroItem], [WatchProgress], [HeroItem], [Movie], [Series]) in
                var f = base
                f.limit = 24
                let movies = try db.catalog.movies(playlistID: pid, filter: f)
                let series = try db.catalog.series(playlistID: pid, filter: f)
                var heroes: [HeroItem] = series.filter { $0.backdrop != nil || $0.cover != nil }.prefix(3).map { HeroItem(kind: .series($0)) }
                heroes += movies.filter { $0.poster != nil }.prefix(5 - heroes.count).map { HeroItem(kind: .movie($0)) }
                let cw = try db.userData.continueWatching(pid)

                var most: [HeroItem] = []
                var mf = base
                mf.onlyIDs = try db.userData.mostWatchedIDs(pid, .movie, limit: 12)
                let mm = try db.catalog.movies(playlistID: pid, filter: mf)
                mf.onlyIDs = try db.userData.mostWatchedSeriesIDs(pid, limit: 12)
                let ms = try db.catalog.series(playlistID: pid, filter: mf)
                most = ms.map { HeroItem(kind: .series($0)) } + mm.map { HeroItem(kind: .movie($0)) }
                if most.count < 8 {
                    // Xtream has no popularity data: fill with the provider's best-rated titles.
                    var rf = base
                    rf.sort = .rating
                    rf.limit = 8
                    let topSeries = try db.catalog.series(playlistID: pid, filter: rf).map { HeroItem(kind: .series($0)) }
                    let topMovies = try db.catalog.movies(playlistID: pid, filter: rf).map { HeroItem(kind: .movie($0)) }
                    for item in zip(topSeries, topMovies).flatMap({ [$0.0, $0.1] }) where !most.contains(item) { most.append(item) }
                }
                return (heroes, cw, Array(most.prefix(16)), movies, series)
            }
            await MainActor.run {
                if let r = result {
                    self.hero = r.0
                    self.continueWatching = r.1
                    self.mostWatched = r.2
                    self.latestMovies = r.3
                    self.latestSeries = r.4
                }
                self.loaded = true
            }
        }
    }
}

struct HomeView: View {
    @EnvironmentObject var model: AppModel
    @StateObject private var vm = HomeViewModel()

    var body: some View {
        HStack(alignment: .top, spacing: 18) {
            HomeSideRail()
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 22) {
                    HeroBanner(items: vm.hero)
                        .frame(height: 400)
                    shortcutTiles
                    if !vm.continueWatching.isEmpty {
                        SectionTitle(title: tr("مواصلة المشاهدة", "Continue Watching"))
                        CarouselRow(items: vm.continueWatching.map { IdentifiedProgress(progress: $0) }) { item in
                            ContinueCard(progress: item.progress, quality: nil) { resume(item.progress) }
                        }
                    }
                    if !vm.mostWatched.isEmpty {
                        SectionTitle(title: tr("الأكثر مشاهدة", "Most Watched"))
                        CarouselRow(items: vm.mostWatched) { item in
                            PosterCard(title: item.title, imageURL: posterURL(item), quality: item.quality, showsDetails: false) { open(item) }
                                .frame(width: 190)
                        }
                    }
                    if !vm.latestMovies.isEmpty {
                        SectionTitle(title: tr("أحدث الأفلام", "Recently Added Movies"))
                        CarouselRow(items: vm.latestMovies) { m in
                            PosterCard(title: m.name, imageURL: m.poster, quality: m.quality, rating: m.rating, showsDetails: false) { model.open(.movieDetails(m)) }
                                .frame(width: 190)
                        }
                    }
                    if !vm.latestSeries.isEmpty {
                        SectionTitle(title: tr("أحدث المسلسلات", "Recently Added Series"))
                        CarouselRow(items: vm.latestSeries) { s in
                            PosterCard(title: s.name, imageURL: s.cover, quality: s.quality, rating: s.rating, showsDetails: false) { model.open(.seriesDetails(s)) }
                                .frame(width: 190)
                        }
                    }
                    if vm.loaded && vm.hero.isEmpty && vm.latestMovies.isEmpty && !model.isSyncing {
                        EmptyStateView(systemImage: "arrow.triangle.2.circlepath", title: tr("لا يوجد محتوى بعد", "No content yet"),
                                       message: tr("قم بتحديث القائمة لتحميل القنوات والأفلام والمسلسلات", "Refresh the playlist to load channels, movies and series"),
                                       actionTitle: tr("تحديث المحتوى", "Refresh content")) { model.sync() }
                            .frame(height: 260)
                    }
                }
                .padding(.trailing, 24)
                .padding(.bottom, 30)
            }
        }
        .padding(.leading, 18)
        .onAppear { vm.load(model) }
        .onChange(of: model.catalogVersion) { _ in vm.load(model) }
        .onChange(of: model.userDataVersion) { _ in vm.load(model) }
    }

    private var shortcutTiles: some View {
        HStack(spacing: 16) {
            ShortcutTile(title: tr("القنوات المباشرة", "Live TV"), systemImage: "tv", accent: .blue, artwork: "HomeTileLive", wide: true) { model.open(.live) }
                .frame(maxWidth: .infinity)
                .layoutPriority(2)
            ShortcutTile(title: tr("الأفلام", "Movies"), systemImage: "film", accent: .red, artwork: "HomeTileMovies", wide: true) { model.open(.movies) }
                .frame(maxWidth: .infinity)
                .layoutPriority(2)
            ShortcutTile(title: tr("المسلسلات", "Series"), systemImage: "square.stack.3d.up", accent: .blue, artwork: "HomeTileSeries", wide: true) { model.open(.series) }
                .frame(maxWidth: .infinity)
                .layoutPriority(2)
            ShortcutTile(title: tr("قوائم التشغيل", "Playlists"), systemImage: "gearshape", accent: .magenta) { model.open(.settings(.playlists)) }
                .frame(width: 150)
            ShortcutTile(title: tr("التنزيلات", "Downloads"), systemImage: "arrow.down.to.line", accent: .blue) { model.open(.downloads) }
                .frame(width: 150)
            ShortcutTile(title: tr("المشاهدة لاحقاً", "Watch Later"), systemImage: "clock", accent: .magenta) { model.open(.watchLater) }
                .frame(width: 150)
            ShortcutTile(title: tr("المفضلة", "Favorites"), systemImage: "star", accent: .purple) { model.open(.favorites) }
                .frame(width: 150)
        }
        .contentDirection()
    }

    private func posterURL(_ item: HeroItem) -> String? {
        switch item.kind {
        case .movie(let m): return m.poster
        case .series(let s): return s.cover
        }
    }

    private func open(_ item: HeroItem) {
        switch item.kind {
        case .movie(let m): model.open(.movieDetails(m))
        case .series(let s): model.open(.seriesDetails(s))
        }
    }

    private func resume(_ p: WatchProgress) {
        guard let pid = model.activePlaylist?.id else { return }
        switch p.type {
        case .movie:
            if let id = Int(p.itemID), let m = try? model.database.catalog.movie(playlistID: pid, id: id) {
                model.guarded(type: .movie, id: p.itemID, categoryID: m.categoryID) {
                    model.play(.movie(m, details: nil, resumeAt: ContinueWatchingRules.resumePosition(position: p.position, duration: p.duration)))
                }
            }
        case .episode:
            if let sid = p.seriesID, let s = try? model.database.catalog.series(playlistID: pid, id: sid) {
                let ep = Episode(id: p.itemID, seriesID: sid, season: p.season ?? 1, episodeNumber: p.episodeNumber ?? 1, title: p.title, plot: nil, thumbnail: p.image, durationSeconds: Int(p.duration), containerExtension: p.containerExtension, rating: nil, releaseDate: nil)
                model.guarded(type: .series, id: String(sid), categoryID: s.categoryID) {
                    model.play(.episode(ep, series: s, details: nil, resumeAt: ContinueWatchingRules.resumePosition(position: p.position, duration: p.duration)))
                }
            }
        default:
            break
        }
    }
}

struct IdentifiedProgress: Identifiable {
    let progress: WatchProgress
    var id: String { "\(progress.type.rawValue)-\(progress.itemID)" }
}

/// Vertical icon rail on the left of Home (profile, home, live, movies, series, favorites, downloads, history, playlists).
struct HomeSideRail: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        VStack(spacing: 18) {
            railButton("person.crop.circle", tr("الحساب", "Account"), active: false) { model.open(.settings(.account)) }
            railButton("house", tr("الرئيسية", "Home"), active: true) { model.open(.home) }
            railButton("tv", tr("القنوات المباشرة", "Live TV"), active: false) { model.open(.live) }
            railButton("film", tr("الأفلام", "Movies"), active: false) { model.open(.movies) }
            railButton("square.stack.3d.up", tr("المسلسلات", "Series"), active: false) { model.open(.series) }
            railButton("heart", tr("المفضلة", "Favorites"), active: false) { model.open(.favorites) }
            railButton("arrow.down.to.line", tr("التنزيلات", "Downloads"), active: false) { model.open(.downloads) }
            railButton("clock", tr("السجل", "History"), active: false) { model.open(.history) }
            Spacer(minLength: 0)
            railButton("arrow.right.square", tr("تبديل القائمة", "Switch playlist"), active: false) { model.open(.settings(.playlists)) }
        }
        .padding(.vertical, 18)
        .frame(width: 76)
        .background(RoundedRectangle(cornerRadius: 20).fill(Color.black.opacity(0.35)))
    }

    private func railButton(_ icon: String, _ help: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 26, weight: .regular))
                .foregroundColor(.white)
                .frame(width: 58, height: 58)
                .background(RoundedRectangle(cornerRadius: 16).fill(active ? Theme.redGradient : linear([Color.clear, Color.clear])))
                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(active ? Color(hex: 0xFF8080) : Color.clear, lineWidth: 1.5))
                .shadow(color: active ? Theme.red : .clear, radius: 10)
        }
        .buttonStyle(PlainButtonStyle())
        .help(help)
    }
}

/// Cinematic hero banner with page dots and arrows.
struct HeroBanner: View {
    @EnvironmentObject var model: AppModel
    let items: [HeroItem]
    @State private var index = 0
    @State private var timer: Timer?

    var body: some View {
        GeometryReader { geo in
            ZStack {
                if items.isEmpty {
                    PlaceholderArt(kind: .backdrop)
                } else {
                    let item = items[min(index, items.count - 1)]
                    ZStack(alignment: .leading) {
                        // Wide artwork: blurred fill + sharp image on the trailing side.
                        RemoteImage(url: item.image, contentMode: .fill, placeholder: .backdrop, maxPixel: 1400)
                            .frame(width: geo.size.width, height: geo.size.height)
                            .blur(radius: 24)
                            .opacity(0.55)
                            .clipped()
                        HStack {
                            Spacer()
                            RemoteImage(url: item.image, contentMode: .fill, placeholder: .backdrop, maxPixel: 1400)
                                .frame(width: geo.size.width * 0.62, height: geo.size.height)
                                .clipped()
                                .mask(linear([.clear, Color.black.opacity(0.85), .black], .leading, .trailing))
                        }
                        linear([Color.black.opacity(0.92), Color.black.opacity(0.6), .clear], .leading, .trailing)
                        heroText(item)
                            .frame(width: geo.size.width * 0.42, alignment: .leading)
                            .padding(.leading, 90)
                    }
                    .id(item.id)
                    .transition(.opacity)
                }
                HStack {
                    arrow("chevron.left") { step(-1) }
                    Spacer()
                    arrow("chevron.right") { step(1) }
                }
                .padding(.horizontal, 14)
                if items.count > 1 {
                    HStack(spacing: 10) {
                        ForEach(0..<items.count, id: \.self) { i in
                            Circle().fill(i == index ? Theme.redBright : Color.white.opacity(0.9))
                                .frame(width: 14, height: 14)
                                .shadow(color: i == index ? Theme.red : .clear, radius: 5)
                        }
                    }
                    .frame(maxHeight: .infinity, alignment: .bottom)
                    .padding(.bottom, 16)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(Theme.neonStroke, lineWidth: 2))
            .shadow(color: Color(hex: 0x6A1AAE).opacity(0.6), radius: 16)
        }
        .onAppear { startTimer() }
        .onDisappear { timer?.invalidate() }
    }

    private func heroText(_ item: HeroItem) -> some View {
        let titles = ContentText.displayTitles(item.title)
        return VStack(alignment: .leading, spacing: 14) {
            Text(titles.primary)
                .font(.system(size: 48, weight: .heavy))
                .foregroundColor(Color(hex: 0xF1D9A0))
                .shadow(color: .black, radius: 8)
                .lineLimit(2)
                .minimumScaleFactor(0.6)
            if let s = titles.secondary {
                Text(s.uppercased()).font(.system(size: 22, weight: .bold)).foregroundColor(Color(hex: 0xE8D2A0)).lineLimit(1)
            }
            HStack(spacing: 10) {
                if item.isNew { Chip(text: tr("جديد", "New"), filled: true, fontSize: 18) }
                if let q = item.quality { QualityBadge(quality: q, large: true) }
                if case .series(let s) = item.kind, let y = s.year { Chip(text: String(y), fontSize: 16) }
                if case .movie(let m) = item.kind, let r = m.rating { Chip(text: String(format: "★ %.1f", r), fontSize: 16) }
            }
            if let plot = item.plot {
                Text(plot).font(.system(size: 17, weight: .medium)).foregroundColor(.white.opacity(0.92)).lineLimit(2)
            }
            HStack(spacing: 14) {
                NeonButton(title: tr("شاهد الآن", "Watch Now"), systemImage: "play.fill", kind: .red, height: 50, fontSize: 18) { watch(item) }
                NeonButton(title: tr("أضف للمفضلة", "Add to Favorites"), kind: .dark, height: 50, fontSize: 17) { favorite(item) }
                NeonButton(title: tr("معلومات", "Info"), systemImage: "info.circle", kind: .dark, height: 50, fontSize: 17) { info(item) }
            }
        }
        .contentDirection()
    }

    private func arrow(_ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 24, weight: .bold)).foregroundColor(.white)
                .frame(width: 58, height: 58)
                .background(Circle().fill(Color.black.opacity(0.55)))
                .overlay(Circle().strokeBorder(Color.white.opacity(0.4), lineWidth: 1.5))
        }.buttonStyle(PlainButtonStyle())
    }

    private func step(_ d: Int) {
        guard !items.isEmpty else { return }
        withAnimation(.easeInOut(duration: 0.5)) { index = (index + d + items.count) % items.count }
        startTimer()
    }

    private func startTimer() {
        timer?.invalidate()
        guard !model.settings.reduceMotion else { return }
        timer = Timer.scheduledTimer(withTimeInterval: 9, repeats: true) { _ in
            guard !items.isEmpty else { return }
            withAnimation(.easeInOut(duration: 0.6)) { index = (index + 1) % items.count }
        }
    }

    private func watch(_ item: HeroItem) {
        switch item.kind {
        case .movie(let m): model.guarded(type: .movie, id: String(m.id), categoryID: m.categoryID) { model.play(.movie(m, details: nil, resumeAt: nil)) }
        case .series(let s): model.open(.seriesDetails(s))
        }
    }

    private func favorite(_ item: HeroItem) {
        switch item.kind {
        case .movie(let m): model.toggleFavorite(.movie, String(m.id))
        case .series(let s): model.toggleFavorite(.series, String(s.id))
        }
    }

    private func info(_ item: HeroItem) {
        switch item.kind {
        case .movie(let m): model.open(.movieDetails(m))
        case .series(let s): model.open(.seriesDetails(s))
        }
    }
}
