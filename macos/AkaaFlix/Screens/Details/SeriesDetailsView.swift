import SwiftUI

final class SeriesDetailsViewModel: ObservableObject {
    @Published var details: SeriesDetails?
    @Published var progress: [WatchProgress] = []
    @Published var season: Int = 1
    @Published var isFavorite = false
    @Published var loading = true
    @Published var error: String?

    func load(_ model: AppModel, series: Series) {
        guard let playlist = model.activePlaylist else { return }
        isFavorite = model.isFavorite(.series, String(series.id))
        progress = (try? model.database.userData.progressForSeries(playlist.id, seriesID: series.id)) ?? []
        guard let creds = model.credentials(for: playlist) else { loading = false; return }
        let client = model.client
        Task {
            do {
                let d = try await client.seriesDetails(creds, series: series)
                await MainActor.run {
                    self.details = d
                    let latestSeason = self.progress.first?.season
                    self.season = latestSeason.flatMap { s in d.seasons.contains(where: { $0.number == s }) ? s : nil } ?? d.seasons.first?.number ?? 1
                    self.loading = false
                }
            } catch {
                await MainActor.run {
                    self.error = AkaaError.from(error).message
                    self.loading = false
                }
            }
        }
    }

    func refreshProgress(_ model: AppModel, series: Series) {
        guard let pid = model.activePlaylist?.id else { return }
        progress = (try? model.database.userData.progressForSeries(pid, seriesID: series.id)) ?? []
    }
}

