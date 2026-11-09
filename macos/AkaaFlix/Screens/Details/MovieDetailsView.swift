import SwiftUI

final class MovieDetailsViewModel: ObservableObject {
    @Published var details: MovieDetails?
    @Published var similar: [Movie] = []
    @Published var progress: WatchProgress?
    @Published var isFavorite = false
    @Published var loading = true
    @Published var download: DownloadRecord?

    func load(_ model: AppModel, movie: Movie) {
        guard let playlist = model.activePlaylist else { return }
        let pid = playlist.id
        progress = model.database.userData.progress(pid, .movie, String(movie.id))
        isFavorite = model.isFavorite(.movie, String(movie.id))
        download = model.database.userData.findDownload(pid, .movie, String(movie.id))
        var f = model.baseFilter()
        f.categoryID = movie.categoryID
        f.sort = .rating
        f.limit = 20
        similar = ((try? model.database.catalog.movies(playlistID: pid, filter: f)) ?? []).filter { $0.id != movie.id }
        guard let creds = model.credentials(for: playlist) else { loading = false; return }
        let client = model.client
        Task {
            let d = try? await client.movieDetails(creds, movie: movie)
            await MainActor.run {
                self.details = d ?? MovieDetails(movie: movie, plot: movie.plot, genre: movie.genre)
                self.loading = false
            }
        }
    }
}

struct MovieDetailsView: View {
    @EnvironmentObject var model: AppModel
    @StateObject private var vm = MovieDetailsViewModel()
    @State private var showCast = false
    let movie: Movie

    private var m: Movie { vm.details?.movie ?? movie }
    private var titles: (primary: String, secondary: String?) { ContentText.displayTitles(movie.name) }

