import SwiftUI

/// Movie or series entry in a grid.
struct CatalogItem: Identifiable, Hashable {
    enum Kind: Hashable { case movie(Movie), series(Series) }
    let kind: Kind
    var id: String {
        switch kind {
        case .movie(let m): return "m\(m.id)"
        case .series(let s): return "s\(s.id)"
        }
    }
    var name: String {
        switch kind {
        case .movie(let m): return m.name
        case .series(let s): return s.name
        }
    }
    var image: String? {
        switch kind {
        case .movie(let m): return m.poster
        case .series(let s): return s.cover
        }
    }
    var rating: Double? {
        switch kind {
        case .movie(let m): return m.rating
        case .series(let s): return s.rating
        }
    }
    var quality: String? {
        switch kind {
        case .movie(let m): return m.quality
        case .series(let s): return s.quality
        }
    }
    var date: Date? {
        switch kind {
        case .movie(let m): return m.added
        case .series(let s): return s.lastModified
        }
    }
    var categoryID: String? {
        switch kind {
        case .movie(let m): return m.categoryID
        case .series(let s): return s.categoryID
        }
    }
    var itemID: String {
        switch kind {
        case .movie(let m): return String(m.id)
        case .series(let s): return String(s.id)
        }
    }
    var isNew: Bool { date.map { Date().timeIntervalSince($0) < 14 * 86_400 } ?? false }
}

final class CatalogViewModel: ObservableObject {
    let type: ContentType
    @Published var categories: [ContentCategory] = []
    @Published var groups: [CustomGroup] = []
    @Published var selection: BrowseSelection = .all
    @Published var items: [CatalogItem] = []
    @Published var totalCount = 0
    @Published var search = ""
    @Published var sort: SortOption = .recentlyAdded
    @Published var year: Int = 0
    @Published var genre: String = ""
    @Published var quality: String = ""
    @Published var years: [Int] = []
    @Published var genres: [String] = []
    @Published var loading = false
    private var token = 0

    init(type: ContentType) { self.type = type }

    func loadSidebar(_ model: AppModel) {
        guard let pid = model.activePlaylist?.id else { return }
        let f = model.baseFilter()
        let db = model.database
        categories = (try? db.catalog.categories(playlistID: pid, type: type, hidden: f.hidden, excludeAdult: f.excludeAdult)) ?? []
        groups = model.customGroups()
        totalCount = (try? db.catalog.count(playlistID: pid, type: type, filter: f)) ?? 0
        years = (try? db.catalog.years(playlistID: pid, type: type)) ?? []
        genres = (try? db.catalog.genres(playlistID: pid, type: type)) ?? []
    }

    func load(_ model: AppModel) {
        guard let pid = model.activePlaylist?.id else { return }
        let db = model.database
        let type = self.type
        var f = model.baseFilter()
        f.search = search
        f.sort = sort
        f.year = year == 0 ? nil : year
        f.genre = genre.isEmpty ? nil : genre
        f.quality = quality.isEmpty ? nil : quality
        let idType: ContentType = type == .movie ? .movie : .series
        switch selection {
        case .all: break
        case .category(let id): f.categoryID = id
        case .favorites: f.onlyIDs = (try? db.userData.favoriteIDs(pid, idType)) ?? []
        case .mostWatched:
            f.onlyIDs = type == .movie ? ((try? db.userData.mostWatchedIDs(pid, .movie, limit: 100)) ?? []) : ((try? db.userData.mostWatchedSeriesIDs(pid, limit: 100)) ?? [])
        case .recent:
            f.addedSince = Date().addingTimeInterval(-14 * 86_400)
        case .group(let gid): f.onlyIDs = (try? db.userData.groupItemIDs(gid, idType)) ?? []
        }
        token += 1
        let t = token
        loading = true
        Task {
            let result: [CatalogItem] = (try? await runInBackground { () -> [CatalogItem] in
                if type == .movie {
                    return try db.catalog.movies(playlistID: pid, filter: f).map { CatalogItem(kind: .movie($0)) }
                }
                return try db.catalog.series(playlistID: pid, filter: f).map { CatalogItem(kind: .series($0)) }
            }) ?? []
            await MainActor.run {
                guard t == self.token else { return }
                self.items = result
                self.loading = false
            }
        }
    }
}

struct CatalogView: View {
    @EnvironmentObject var model: AppModel
    @StateObject private var vm: CatalogViewModel
    @State private var showNewGroup = false
    @State private var newGroupName = ""
    let type: ContentType

    init(type: ContentType) {
        self.type = type
        _vm = StateObject(wrappedValue: CatalogViewModel(type: type))
    }

    private var isMovies: Bool { type == .movie }

