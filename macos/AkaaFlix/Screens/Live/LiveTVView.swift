import SwiftUI

enum BrowseSelection: Hashable {
    case mostWatched, favorites, all, recent, category(String), group(Int64)
}

final class LiveTVViewModel: ObservableObject {
    @Published var categories: [ContentCategory] = []
    @Published var groups: [CustomGroup] = []
    @Published var selection: BrowseSelection = .all
    @Published var channels: [Channel] = []
    @Published var totalCount = 0
    @Published var search = ""
    @Published var selected: Channel?
    @Published var favorites: Set<String> = []
    @Published var preview: PlayerController?
    private var loadToken = 0

    func loadSidebar(_ model: AppModel) {
        guard let pid = model.activePlaylist?.id else { return }
        let filter = model.baseFilter()
        categories = (try? model.database.catalog.categories(playlistID: pid, type: .live, hidden: filter.hidden, excludeAdult: filter.excludeAdult)) ?? []
        groups = model.customGroups()
        totalCount = (try? model.database.catalog.count(playlistID: pid, type: .live, filter: filter)) ?? 0
        favorites = Set((try? model.database.userData.favoriteIDs(pid, .live)) ?? [])
    }

    func loadChannels(_ model: AppModel) {
        guard let pid = model.activePlaylist?.id else { return }
        var f = model.baseFilter()
        f.search = search
        let db = model.database
        switch selection {
        case .all: break
        case .category(let id): f.categoryID = id
        case .favorites: f.onlyIDs = (try? db.userData.favoriteIDs(pid, .live)) ?? []
        case .mostWatched: f.onlyIDs = (try? db.userData.mostWatchedIDs(pid, .live, limit: 100)) ?? []
        case .recent: f.onlyIDs = ((try? db.userData.history(pid, limit: 200)) ?? []).filter { $0.type == .live }.map { $0.itemID }
        case .group(let gid): f.onlyIDs = (try? db.userData.groupItemIDs(gid, .live)) ?? []
        }
        loadToken += 1
        let token = loadToken
        let order = f.onlyIDs
        let sel = selection
        Task {
            var list = (try? await runInBackground { try db.catalog.channels(playlistID: pid, filter: f) }) ?? []
            if let order = order, sel == .mostWatched || sel == .favorites {
                let rank = Dictionary(uniqueKeysWithValues: order.enumerated().map { ($0.element, $0.offset) })
                list.sort { (rank[String($0.id)] ?? 0) < (rank[String($1.id)] ?? 0) }
            }
            let result = list
            await MainActor.run {
                guard token == self.loadToken else { return }
                self.channels = result
                if self.selected == nil { self.selected = result.first }
            }
        }
    }

    func select(_ channel: Channel, model: AppModel) {
        selected = channel
        guard let playlist = model.activePlaylist, let creds = model.credentials(for: playlist) else { return }
        if let p = preview, model.player !== p {
            if case .live = p.request {
                p.play(channel: channel)
                return
            }
        }
        let controller = PlayerController(model: model, request: .live(channel, list: channels), playlist: playlist, credentials: creds)
        controller.returnsToPreview = true
        preview = controller
        controller.start()
    }

    func stopPreview(_ model: AppModel) {
        guard let p = preview, model.player !== p else { return }
        p.stop()
        preview = nil
    }
}