struct SeriesDetailsView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.contentSize) private var size
    @StateObject private var vm = SeriesDetailsViewModel()
    let series: Series

    private var s: Series { vm.details?.series ?? series }
    private var titles: (primary: String, secondary: String?) { ContentText.displayTitles(series.name) }

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .top) {
                RemoteImage(url: s.backdrop ?? s.cover, contentMode: .fill, placeholder: .backdrop, maxPixel: 1800)
                    .frame(width: geo.size.width, height: geo.size.height * 0.62)
                    .clipped()
                    .overlay(linear([Color.black.opacity(0.35), .clear, Theme.background.opacity(0.85), Theme.background]))
                    .overlay(linear([.clear, Color.black.opacity(0.25), Color.black.opacity(0.75)], .leading, .trailing))
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 18) {
                        header
                        HStack(alignment: .bottom) {
                            watchedPill
                            Spacer()
                            infoBlock.frame(maxWidth: 860)
                        }
                        .padding(.horizontal, 40)
                        HStack(alignment: .top, spacing: 26) {
                            detailsPanel.frame(width: size.width < 1300 ? 440 : max(520, geo.size.width * 0.36))
                            episodesPanel
                        }
                        .frame(height: max(400, geo.size.height * 0.46))
                        .padding(.horizontal, 40)
                        .padding(.bottom, 24)
                    }
                }
            }
        }
        .onAppear { vm.load(model, series: series) }
        .onChange(of: model.userDataVersion) { _ in vm.refreshProgress(model, series: series); vm.isFavorite = model.isFavorite(.series, String(series.id)) }
    }

    private var header: some View {
        HStack(spacing: 18) {
            Button(action: { model.back() }) {
                Image(systemName: "arrow.left").font(.system(size: 24, weight: .bold)).foregroundColor(.white)
                    .frame(width: 58, height: 58)
                    .background(Circle().fill(Color.black.opacity(0.55)))
                    .overlay(Circle().strokeBorder(Color.white.opacity(0.35), lineWidth: 1.4))
            }.buttonStyle(PlainButtonStyle())
            LogoView(height: 46)
            HeaderDivider(height: 36)
            Text(tr("المسلسلات", "Series")).font(.system(size: 30, weight: .heavy)).foregroundColor(.white)
            Spacer()
            CircleIconButton(systemImage: "magnifyingglass", size: 52) { model.open(.search) }
            CircleIconButton(systemImage: "bell", showsDot: model.unreadNotifications > 0, size: 52) { model.open(.notifications) }
            CircleIconButton(systemImage: "heart", size: 52) { model.open(.favorites) }
            CircleIconButton(systemImage: "gearshape", size: 52) { model.open(.settings(.general)) }
        }
        .padding(.horizontal, 28)
        .padding(.top, 14)
    }

    private var infoBlock: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(titles.primary).font(.system(size: 60, weight: .heavy)).foregroundColor(.white).lineLimit(1).minimumScaleFactor(0.5)
                .shadow(color: .black, radius: 8)
            if let o = vm.details?.originalName ?? titles.secondary {
                Text(o).font(.system(size: 32, weight: .medium)).foregroundColor(.white)
            }
            HStack(spacing: 12) {
                if let r = s.rating { RatingLabel(rating: r, size: 26) }
                if let y = s.year { Chip(text: String(y), fontSize: 20) }
                if let a = vm.details?.ageRating { Chip(text: a, tint: .white, filled: true, fontSize: 20) }
                if let count = vm.details?.seasons.count, count > 0 { Text(tr("\(count) مواسم", "\(count) seasons")).font(.system(size: 22, weight: .bold)).foregroundColor(.white) }
                if let q = s.quality { QualityBadge(quality: q, large: true) }
            }
            .contentDirection()
            if let g = vm.details?.genres ?? Optional(ContentText.splitList(s.genre)), !g.isEmpty {
                HStack(spacing: 10) { ForEach(g.prefix(5), id: \.self) { Chip(text: $0, fontSize: 18) } }
            }
            if let plot = s.plot {
                Text(plot).font(.system(size: 19, weight: .medium)).foregroundColor(.white).lineLimit(3)
                    .shadow(color: .black, radius: 4)
            }
            HStack(spacing: 18) {
                NeonButton(title: tr("مواصلة المشاهدة", "Continue Watching"), systemImage: "play.fill", kind: .red, height: 58, fontSize: 21) { continueWatching() }
                    .disabled(vm.details?.allEpisodes.isEmpty ?? true)
                NeonButton(title: tr("تشغيل", "Play"), systemImage: "play.fill", kind: .outlineBlue, height: 58, fontSize: 21) { playFirst() }
                    .disabled(vm.details?.allEpisodes.isEmpty ?? true)
                NeonButton(title: vm.isFavorite ? tr("في المفضلة", "In Favorites") : tr("أضف إلى المفضلة", "Add to Favorites"),
                           systemImage: vm.isFavorite ? "heart.fill" : "heart", kind: .outlineBlue, height: 58, fontSize: 20) {
                    model.toggleFavorite(.series, String(series.id))
                    vm.isFavorite.toggle()
                }
            }
        }
        .contentDirection()
    }

    private var watchedPill: some View {
        let total = vm.details?.episodeCount ?? 0
        let watched = vm.details.map { EpisodeNavigator.watchedCount(details: $0, progress: vm.progress) } ?? 0
        let fraction = total > 0 ? Double(watched) / Double(total) : 0
        return VStack(alignment: .trailing, spacing: 10) {
            Text(tr("تمت مشاهدة \(watched) من \(total) حلقة", "Watched \(watched) of \(total) episodes"))
                .font(.system(size: 18, weight: .bold)).foregroundColor(.white)
            HStack(spacing: 12) {
                Text("\(Int((fraction * 100).rounded()))%").font(.system(size: 17, weight: .bold)).foregroundColor(.white).monoDigits()
                ProgressBar(fraction: fraction, height: 7)
            }
        }
        .padding(.horizontal, 24).padding(.vertical, 14)
        .frame(width: min(480, size.width * 0.27))
        .background(Capsule().fill(Color.black.opacity(0.65)))
        .overlay(Capsule().strokeBorder(Color.white.opacity(0.25), lineWidth: 1.2))
        .opacity(total > 0 ? 1 : 0)
    }

    private var detailsPanel: some View {
        NeonPanel(accent: .red, padding: 18) {
            HStack(alignment: .top, spacing: 18) {
                RemoteImage(url: s.cover, contentMode: .fill, placeholder: .poster, maxPixel: 600, title: s.name)
                    .frame(width: size.width < 1300 ? 150 : 220, height: size.width < 1300 ? 210 : 300)
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.white.opacity(0.2), lineWidth: 1))
                VStack(spacing: 10) {
                    infoRow("person.2", tr("النوع", "Genre"), s.genre ?? "—")
                    infoRow("globe", tr("الدولة", "Country"), vm.details?.country ?? "—")
                    infoRow("captions.bubble", tr("اللغة", "Language"), vm.details?.language ?? "—")
                    infoRow("clock", tr("مدة الحلقة", "Episode length"), episodeLength)
                    infoRow("display", tr("جودة العرض", "Quality"), s.quality ?? "—")
                }
            }
            .fixedLTR()
        }
    }

    private var episodeLength: String {
        if let r = s.episodeRunTime, let sec = TimeFormat.parseDuration(r) { return tr("\(sec / 60) دقيقة", "\(sec / 60) min") }
        if let first = vm.details?.allEpisodes.first?.durationSeconds { return tr("\(first / 60) دقيقة", "\(first / 60) min") }
        return "—"
    }

    private func infoRow(_ icon: String, _ label: String, _ value: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon).font(.system(size: 22)).foregroundColor(.white).frame(width: 30)
            Text(label + " :").font(.system(size: 17, weight: .bold)).foregroundColor(.white)
            Text(value).font(.system(size: 17, weight: .semibold)).foregroundColor(.white).lineLimit(1)
            Spacer()
        }
        .padding(.horizontal, 12).frame(height: 50)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.white.opacity(0.06)))
        .contentDirection()
    }

    private var episodesPanel: some View {
        NeonPanel(accent: .blue, padding: 18) {
            VStack(spacing: 14) {
                HStack {
                    Spacer()
                    HStack(spacing: 10) {
                        Image(systemName: "square.stack.3d.up").font(.system(size: 22))
                        Text(tr("المواسم", "Seasons")).font(.system(size: 22, weight: .heavy))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 28).frame(height: 50)
                    .background(Capsule().fill(Theme.redGradient))
                    .shadow(color: Theme.red.opacity(0.7), radius: 8)
                    .contentDirection()
                }
                if let error = vm.error {
                    Text(error).foregroundColor(Theme.textSecondary).frame(maxWidth: .infinity, minHeight: 120)
                } else if vm.loading {
                    ProgressView().frame(maxWidth: .infinity, minHeight: 120)
                } else if let d = vm.details {
                    seasonTabs(d)
                    ScrollView(.vertical, showsIndicators: true) {
                        LazyVStack(spacing: 10) {
                            ForEach(d.episodesBySeason[vm.season] ?? []) { ep in
                                EpisodeRow(episode: ep, progress: vm.progress.first(where: { $0.itemID == ep.id }),
                                           isCurrent: vm.progress.first?.itemID == ep.id) { play(ep, details: d) }
                            }
                        }
                    }
                }
            }
        }
    }

    private func seasonTabs(_ d: SeriesDetails) -> some View {
        ScrollViewReader { proxy in
            HStack(spacing: 10) {
                tabArrow("chevron.left") { shiftSeason(d, -1, proxy) }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(d.seasons) { season in
                            Button(action: { vm.season = season.number }) {
                                Text(tr("الموسم \(season.number)", "Season \(season.number)"))
                                    .font(.system(size: 18, weight: .bold)).foregroundColor(.white)
                                    .padding(.horizontal, 26).frame(height: 48)
                                    .background(Capsule().fill(vm.season == season.number ? Theme.redGradient : Theme.darkPill))
                                    .overlay(Capsule().strokeBorder(vm.season == season.number ? Color(hex: 0xFF8080) : Color.white.opacity(0.15), lineWidth: 1.3))
                                    .shadow(color: vm.season == season.number ? Theme.red.opacity(0.7) : .clear, radius: 8)
                            }
                            .buttonStyle(PlainButtonStyle())
                            .id(season.number)
                        }
                    }
                    .padding(.vertical, 4)
                    .contentDirection()
                }
                tabArrow("chevron.right") { shiftSeason(d, 1, proxy) }
            }
            .fixedLTR()
        }
    }

    private func tabArrow(_ icon: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 20, weight: .bold)).foregroundColor(.white)
                .frame(width: 48, height: 48).background(Circle().fill(Theme.darkPill))
                .overlay(Circle().strokeBorder(Color.white.opacity(0.2), lineWidth: 1))
        }.buttonStyle(PlainButtonStyle())
    }

    private func shiftSeason(_ d: SeriesDetails, _ delta: Int, _ proxy: ScrollViewProxy) {
        let numbers = d.seasons.map { $0.number }
        guard let idx = numbers.firstIndex(of: vm.season) else { return }
        let n = idx + (Lang.isArabic ? -delta : delta)
        guard numbers.indices.contains(n) else { return }
        vm.season = numbers[n]
        withAnimation { proxy.scrollTo(numbers[n], anchor: .center) }
    }

    private func play(_ ep: Episode, details d: SeriesDetails) {
        let p = vm.progress.first(where: { $0.itemID == ep.id })
        let resume = p.flatMap { ContinueWatchingRules.resumePosition(position: $0.position, duration: $0.duration) }
        model.guarded(type: .series, id: String(series.id), categoryID: series.categoryID, ageRating: d.ageRating) {
            if let dl = model.activePlaylist.flatMap({ model.database.userData.findDownload($0.id, .episode, ep.id) }), dl.status == .completed,
               let path = dl.filePath, FileManager.default.fileExists(atPath: path) {
                model.play(.file(URL(fileURLWithPath: path), title: "\(series.name) · S\(ep.season)E\(ep.episodeNumber)", type: .episode, itemID: ep.id))
            } else {
                model.play(.episode(ep, series: s, details: d, resumeAt: resume))
            }
        }
    }

    private func continueWatching() {
        guard let d = vm.details, let target = EpisodeNavigator.resumeTarget(details: d, progress: vm.progress) else { return }
        model.guarded(type: .series, id: String(series.id), categoryID: series.categoryID, ageRating: d.ageRating) {
            model.play(.episode(target.0, series: s, details: d, resumeAt: target.1))
        }
    }

    private func playFirst() {
        guard let d = vm.details, let first = d.allEpisodes.first else { return }
        play(first, details: d)
    }
}