    var body: some View {
        VStack(spacing: 10) {
            header.padding(.horizontal, 28)
            GeometryReader { geo in
                HStack(alignment: .top, spacing: 18) {
                    groupsPanel.frame(width: max(290, min(400, geo.size.width * 0.22)))
                    gridPanel.frame(maxWidth: .infinity)
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 20)
            }
        }
        .onAppear { vm.loadSidebar(model); vm.load(model) }
        .onChange(of: model.catalogVersion) { _ in vm.loadSidebar(model); vm.load(model) }
        .onChange(of: model.userDataVersion) { _ in if case .favorites = vm.selection { vm.load(model) } }
        .sheet(isPresented: $showNewGroup) {
            NewGroupSheet(name: $newGroupName, onCreate: {
                model.createGroup(named: newGroupName)
                newGroupName = ""
                showNewGroup = false
                vm.loadSidebar(model)
            }, onCancel: { showNewGroup = false })
        }
    }

    // MARK: Header

    private var header: some View {
        HStack(spacing: 20) {
            ScreenTitle(systemImage: "film", title: isMovies ? tr("الأفلام", "Movies") : tr("المسلسلات", "Series"), subtitle: isMovies ? "Movies" : "TV Series")
            Image("CatalogHeaderArt")
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(height: 96)
                .opacity(0.95)
            Spacer()
            HStack(spacing: 12) {
                Image(systemName: isMovies ? "film" : "square.stack.3d.up").font(.system(size: 26))
                HeaderDivider(height: 30)
                Text(countText).font(.system(size: 24, weight: .heavy)).monoDigits()
            }
            .foregroundColor(.white)
            .padding(.horizontal, 22)
            .frame(height: 62)
            .background(Capsule().fill(Theme.darkPill))
            .overlay(Capsule().strokeBorder(NeonAccent.red.stroke, lineWidth: 1.6))
            .shadow(color: Theme.red.opacity(0.5), radius: 10)
            .fixedLTR()
            ForEach(["4K", "FHD", "HD"], id: \.self) { q in
                Button(action: { vm.quality = vm.quality == q ? "" : q; vm.load(model) }) {
                    QualityBadge(quality: q, large: true)
                        .scaleEffect(vm.quality == q ? 1.08 : 1)
                        .shadow(color: vm.quality == q ? Theme.redBright : .clear, radius: 10)
                }
                .buttonStyle(PlainButtonStyle())
                .help(tr("تصفية حسب الجودة", "Filter by quality"))
            }
        }
    }