    var body: some View {
        ZStack(alignment: .topLeading) {
            backdrop
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 22) {
                    HStack(alignment: .top, spacing: 34) {
                        posterCard
                        infoBlock
                        Spacer(minLength: 0)
                    }
                    actionButtons
                    castSection
                    if !vm.similar.isEmpty {
                        SectionTitle(title: tr("أفلام مشابهة", "Similar Movies"))
                        CarouselRow(items: vm.similar) { s in
                            PosterCard(title: s.name, imageURL: s.poster, quality: s.quality, rating: s.rating, showsDetails: true) { model.open(.movieDetails(s)) }
                                .frame(width: 250)
                        }
                    }
                }
                .padding(.horizontal, 40)
                .padding(.vertical, 20)
            }
            BackButton().padding(.leading, 12).padding(.top, 4).opacity(model.canGoBack ? 1 : 0)
        }
        .onAppear { vm.load(model, movie: movie) }
        .onChange(of: model.userDataVersion) { _ in vm.isFavorite = model.isFavorite(.movie, String(movie.id)) }
        .sheet(isPresented: $showCast) { CastSheet(names: vm.details?.castList ?? [], director: vm.details?.director, onClose: { showCast = false }) }
    }

    private var backdrop: some View {
        GeometryReader { geo in
            HStack {
                Spacer()
                RemoteImage(url: vm.details?.backdrop ?? m.poster, contentMode: .fill, placeholder: .backdrop, maxPixel: 1600)
                    .frame(width: geo.size.width * 0.62, height: geo.size.height * 0.62)
                    .clipped()
                    .mask(linear([.clear, Color.black.opacity(0.8), .black], .leading, .trailing))
                    .overlay(linear([.clear, .clear, Theme.background], .top, .bottom))
            }
        }
        .allowsHitTesting(false)
    }

    private var posterCard: some View {
        ZStack(alignment: .bottom) {
            RemoteImage(url: m.poster, contentMode: .fill, placeholder: .poster, maxPixel: 700, title: m.name)
                .frame(width: 290, height: 380)
                .clipped()
            linear([.clear, Color.black.opacity(0.9)]).frame(height: 140)
            VStack(spacing: 4) {
                Text(titles.primary).font(.system(size: 30, weight: .heavy)).foregroundColor(.white).lineLimit(2).multilineTextAlignment(.center)
                if let s = titles.secondary ?? vm.details?.originalName { Text(s).font(.system(size: 17, weight: .semibold)).foregroundColor(.white).lineLimit(1) }
            }
            .padding(.bottom, 16).padding(.horizontal, 10)
        }
        .frame(width: 290, height: 380)
        .overlay(HStack {
            if let q = m.quality { QualityBadge(quality: q, large: false).scaleEffect(1.4).padding(10) }
            Spacer()
            if (m.added.map { Date().timeIntervalSince($0) < 14 * 86_400 } ?? false) { NewBadge().scaleEffect(1.3).padding(12) }
        }, alignment: .top)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Theme.neonStrokeVertical, lineWidth: 2))
        .shadow(color: Theme.red.opacity(0.6), radius: 16)
    }

    private var infoBlock: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(titles.primary).font(.system(size: 52, weight: .heavy)).foregroundColor(.white).lineLimit(2).minimumScaleFactor(0.6)
            if let s = vm.details?.originalName ?? titles.secondary {
                Text(s).font(.system(size: 28, weight: .medium)).foregroundColor(.white.opacity(0.92))
            }
            if let r = m.rating {
                HStack(spacing: 8) {
                    Image(systemName: "star.fill").foregroundColor(Theme.gold).font(.system(size: 26))
                    Text(String(format: "%.1f", r)).font(.system(size: 28, weight: .heavy)).foregroundColor(.white)
                    if let age = vm.details?.ageRating { Chip(text: age, fontSize: 13) }
                }
                .fixedLTR()
            }
            HStack(spacing: 12) {
                if let date = vm.details?.releaseDate ?? m.added.map({ TimeFormat.dayMonthYear.string(from: $0) }) {
                    infoChip("calendar", date)
                }
                if let d = vm.details?.durationSeconds { infoChip("clock", TimeFormat.clock(Double(d))) }
                if let q = m.quality { QualityBadge(quality: q, large: true) }
                if m.isHDR { Chip(text: "HDR", fontSize: 20) }
            }
            .fixedLTR()
            if let genres = vm.details?.genres, !genres.isEmpty {
                HStack(spacing: 10) { ForEach(genres.prefix(5), id: \.self) { Chip(text: $0, fontSize: 16) } }
            }
            if let plot = vm.details?.plot ?? m.plot {
                Text(plot).font(.system(size: 18, weight: .medium)).foregroundColor(.white.opacity(0.95)).lineLimit(4).frame(maxWidth: 760, alignment: .leading)
            } else if vm.loading {
                ProgressView().scaleEffect(0.8)
            }
        }
        .contentDirection()
        .frame(maxWidth: 820, alignment: .leading)
    }

    private func infoChip(_ icon: String, _ text: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon).font(.system(size: 20))
            Text(text).font(.system(size: 20, weight: .bold)).monoDigits()
        }
        .foregroundColor(.white)
        .padding(.horizontal, 16).frame(height: 46)
        .background(Capsule().fill(Theme.darkPill))
        .overlay(Capsule().strokeBorder(Color.white.opacity(0.25), lineWidth: 1))
    }

    private var actionButtons: some View {
        HStack(spacing: 16) {
            if let p = vm.progress, let resume = ContinueWatchingRules.resumePosition(position: p.position, duration: p.duration) {
                NeonButton(title: tr("مواصلة المشاهدة", "Continue Watching"), systemImage: "play.circle", kind: .outlineRed, height: 54, fontSize: 18) { play(resume) }
            }
            NeonButton(title: tr("تشغيل الفيلم", "Play Movie"), systemImage: "play.fill", kind: .red, height: 54, fontSize: 19) { play(nil) }
            NeonButton(title: tr("المفضلة", "Favorite"), systemImage: vm.isFavorite ? "heart.fill" : "heart", kind: .dark, height: 54, fontSize: 18) {
                model.toggleFavorite(.movie, String(movie.id))
                vm.isFavorite.toggle()
            }
            NeonButton(title: downloadTitle, systemImage: "arrow.down.to.line", kind: .dark, height: 54, fontSize: 18) { startDownload() }
            NeonButton(title: tr("معلومات", "Info"), systemImage: "info.circle", kind: .dark, height: 54, fontSize: 18) { showCast = true }
            NeonButton(title: tr("المقطع الدعائي", "Trailer"), systemImage: "film", kind: .dark, height: 54, fontSize: 18) { openTrailer() }
                .disabled(vm.details?.trailer == nil)
            Spacer()
        }
    }

    private var downloadTitle: String {
        switch vm.download?.status {
        case .some(.completed): return tr("تم التحميل", "Downloaded")
        case .some(.downloading), .some(.queued): return tr("جارٍ التحميل", "Downloading")
        default: return tr("تحميل", "Download")
        }
    }

    @ViewBuilder private var castSection: some View {
        let cast = vm.details?.castList ?? []
        if !cast.isEmpty || vm.details?.director != nil {
            SectionTitle(title: tr("فريق العمل", "Cast & Crew"))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 16) {
                    if let d = vm.details?.director {
                        CastCard(name: d, role: tr("المخرج", "Director"), isDirector: true)
                    }
                    ForEach(cast.prefix(8), id: \.self) { name in CastCard(name: name, role: nil) }
                    Button(action: { showCast = true }) {
                        VStack(spacing: 10) {
                            Image(systemName: "person.3").font(.system(size: 30))
                            Text(tr("عرض الكل", "View all")).font(.system(size: 20, weight: .heavy))
                            Image(systemName: Lang.isArabic ? "chevron.left" : "chevron.right").font(.system(size: 20, weight: .bold))
                        }
                        .foregroundColor(.white)
                        .frame(width: 170, height: 170)
                        .background(RoundedRectangle(cornerRadius: 18).fill(linear([Theme.purple.opacity(0.6), Color.black.opacity(0.7)])))
                        .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NeonAccent.magenta.stroke, lineWidth: 1.8))
                        .shadow(color: Theme.magenta.opacity(0.6), radius: 10)
                    }.buttonStyle(PlainButtonStyle())
                }
                .padding(.vertical, 8)
                .contentDirection()
            }
        }
    }

    private func play(_ resume: Double?) {
        let d = vm.details
        model.guarded(type: .movie, id: String(movie.id), categoryID: movie.categoryID, ageRating: d?.ageRating) {
            if let dl = vm.download, dl.status == .completed, let path = dl.filePath, FileManager.default.fileExists(atPath: path) {
                model.play(.file(URL(fileURLWithPath: path), title: movie.name, type: .movie, itemID: String(movie.id)))
            } else {
                model.play(.movie(movie, details: d, resumeAt: resume))
            }
        }
    }

    private func startDownload() {
        guard vm.download?.status != .completed else { model.open(.downloads); return }
        DownloadManager.shared.enqueueMovie(model: model, movie: m)
        vm.download = model.activePlaylist.flatMap { model.database.userData.findDownload($0.id, .movie, String(movie.id)) }
    }

    private func openTrailer() {
        guard let t = vm.details?.trailer else { return }
        let url = t.hasPrefix("http") ? URL(string: t) : URL(string: "https://www.youtube.com/watch?v=\(t)")
        if let u = url { NSWorkspace.shared.open(u) }
    }
}