struct LiveTVView: View {
    @EnvironmentObject var model: AppModel
    @StateObject private var vm = LiveTVViewModel()
    @State private var newGroupName = ""
    @State private var showNewGroup = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ScreenTitle(systemImage: "tv", title: tr("القنوات المباشرة", "Live TV"), subtitle: "Live TV")
                .padding(.leading, 28)
            GeometryReader { geo in
                HStack(alignment: .top, spacing: 18) {
                    groupsPanel.frame(width: max(300, geo.size.width * 0.27))
                    channelsPanel.frame(width: max(340, geo.size.width * 0.29))
                    previewPanel.frame(maxWidth: .infinity)
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 20)
            }
        }
        .onAppear {
            vm.loadSidebar(model)
            vm.loadChannels(model)
        }
        .onDisappear { vm.stopPreview(model) }
        .onChange(of: model.catalogVersion) { _ in vm.loadSidebar(model); vm.loadChannels(model) }
        .onChange(of: model.userDataVersion) { _ in vm.loadSidebar(model) }
        .sheet(isPresented: $showNewGroup) {
            NewGroupSheet(name: $newGroupName, onCreate: {
                model.createGroup(named: newGroupName)
                newGroupName = ""
                showNewGroup = false
                vm.loadSidebar(model)
            }, onCancel: { showNewGroup = false })
        }
    }

    // MARK: Groups

    private var groupsPanel: some View {
        NeonPanel(accent: .red, padding: 14) {
            VStack(spacing: 10) {
                PanelHeader(systemImage: "square.stack.3d.up", title: tr("المجموعات", "Groups"), size: 26)
                    .padding(.bottom, 4)
                ScrollView(.vertical, showsIndicators: false) {
                    LazyVStack(spacing: 8) {
                        GroupRow(title: tr("الأكثر مشاهدة", "Most Watched"), systemImage: "chart.bar.fill", isSelected: vm.selection == .mostWatched) { choose(.mostWatched) }
                        GroupRow(title: tr("المفضلة", "Favorites"), systemImage: "star.fill", iconColor: Theme.gold, isSelected: vm.selection == .favorites) { choose(.favorites) }
                        GroupRow(title: tr("إضافة مجموعة", "Add Group"), systemImage: "plus") { showNewGroup = true }
                        GroupRow(title: tr("كل القنوات", "All Channels"), systemImage: "tv", count: vm.totalCount, isSelected: vm.selection == .all) { choose(.all) }
                        ForEach(vm.groups) { g in
                            GroupRow(title: g.name, systemImage: "folder", isSelected: vm.selection == .group(g.id)) { choose(.group(g.id)) }
                                .contextMenu {
                                    Button(tr("حذف المجموعة", "Delete group")) {
                                        try? model.database.userData.deleteGroup(g.id)
                                        vm.loadSidebar(model)
                                    }
                                }
                        }
                        ForEach(vm.categories) { c in
                            GroupRow(title: c.name, systemImage: c.kind.systemImage, emoji: c.flag, count: c.count, isSelected: vm.selection == .category(c.id)) { choose(.category(c.id)) }
                                .contextMenu {
                                    Button(tr("إخفاء هذه المجموعة", "Hide this category")) { model.setHidden(kind: "category", value: c.id, hidden: true) }
                                    Button(tr("قفل بالرمز السري", "Lock with PIN")) {
                                        if let pid = model.activePlaylist?.id { try? model.database.userData.setLocked(pid, kind: "category", value: c.id, locked: true) }
                                    }
                                }
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
        }
    }

    private func choose(_ s: BrowseSelection) {
        vm.selection = s
        vm.loadChannels(model)
    }

    // MARK: Channels

    private var channelsPanel: some View {
        NeonPanel(accent: .blue, padding: 14) {
            VStack(spacing: 12) {
                HStack(spacing: 12) {
                    Image(systemName: "tv").font(.system(size: 28))
                    Text(tr("القنوات", "Channels")).font(.system(size: 26, weight: .heavy))
                    Text("(\(vm.channels.count))").font(.system(size: 22, weight: .bold)).monospacedDigit()
                }
                .foregroundColor(.white)
                .contentDirection()
                SearchField(placeholder: tr("بحث في القنوات...", "Search channels..."), text: $vm.search)
                    .onChange(of: vm.search) { _ in vm.loadChannels(model) }
                if vm.channels.isEmpty {
                    EmptyStateView(systemImage: "tv", title: tr("لا توجد قنوات", "No channels"))
                } else {
                    ScrollViewReader { proxy in
                        ScrollView(.vertical, showsIndicators: true) {
                            LazyVStack(spacing: 6) {
                                ForEach(vm.channels) { c in
                                    ChannelRow(channel: c, isSelected: vm.selected?.id == c.id, isFavorite: vm.favorites.contains(String(c.id)),
                                               onSelect: { tap(c) },
                                               onFavorite: { model.toggleFavorite(.live, String(c.id)); vm.loadSidebar(model) })
                                        .id(c.id)
                                        .contextMenu { channelMenu(c) }
                                }
                            }
                        }
                        .onAppear { if let s = vm.selected { proxy.scrollTo(s.id, anchor: .center) } }
                    }
                }
            }
        }
    }

    @ViewBuilder private func channelMenu(_ c: Channel) -> some View {
        Button(tr("تشغيل بملء الشاشة", "Play full screen")) { model.guarded(type: .live, id: String(c.id), categoryID: c.categoryID) { model.play(.live(c, list: vm.channels)) } }
        Button(vm.favorites.contains(String(c.id)) ? tr("إزالة من المفضلة", "Remove from favorites") : tr("إضافة إلى المفضلة", "Add to favorites")) {
            model.toggleFavorite(.live, String(c.id)); vm.loadSidebar(model)
        }
        ForEach(vm.groups) { g in
            Button(tr("إضافة إلى ", "Add to ") + g.name) { try? model.database.userData.setInGroup(g.id, .live, String(c.id), true) }
        }
        Button(tr("إخفاء القناة", "Hide channel")) { model.setHidden(kind: "item", value: "live:\(c.id)", hidden: true) }
    }

    private func tap(_ c: Channel) {
        if vm.selected?.id == c.id, vm.preview != nil {
            openFull()
            return
        }
        model.guarded(type: .live, id: String(c.id), categoryID: c.categoryID) { vm.select(c, model: model) }
    }

    private func openFull() {
        guard let p = vm.preview else { return }
        model.player = p
        p.pokeControls()
    }

    // MARK: Preview

    private var previewPanel: some View {
        NeonPanel(accent: .mixed, padding: 14) {
            VStack(spacing: 12) {
                ZStack {
                    if let p = vm.preview, model.player !== p {
                        VideoSurface(engine: p.engine)
                        PreviewStatus(controller: p)
                    } else {
                        ZStack {
                            Color.black
                            if let c = vm.selected {
                                VStack(spacing: 14) {
                                    ChannelLogo(url: c.icon, name: c.name, width: 180, height: 90)
                                    NeonButton(title: tr("تشغيل", "Play"), systemImage: "play.fill", kind: .red) { tap(c) }
                                }
                            }
                        }
                    }
                    VStack {
                        HStack {
                            LiveBadge()
                            Spacer()
                            if let c = vm.selected { ChannelLogo(url: c.icon, name: c.name, width: 150, height: 40) }
                        }
                        Spacer()
                    }
                    .padding(14)
                }
                .aspectRatio(16 / 9, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .onTapGesture(count: 2) { openFull() }

                if let p = vm.preview, model.player !== p {
                    PreviewControls(controller: p, onFullScreen: openFull)
                }

                if let c = vm.selected {
                    HStack(spacing: 14) {
                        ChannelLogo(url: c.icon, name: c.name, width: 110, height: 62)
                        VStack(alignment: .leading, spacing: 6) {
                            Text(c.name).font(.system(size: 22, weight: .heavy)).foregroundColor(.white).lineLimit(1)
                            if let now = vm.preview?.nowPlaying {
                                HStack(spacing: 8) {
                                    Text(tr("مباشر الآن : ", "Live now: ") + now.title).font(.system(size: 15, weight: .semibold)).foregroundColor(Theme.textSecondary).lineLimit(1)
                                    Circle().fill(Theme.redBright).frame(width: 12, height: 12)
                                }
                            }
                        }
                        Spacer()
                        Button(action: { model.toggleFavorite(.live, String(c.id)); vm.loadSidebar(model) }) {
                            Image(systemName: vm.favorites.contains(String(c.id)) ? "star.fill" : "star")
                                .font(.system(size: 30)).foregroundColor(vm.favorites.contains(String(c.id)) ? Theme.gold : .white)
                        }.buttonStyle(PlainButtonStyle())
                        Button(action: { model.showToast("\(c.name) · #\(c.number)" + (c.epgChannelID.map { " · EPG: \($0)" } ?? ""), .info) }) {
                            Image(systemName: "info.circle").font(.system(size: 30)).foregroundColor(.white)
                        }.buttonStyle(PlainButtonStyle())
                    }
                    .padding(12)
                    .background(RoundedRectangle(cornerRadius: 16).fill(Color.black.opacity(0.35)))
                    .contentDirection()
                }
                EPGListPanel(entries: Array((vm.preview?.epg ?? []).prefix(4)), compact: true)
                Spacer(minLength: 0)
            }
        }
    }
}

/// One row of the channel list: number, logo, name, quality and favourite star.
struct ChannelRow: View {
    let channel: Channel
    let isSelected: Bool
    let isFavorite: Bool
    let onSelect: () -> Void
    let onFavorite: () -> Void
    @State private var hovering = false

    var body: some View {
        HStack(spacing: 12) {
            Text(String(format: "%03d", channel.number)).font(.system(size: 17, weight: .bold)).monospacedDigit().foregroundColor(.white).frame(width: 44)
            ChannelLogo(url: channel.icon, name: channel.name, width: 62, height: 34)
            Text(channel.name).font(.system(size: 16, weight: .bold)).foregroundColor(.white).lineLimit(1)
            Spacer(minLength: 4)
            if let q = channel.quality {
                Text(q).font(.system(size: 12, weight: .heavy)).foregroundColor(.white)
                    .padding(.horizontal, 5).padding(.vertical, 2)
                    .overlay(RoundedRectangle(cornerRadius: 5).strokeBorder(Color.white, lineWidth: 1.4))
            }
            Button(action: onFavorite) {
                Image(systemName: isFavorite ? "star.fill" : "star").font(.system(size: 20)).foregroundColor(isFavorite ? Theme.gold : .white)
            }.buttonStyle(PlainButtonStyle())
        }
        .padding(.horizontal, 12)
        .frame(height: 52)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(isSelected ? Theme.redGradient : linear([Color(hex: 0x161824).opacity(hovering ? 1 : 0.85), Color(hex: 0x0C0D15).opacity(0.85)])))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(isSelected ? Color(hex: 0xFF8080) : Color.white.opacity(0.06), lineWidth: isSelected ? 1.6 : 1))
        .shadow(color: isSelected ? Theme.red.opacity(0.7) : .clear, radius: 10)
        .contentShape(Rectangle())
        .onTapGesture(perform: onSelect)
        .onHover { hovering = $0 }
        .fixedLTR()
    }
}

/// Loading / error state drawn over the preview video.
struct PreviewStatus: View {
    @ObservedObject var controller: PlayerController
    var body: some View {
        Group {
            switch controller.phase {
            case .opening, .reconnecting, .buffering:
                VStack(spacing: 8) {
                    ProgressView()
                    if !controller.attemptTrail.isEmpty {
                        Text(controller.attemptTrail).font(.system(size: 11, weight: .semibold)).foregroundColor(.white.opacity(0.8))
                    }
                }
            case .failed(let m):
                VStack(spacing: 10) {
                    Text(m).font(.system(size: 14, weight: .bold)).foregroundColor(.white).multilineTextAlignment(.center)
                    NeonButton(title: tr("إعادة المحاولة", "Retry"), systemImage: "arrow.clockwise", kind: .red, height: 36, fontSize: 13) { controller.retry() }
                }
                .padding()
            default:
                EmptyView()
            }
        }
    }
}

/// Control bar under the preview: pause, volume, slider, HD, aspect, fullscreen.
struct PreviewControls: View {
    @ObservedObject var controller: PlayerController
    let onFullScreen: () -> Void

    var body: some View {
        HStack(spacing: 18) {
            Button(action: { controller.togglePlay() }) {
                Image(systemName: controller.isPlaying ? "pause.fill" : "play.fill").font(.system(size: 26, weight: .bold)).foregroundColor(.white)
            }.buttonStyle(PlainButtonStyle())
            Button(action: { controller.toggleMute() }) {
                Image(systemName: controller.isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill").font(.system(size: 22)).foregroundColor(.white)
            }.buttonStyle(PlainButtonStyle())
            NeonSlider(value: Binding(get: { controller.isMuted ? 0 : controller.volume }, set: { controller.setVolume($0) }), height: 5, knob: 18)
            Text(controller.videoSize.height >= 900 ? "FHD" : "HD").font(.system(size: 14, weight: .heavy)).foregroundColor(.white)
                .padding(.horizontal, 8).padding(.vertical, 4)
                .overlay(RoundedRectangle(cornerRadius: 6).strokeBorder(Color.white, lineWidth: 1.5))
            Button(action: {
                let all = AspectMode.allCases
                controller.setAspect(all[((all.firstIndex(of: controller.aspect) ?? 0) + 1) % all.count])
            }) {
                Image(systemName: "rectangle").font(.system(size: 22)).foregroundColor(.white)
            }.buttonStyle(PlainButtonStyle())
            Button(action: onFullScreen) {
                Image(systemName: "arrow.up.left.and.arrow.down.right").font(.system(size: 22)).foregroundColor(.white)
            }.buttonStyle(PlainButtonStyle())
        }
        .padding(.horizontal, 6)
        .fixedLTR()
    }
}

struct NewGroupSheet: View {
    @Binding var name: String
    let onCreate: () -> Void
    let onCancel: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Text(tr("إضافة مجموعة", "Add Group")).font(.system(size: 20, weight: .heavy)).foregroundColor(.white)
            TextField(tr("اسم المجموعة", "Group name"), text: $name, onCommit: onCreate)
                .textFieldStyle(PlainTextFieldStyle())
                .font(.system(size: 17, weight: .semibold))
                .padding(.horizontal, 14).frame(height: 44)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color.black.opacity(0.4)))
            HStack(spacing: 12) {
                NeonButton(title: tr("إضافة", "Add"), systemImage: "plus", kind: .red, action: onCreate)
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                NeonButton(title: tr("إلغاء", "Cancel"), systemImage: "xmark", kind: .dark, action: onCancel)
            }
        }
        .padding(26)
        .frame(width: 400)
        .background(Theme.panel)
        .contentDirection()
    }
}