    private var countText: String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.locale = Locale(identifier: "en_US")
        let n = f.string(from: NSNumber(value: vm.totalCount)) ?? "\(vm.totalCount)"
        return isMovies ? tr("\(n) فيلم", "\(n) movies") : tr("مسلسل (\(n))", "(\(n)) series")
    }

    // MARK: Groups

    private var groupsPanel: some View {
        NeonPanel(accent: .red, padding: 14) {
            VStack(spacing: 10) {
                PanelHeader(systemImage: "square.stack.3d.up", title: tr("المجموعات", "Groups"), size: 28)
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(spacing: 8) {
                        GroupRow(title: tr("الأكثر مشاهدة", "Most Watched"), systemImage: "chart.bar.fill", isSelected: vm.selection == .mostWatched) { choose(.mostWatched) }
                        GroupRow(title: tr("المفضلة", "Favorites"), systemImage: "star.fill", iconColor: Theme.gold, isSelected: vm.selection == .favorites) { choose(.favorites) }
                        GroupRow(title: tr("إضافة مجموعة", "Add Group"), systemImage: "plus") { showNewGroup = true }
                        GroupRow(title: isMovies ? tr("كل الأفلام", "All Movies") : tr("كل المسلسلات", "All Series"), systemImage: isMovies ? "film" : "tv",
                                 count: isMovies ? nil : vm.totalCount, isSelected: vm.selection == .all) { choose(.all) }
                        GroupRow(title: isMovies ? tr("أفلام جديدة", "New Movies") : tr("مسلسلات جديدة", "New Series"), systemImage: "flame.fill", iconColor: Color(hex: 0xFF8A00),
                                 isSelected: vm.selection == .recent) { choose(.recent) }
                        ForEach(vm.groups) { g in
                            GroupRow(title: g.name, systemImage: "folder", isSelected: vm.selection == .group(g.id)) { choose(.group(g.id)) }
                        }
                        ForEach(vm.categories) { c in
                            GroupRow(title: c.name, systemImage: isMovies ? c.movieIcon : c.kind.systemImage, emoji: isMovies ? nil : c.flag,
                                     count: isMovies ? nil : c.count, isSelected: vm.selection == .category(c.id)) { choose(.category(c.id)) }
                                .contextMenu {
                                    Button(tr("إخفاء هذه المجموعة", "Hide this category")) { model.setHidden(kind: "category", value: c.id, hidden: true) }
                                    Button(tr("قفل بالرمز السري", "Lock with PIN")) {
                                        if let pid = model.activePlaylist?.id { try? model.database.userData.setLocked(pid, kind: "category", value: c.id, locked: true) }
                                    }
                                }
                        }
                    }
                }
            }
        }
    }

    private func choose(_ s: BrowseSelection) {
        vm.selection = s
        vm.load(model)
    }

    // MARK: Grid

    private var gridPanel: some View {
        NeonPanel(accent: .mixed, padding: 16) {
            VStack(spacing: 14) {
                filterBar
                if vm.items.isEmpty && !vm.loading {
                    EmptyStateView(systemImage: isMovies ? "film" : "square.stack.3d.up",
                                   title: isMovies ? tr("لا توجد أفلام", "No movies") : tr("لا توجد مسلسلات", "No series"),
                                   message: vm.search.isEmpty ? nil : tr("جرّب كلمة بحث أخرى", "Try a different search"))
                } else {
                    ScrollView(.vertical, showsIndicators: true) {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 175, maximum: 240), spacing: 18)], spacing: 22) {
                            ForEach(Array(vm.items.enumerated()), id: \.element.id) { index, item in
                                PosterCard(title: item.name, imageURL: item.image, quality: item.quality, isNew: item.isNew, rating: item.rating,
                                           dateText: item.date.map { TimeFormat.dayMonthYear.string(from: $0) }, accentIndex: index) { open(item) }
                                    .contextMenu { itemMenu(item) }
                            }
                        }
                        .padding(.horizontal, 6)
                        .padding(.vertical, 8)
                    }
                }
            }
        }
    }

    private var filterBar: some View {
        HStack(spacing: 12) {
            SearchField(placeholder: isMovies ? tr("بحث عن فيلم...", "Search movies...") : tr("بحث عن مسلسل...", "Search series..."), text: $vm.search, accent: .red, height: 50)
                .frame(minWidth: 220)
                .onChange(of: vm.search) { _ in vm.load(model) }
            DropdownButton(options: sortOptions, selection: Binding(get: { vm.sort }, set: { vm.sort = $0; vm.load(model) }),
                           width: 210, height: 50, accent: Theme.redBright)
                .shadow(color: Theme.red.opacity(0.5), radius: 8)
            DropdownButton(options: [(0, tr("سنة الإنتاج", "Year"))] + vm.years.map { ($0, String($0)) },
                           selection: Binding(get: { vm.year }, set: { vm.year = $0; vm.load(model) }), width: 180, height: 50, leadingIcon: "calendar")
            DropdownButton(options: [("", tr("النوع", "Genre"))] + vm.genres.map { ($0, $0) },
                           selection: Binding(get: { vm.genre }, set: { vm.genre = $0; vm.load(model) }), width: 180, height: 50, leadingIcon: "square.stack.3d.up")
            DropdownButton(options: [("", tr("الجودة", "Quality")), ("4K", "4K"), ("FHD", "FHD"), ("HD", "HD"), ("SD", "SD")],
                           selection: Binding(get: { vm.quality }, set: { vm.quality = $0; vm.load(model) }), width: 180, height: 50, leadingIcon: "4k.tv")
        }
        .fixedLTR()
    }

    private var sortOptions: [(SortOption, String)] {
        [(.recentlyAdded, "Recently Added"), (.name, tr("الاسم", "Name")), (.rating, tr("التقييم", "Rating")),
         (.year, tr("سنة الإنتاج", "Year")), (.providerOrder, tr("ترتيب المزود", "Provider order"))]
    }

    @ViewBuilder private func itemMenu(_ item: CatalogItem) -> some View {
        let t: ContentType = isMovies ? .movie : .series
        Button(model.isFavorite(t, item.itemID) ? tr("إزالة من المفضلة", "Remove from favorites") : tr("إضافة إلى المفضلة", "Add to favorites")) {
            model.toggleFavorite(t, item.itemID)
        }
        Button(model.isInWatchLater(t, item.itemID) ? tr("إزالة من المشاهدة لاحقاً", "Remove from Watch Later") : tr("المشاهدة لاحقاً", "Watch Later")) {
            model.toggleWatchLater(t, item.itemID)
        }
        ForEach(vm.groups) { g in
            Button(tr("إضافة إلى ", "Add to ") + g.name) { try? model.database.userData.setInGroup(g.id, t, item.itemID, true) }
        }
        Button(tr("إخفاء", "Hide")) { model.setHidden(kind: "item", value: "\(t.rawValue):\(item.itemID)", hidden: true) }
        Button(tr("قفل بالرمز السري", "Lock with PIN")) {
            if let pid = model.activePlaylist?.id { try? model.database.userData.setLocked(pid, kind: "item", value: "\(t.rawValue):\(item.itemID)", locked: true) }
        }
    }

    private func open(_ item: CatalogItem) {
        switch item.kind {
        case .movie(let m): model.guarded(type: .movie, id: String(m.id), categoryID: m.categoryID) { model.open(.movieDetails(m)) }
        case .series(let s): model.guarded(type: .series, id: String(s.id), categoryID: s.categoryID) { model.open(.seriesDetails(s)) }
        }
    }
}