/// Cast member card. Xtream provides names only, so a silhouette is shown instead of a photo.
struct CastCard: View {
    let name: String
    let role: String?
    var isDirector = false

    var body: some View {
        VStack(spacing: 6) {
            if isDirector {
                Image(systemName: "film").font(.system(size: 34)).foregroundColor(.white).padding(.top, 18)
                Text(role ?? "").font(.system(size: 18, weight: .bold)).foregroundColor(.white)
                Text(name).font(.system(size: 20, weight: .heavy)).foregroundColor(.white).lineLimit(2).multilineTextAlignment(.center)
            } else {
                ZStack {
                    linear([Color(hex: 0x2A2C3A), Color(hex: 0x12131C)])
                    Image(systemName: "person.fill").font(.system(size: 58)).foregroundColor(.white.opacity(0.35)).offset(y: 10)
                }
                .frame(height: 104)
                .clipShape(RoundedRectangle(cornerRadius: 14))
                Text(name).font(.system(size: 17, weight: .heavy)).foregroundColor(.white).lineLimit(1).minimumScaleFactor(0.7)
                if let r = role { Text(r).font(.system(size: 14, weight: .medium)).foregroundColor(Theme.textSecondary).lineLimit(1) }
            }
            Spacer(minLength: 0)
        }
        .padding(8)
        .frame(width: 210, height: 170)
        .background(RoundedRectangle(cornerRadius: 18).fill(isDirector ? linear([Theme.purple.opacity(0.55), Color(hex: 0x1A0C3A)]) : linear([Color(hex: 0x1A1C28), Color(hex: 0x0C0D14)])))
        .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(isDirector ? NeonAccent.purple.stroke : NeonAccent.mixed.stroke, lineWidth: 1.4))
        .shadow(color: (isDirector ? Theme.purple : Theme.blue).opacity(0.45), radius: 8)
    }
}

struct CastSheet: View {
    let names: [String]
    let director: String?
    let onClose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(tr("فريق العمل", "Cast & Crew")).font(.system(size: 22, weight: .heavy))
                Spacer()
                Button(action: onClose) { Image(systemName: "xmark.circle.fill").font(.system(size: 22)) }.buttonStyle(PlainButtonStyle())
            }
            if let d = director { Text(tr("المخرج: ", "Director: ") + d).font(.system(size: 16, weight: .bold)) }
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(names, id: \.self) { Text($0).font(.system(size: 15, weight: .semibold)) }
                    if names.isEmpty { Text(tr("لا تتوفر معلومات عن الممثلين", "No cast information available")).foregroundColor(Theme.textSecondary) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .foregroundColor(.white)
        .padding(24)
        .frame(width: 460, height: 460)
        .background(Theme.panel)
        .contentDirection()
    }
}
