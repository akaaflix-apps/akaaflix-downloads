import SwiftUI

struct RootView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        ZStack {
            AppBackground()
            if model.activePlaylist == nil && model.playlists.isEmpty {
                WelcomeView()
            } else {
                MainShell()
            }
            if let player = model.player {
                GeometryReader { geo in
                    PlayerScreen(controller: player)
                        .environment(\.contentSize, geo.size)
                }
                .transition(.opacity)
                .zIndex(10)
            }
            if let toast = model.toast {
                ToastView(toast: toast)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .padding(.bottom, 34)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .zIndex(20)
                    .allowsHitTesting(false)
            }
        }
        .fixedLTR()
        .id(model.settings.language)
        .sheet(item: $model.pinRequest) { request in
            PINEntrySheet(request: request)
                .environmentObject(model)
        }
    }
}

/// Top navigation + routed content.
struct MainShell: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        VStack(spacing: 0) {
            if !hidesTopBar {
                TopBar()
                    .padding(.horizontal, 24)
                    .padding(.top, 10)
                    .padding(.bottom, 8)
            }
            // A screen taller than the window must never push the top bar out of view.
            GeometryReader { geo in
                content
                    .frame(width: geo.size.width, height: geo.size.height)
                    .environment(\.contentSize, geo.size)
            }
        }
    }

    /// The series details design has its own header (back, logo | المسلسلات, icons).
    private var hidesTopBar: Bool {
        if case .seriesDetails = model.route { return true }
        return false
    }

    @ViewBuilder private var content: some View {
        switch model.route {
        case .home: HomeView()
        case .live: LiveTVView()
        case .movies: CatalogView(type: .movie)
        case .series: CatalogView(type: .series)
        case .movieDetails(let m): MovieDetailsView(movie: m).id("movie-\(m.id)")
        case .seriesDetails(let s): SeriesDetailsView(series: s).id("series-\(s.id)")
        case .settings(let section): SettingsShell(section: section)
        case .playlistForm(let id): SettingsShell(section: .playlists, formPlaylistID: id, showForm: true)
        case .search: SearchView()
        case .favorites: LibraryView(kind: .favorites)
        case .watchLater: LibraryView(kind: .watchLater)
        case .history: LibraryView(kind: .history)
        case .downloads: DownloadsView()
        case .notifications: NotificationsView()
        }
    }
}

/// Logo, the four main tabs and the round icons (search, notifications, favorites, settings).
struct TopBar: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        GeometryReader { geo in
            let compact = geo.size.width < 1350
            HStack(spacing: compact ? 10 : 16) {
                Button(action: { model.open(.home) }) { LogoView(height: compact ? 40 : 50) }
                    .buttonStyle(PlainButtonStyle())
                    .padding(.trailing, compact ? 4 : 18)
                PillTabButton(title: tr("الرئيسية", "Home"), systemImage: "house", isActive: model.route.tab == 0, compact: compact) { model.open(.home) }
                PillTabButton(title: tr("القنوات المباشرة", "Live TV"), systemImage: "tv", isActive: model.route.tab == 1, compact: compact) { model.open(.live) }
                PillTabButton(title: tr("الأفلام", "Movies"), systemImage: "film", isActive: model.route.tab == 2, compact: compact) { model.open(.movies) }
                PillTabButton(title: tr("المسلسلات", "Series"), systemImage: "square.stack.3d.up", isActive: model.route.tab == 3, compact: compact) { model.open(.series) }
                Spacer(minLength: 8)
                if model.isSyncing {
                    HStack(spacing: 8) {
                        ProgressView().scaleEffect(0.55)
                        Text(tr("جارٍ التحديث…", "Updating…")).font(.system(size: 13, weight: .semibold)).foregroundColor(Theme.textSecondary)
                    }
                }
                CircleIconButton(systemImage: "magnifyingglass", isActive: model.route == .search, size: compact ? 44 : 52, help: tr("بحث", "Search")) { model.open(.search) }
                CircleIconButton(systemImage: "bell", isActive: model.route == .notifications, showsDot: model.unreadNotifications > 0, size: compact ? 44 : 52, help: tr("الإشعارات", "Notifications")) { model.open(.notifications) }
                CircleIconButton(systemImage: "heart", isActive: model.route == .favorites, size: compact ? 44 : 52, help: tr("المفضلة", "Favorites")) { model.open(.favorites) }
                CircleIconButton(systemImage: "gearshape", isActive: isSettings, size: compact ? 44 : 52, help: tr("الإعدادات", "Settings")) { model.open(.settings(.general)) }
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
        .frame(height: 62)
    }