/// Episode row: thumbnail with duration, "الحلقة 1 : …", plot and a play button.
struct EpisodeRow: View {
    @EnvironmentObject var model: AppModel
    let episode: Episode
    let progress: WatchProgress?
    let isCurrent: Bool
    let onPlay: () -> Void
    @State private var hovering = false

    var body: some View {
        Button(action: onPlay) {
            HStack(spacing: 16) {
                ZStack(alignment: .bottomLeading) {
                    RemoteImage(url: episode.thumbnail, contentMode: .fill, placeholder: .episode, maxPixel: 420)
                        .frame(width: 210, height: 96)
                        .clipped()
                    if let d = episode.durationSeconds {
                        Text(TimeFormat.clock(Double(d))).font(.system(size: 15, weight: .bold)).foregroundColor(.white)
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .background(RoundedRectangle(cornerRadius: 6).fill(Color.black.opacity(0.7)))
                            .padding(8)
                    }
                    if let p = progress, p.fraction > 0 {
                        ProgressBar(fraction: p.fraction, height: 4).padding(.horizontal, 6).padding(.bottom, 2)
                            .frame(maxHeight: .infinity, alignment: .bottom)
                    }
                }
                .frame(width: 210, height: 96)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .fixedLTR()
                VStack(alignment: .leading, spacing: 8) {
                    Text(tr("الحلقة \(episode.episodeNumber) : \(episode.title)", "Episode \(episode.episodeNumber): \(episode.title)"))
                        .font(.system(size: 20, weight: .heavy)).foregroundColor(.white).lineLimit(1)
                    if let plot = episode.plot {
                        Text(plot).font(.system(size: 15, weight: .medium)).foregroundColor(.white.opacity(0.88)).lineLimit(2)
                    }
                }
                Spacer()
                Image(systemName: "play.fill").font(.system(size: 20, weight: .bold)).foregroundColor(.white)
                    .frame(width: 54, height: 54)
                    .overlay(Circle().strokeBorder(Color.white, lineWidth: 2))
                Button(action: download) {
                    Image(systemName: "arrow.down.to.line").font(.system(size: 18)).foregroundColor(.white.opacity(0.85))
                }.buttonStyle(PlainButtonStyle()).help(tr("تحميل", "Download"))
            }
            .padding(10)
            .background(RoundedRectangle(cornerRadius: 18).fill(isCurrent ? linear([Theme.red.opacity(0.7), Theme.redDeep.opacity(0.8)], .leading, .trailing) : linear([Color(hex: 0x171925).opacity(hovering ? 1 : 0.9), Color(hex: 0x0B0C13).opacity(0.9)])))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(isCurrent ? Theme.redBright : Color.white.opacity(0.1), lineWidth: isCurrent ? 2 : 1))
            .shadow(color: isCurrent ? Theme.red.opacity(0.6) : .clear, radius: 10)
            .contentShape(Rectangle())
            .contentDirection()
        }
        .buttonStyle(PlainButtonStyle())
        .onHover { hovering = $0 }
    }

    private func download() {
        DownloadManager.shared.enqueueEpisode(model: model, episode: episode)
    }
}