    private var isSettings: Bool {
        switch model.route {
        case .settings, .playlistForm: return true
        default: return false
        }
    }
}

/// Header used by screens like Live TV / Movies ("القنوات المباشرة · Live TV").
struct ScreenTitle: View {
    let systemImage: String
    let title: String
    let subtitle: String

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: systemImage)
                .font(.system(size: 46, weight: .regular))
                .foregroundColor(.white)
                .shadow(color: Theme.red, radius: 12)
                .overlay(Image(systemName: systemImage).font(.system(size: 46)).foregroundColor(Theme.redBright).opacity(0.35))
            VStack(alignment: .leading, spacing: 0) {
                Text(title).font(.system(size: 34, weight: .heavy)).foregroundColor(.white)
                Text(subtitle).font(.system(size: 20, weight: .medium)).foregroundColor(.white.opacity(0.9))
            }
        }
        .contentDirection()
    }
}

/// Back button used on details/library screens.
struct BackButton: View {
    @EnvironmentObject var model: AppModel
    var body: some View {
        Button(action: { model.back() }) {
            Image(systemName: Lang.isArabic ? "arrow.right" : "arrow.left")
                .font(.system(size: 22, weight: .bold))
                .foregroundColor(.white)
                .frame(width: 56, height: 50)
                .background(RoundedRectangle(cornerRadius: 14).fill(Theme.darkPill))
                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(Color.white.opacity(0.25), lineWidth: 1.2))
        }
        .buttonStyle(PlainButtonStyle())
        .keyboardShortcut(.cancelAction)
    }
}

/// Parental PIN prompt.
struct PINEntrySheet: View {
    @EnvironmentObject var model: AppModel
    let request: PINRequest
    @State private var pin = ""
    @State private var error = false

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "lock.shield").font(.system(size: 44)).foregroundColor(Theme.redBright)
            Text(request.reason).font(.system(size: 18, weight: .bold)).foregroundColor(.white).multilineTextAlignment(.center)
            SecureField(tr("الرمز السري", "PIN"), text: $pin, onCommit: verify)
                .textFieldStyle(PlainTextFieldStyle())
                .font(.system(size: 26, weight: .bold))
                .multilineTextAlignment(.center)
                .frame(width: 200, height: 50)
                .background(RoundedRectangle(cornerRadius: 12).fill(Color.black.opacity(0.4)))
                .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(error ? Theme.redBright : Color.white.opacity(0.2), lineWidth: 1.4))
            if error { Text(tr("الرمز غير صحيح", "Wrong PIN")).foregroundColor(Theme.redBright).font(.system(size: 13, weight: .semibold)) }
            HStack(spacing: 12) {
                NeonButton(title: tr("تأكيد", "Confirm"), systemImage: "checkmark", kind: .red, action: verify)
                NeonButton(title: tr("إلغاء", "Cancel"), systemImage: "xmark", kind: .dark) { model.pinRequest = nil }
            }
        }
        .padding(30)
        .frame(width: 420)
        .background(Theme.panel)
        .contentDirection()
    }

    private func verify() {
        if PinHasher.verify(pin, stored: model.settings.parentalPINHash) {
            model.parentalUnlocked = true
            let action = request.onSuccess
            model.pinRequest = nil
            DispatchQueue.main.async { action() }
        } else {
            error = true
            pin = ""
        }
    }
}
