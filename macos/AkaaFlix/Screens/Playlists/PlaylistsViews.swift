import AppKit
import SwiftUI

/// "إدارة القوائم" — multiple Xtream Codes servers, default selection, refresh/edit/duplicate/delete, ordering.
struct PlaylistsManagementView: View {
    @EnvironmentObject var model: AppModel
    @State private var selectedID: Int64?
    @State private var reorderMode = false
    @State private var dragging: Int64?
    @State private var confirmDelete: Playlist?
    @State private var refreshingID: Int64?

    var body: some View {
        VStack(spacing: 16) {
            banner
            actionBar
            ScrollView(.vertical, showsIndicators: true) {
                VStack(spacing: 14) {
                    ForEach(model.playlists) { p in
                        PlaylistRow(playlist: p, isSelected: (selectedID ?? model.activePlaylist?.id) == p.id, reorderMode: reorderMode,
                                    isRefreshing: refreshingID == p.id || (model.isSyncing && model.activePlaylist?.id == p.id),
                                    onSelect: { selectedID = p.id; if p.isEnabled { model.activate(p) } },
                                    onToggle: { model.setEnabled(p, $0) },
                                    onRefresh: { refresh(p) },
                                    onEdit: { model.open(.playlistForm(p.id)) },
                                    onDuplicate: { model.duplicatePlaylist(p) },
                                    onDelete: { confirmDelete = p },
                                    onMove: { move(p, by: $0) })
                            .onDrag {
                                dragging = p.id
                                return NSItemProvider(object: String(p.id) as NSString)
                            }
                            .onDrop(of: ["public.text"], delegate: PlaylistDropDelegate(target: p, dragging: $dragging, model: model))
                    }
                    if model.playlists.isEmpty {
                        EmptyStateView(systemImage: "square.stack.3d.up", title: tr("لا توجد قوائم بعد", "No playlists yet"),
                                       actionTitle: tr("إضافة قائمة جديدة", "Add new playlist")) { model.open(.playlistForm(nil)) }
                    }
                }
                .padding(.vertical, 6)
                .padding(.horizontal, 4)
            }
            footer
        }
        .alert(item: $confirmDelete) { p in
            Alert(title: Text(tr("حذف القائمة؟", "Delete playlist?")),
                  message: Text(tr("سيتم حذف \(p.name) وجميع بياناتها من هذا الجهاز.", "\(p.name) and all its data will be removed from this Mac.")),
                  primaryButton: .destructive(Text(tr("حذف", "Delete"))) { model.deletePlaylist(p) },
                  secondaryButton: .cancel(Text(tr("إلغاء", "Cancel"))))
        }
    }

    private var banner: some View {
        ZStack(alignment: .leading) {
            RoundedRectangle(cornerRadius: 24, style: .continuous).fill(linear([Color(hex: 0x0B1030), Color(hex: 0x060712)], .leading, .trailing))
            Image("PlaylistsBanner").resizable().aspectRatio(contentMode: .fill).frame(width: 660, height: 150).clipped()
                .mask(linear([.black, .black, .clear], .leading, .trailing))
            HStack {
                Spacer()
                VStack(alignment: .trailing, spacing: 8) {
                    HStack(spacing: 14) {
                        Text(tr("إدارة القوائم", "Playlist Management")).font(.system(size: 44, weight: .heavy))
                        Image(systemName: "square.stack.3d.up").font(.system(size: 40))
                    }
                    Text(tr("إدارة عدة سيرفرات IPTV والتبديل بينها وتحديد القائمة الافتراضية", "Manage several IPTV servers, switch between them and choose the default"))
                        .font(.system(size: 19, weight: .semibold)).foregroundColor(.white.opacity(0.92))
                }
                .foregroundColor(.white)
                .padding(.trailing, 34)
            }
        }
        .frame(height: 150)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(NeonAccent.blue.stroke, lineWidth: 1.6))
        .shadow(color: Theme.blue.opacity(0.4), radius: 12)
        .fixedLTR()
    }

    private var actionBar: some View {
        HStack(spacing: 14) {
            NeonButton(title: tr("تحديث الكل", "Refresh All"), systemImage: "arrow.triangle.2.circlepath", kind: .outlineBlue, height: 62, fontSize: 21, expand: true) { model.refreshAll() }
            NeonButton(title: tr("ترتيب القوائم", "Sort Playlists"), systemImage: "arrow.up.arrow.down", kind: reorderMode ? .blue : .outlineBlue, height: 62, fontSize: 21, expand: true) { reorderMode.toggle() }
            NeonButton(title: tr("نسخ احتياطي", "Backup"), systemImage: "icloud.and.arrow.up", kind: .outlineBlue, height: 62, fontSize: 21, expand: true) { BackupUI.export(model: model, includeCredentials: false) }
            NeonButton(title: tr("استعادة", "Restore"), systemImage: "icloud.and.arrow.down", kind: .outlineBlue, height: 62, fontSize: 21, expand: true) { BackupUI.restore(model: model, restoreSettings: false) }
            NeonButton(title: tr("إضافة قائمة جديدة", "Add New Playlist"), systemImage: "plus.circle", kind: .outlineRed, height: 62, fontSize: 21, expand: true) { model.open(.playlistForm(nil)) }
        }
        .fixedLTR()
    }

    private var footer: some View {
        HStack(spacing: 16) {
            HStack(spacing: 20) {
                Image(systemName: "info.circle").font(.system(size: 24))
                Text(tr("إجمالي القوائم: \(model.playlists.count)", "Playlists: \(model.playlists.count)")).font(.system(size: 18, weight: .bold)).lineLimit(1).minimumScaleFactor(0.5)
                HeaderDivider(height: 30)
                Image(systemName: "tv").font(.system(size: 22))
                Text(tr("إجمالي القنوات: \(model.playlists.reduce(0) { $0 + $1.liveCount })", "Channels: \(model.playlists.reduce(0) { $0 + $1.liveCount })")).font(.system(size: 18, weight: .bold)).lineLimit(1).minimumScaleFactor(0.5)
                HeaderDivider(height: 30)
                Image(systemName: "film").font(.system(size: 22))
                Text(tr("إجمالي الأفلام: \(model.playlists.reduce(0) { $0 + $1.movieCount })", "Movies: \(model.playlists.reduce(0) { $0 + $1.movieCount })")).font(.system(size: 18, weight: .bold)).lineLimit(1).minimumScaleFactor(0.5)
                HeaderDivider(height: 30)
                Image(systemName: "square.stack.3d.up").font(.system(size: 22))
                Text(tr("إجمالي المسلسلات: \(model.playlists.reduce(0) { $0 + $1.seriesCount })", "Series: \(model.playlists.reduce(0) { $0 + $1.seriesCount })")).font(.system(size: 18, weight: .bold)).lineLimit(1).minimumScaleFactor(0.5)
            }
            .foregroundColor(.white)
            .padding(.horizontal, 24).frame(height: 66)
            .frame(maxWidth: .infinity)
            .background(RoundedRectangle(cornerRadius: 22).fill(Theme.darkPill))
            .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(NeonAccent.mixed.stroke, lineWidth: 1.4))
            .fixedLTR()
            NeonButton(title: tr("تعيين كافتراضية", "Set as Default"), systemImage: "star", kind: .gold, height: 66, fontSize: 20) {
                if let id = selectedID, let p = model.playlists.first(where: { $0.id == id }) {
                    model.setDefault(p)
                    model.showToast(tr("تم تعيين \(p.name) كقائمة افتراضية", "\(p.name) is now the default"), .success)
                }
            }
        }
        .fixedLTR()
    }

    private func refresh(_ p: Playlist) {
        refreshingID = p.id
        model.sync(p)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1) { refreshingID = nil }
    }

    private func move(_ p: Playlist, by delta: Int) {
        var ids = model.playlists.map { $0.id }
        guard let i = ids.firstIndex(of: p.id) else { return }
        let j = i + delta
        guard ids.indices.contains(j) else { return }
        ids.swapAt(i, j)
        model.reorderPlaylists(ids)
    }
}

struct PlaylistDropDelegate: DropDelegate {
    let target: Playlist
    @Binding var dragging: Int64?
    let model: AppModel

    func performDrop(info: DropInfo) -> Bool {
        dragging = nil
        return true
    }

    func dropEntered(info: DropInfo) {
        guard let d = dragging, d != target.id else { return }
        var ids = model.playlists.map { $0.id }
        guard let from = ids.firstIndex(of: d), let to = ids.firstIndex(of: target.id) else { return }
        ids.remove(at: from)
        ids.insert(d, at: to)
        model.reorderPlaylists(ids)
    }
}

/// One playlist row: logo, name, Xtream Codes badge, status, default radio, counts, enable toggle, actions.
struct PlaylistRow: View {
    @Environment(\.contentSize) private var size
    let playlist: Playlist
    let isSelected: Bool
    let reorderMode: Bool
    let isRefreshing: Bool
    let onSelect: () -> Void
    let onToggle: (Bool) -> Void
    let onRefresh: () -> Void
    let onEdit: () -> Void
    let onDuplicate: () -> Void
    let onDelete: () -> Void
    let onMove: (Int) -> Void

    var body: some View {
        HStack(spacing: 14) {
            if reorderMode {
                VStack(spacing: 6) {
                    smallIcon("chevron.up", .blue) { onMove(-1) }
                    smallIcon("chevron.down", .blue) { onMove(1) }
                }
            } else if size.width >= 1300 {
                Image(systemName: "circle.grid.2x3.fill").font(.system(size: 22)).foregroundColor(.white.opacity(0.8))
            }
            actionIcon("trash", .red, onDelete)
            actionIcon("doc.on.doc", .blue, onDuplicate)
            actionIcon("pencil", .blue, onEdit)
            actionIcon(isRefreshing ? "hourglass" : "arrow.triangle.2.circlepath", .blue, onRefresh)
            VStack(spacing: 4) {
                NeonToggle(isOn: Binding(get: { playlist.isEnabled }, set: onToggle), color: Theme.green, width: 66)
                Text(playlist.isEnabled ? tr("مفعل", "Enabled") : tr("غير مفعل", "Disabled")).font(.system(size: 15, weight: .bold)).foregroundColor(.white)
            }
            .frame(width: 92)
            divider
            countColumn("square.stack.3d.up", tr("المسلسلات", "Series"), playlist.seriesCount)
            divider
            countColumn("film", tr("الأفلام", "Movies"), playlist.movieCount)
            divider
            countColumn("tv", tr("القنوات", "Channels"), playlist.liveCount)
            divider
            Button(action: onSelect) {
                Image(systemName: playlist.isDefault ? "largecircle.fill.circle" : "circle")
                    .font(.system(size: 34))
                    .foregroundColor(playlist.isDefault ? Theme.green : .white)
                    .shadow(color: playlist.isDefault ? Theme.green : .clear, radius: 6)
            }.buttonStyle(PlainButtonStyle())
            Spacer(minLength: 6)
            VStack(alignment: .trailing, spacing: 8) {
                HStack(spacing: 10) {
                    if playlist.isDefault {
                        HStack(spacing: 4) {
                            Image(systemName: "star.fill").font(.system(size: 12))
                            Text(tr("الافتراضية", "Default")).font(.system(size: 13, weight: .heavy))
                        }
                        .foregroundColor(Theme.gold)
                        .padding(.horizontal, 10).padding(.vertical, 4)
                        .background(Capsule().fill(Theme.purple.opacity(0.5)))
                        .overlay(Capsule().strokeBorder(Color(hex: 0xD04BFF), lineWidth: 1))
                        .fixedSize()
                    }
                    Text(playlist.name).font(.system(size: 28, weight: .heavy)).foregroundColor(.white).lineLimit(1).minimumScaleFactor(0.6)
                        .layoutPriority(2)
                }
                HStack(spacing: 10) {
                    statusIcon
                    Text("Xtream Codes").font(.system(size: 16, weight: .bold)).foregroundColor(.white)
                        .padding(.horizontal, 12).padding(.vertical, 4)
                        .background(Capsule().fill(Theme.blue.opacity(0.35)))
                        .overlay(Capsule().strokeBorder(Theme.blueBright, lineWidth: 1))
                }
            }
            Image("AppIconArt")
                .resizable().aspectRatio(contentMode: .fit)
                .frame(width: size.width < 1300 ? 72 : 96, height: size.width < 1300 ? 72 : 96)
                .clipShape(RoundedRectangle(cornerRadius: 18))
        }
        .padding(.horizontal, 16)
        .frame(height: 118)
        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(linear([Color(hex: 0x15172A), Color(hex: 0x0A0B14)])))
        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(isSelected ? AnyShapeStyleBox.red : Theme.neonStroke, lineWidth: isSelected ? 2.6 : 1.6))
        .shadow(color: isSelected ? Theme.red.opacity(0.75) : Color(hex: 0x6A1AAE).opacity(0.45), radius: isSelected ? 14 : 9)
        .contentShape(Rectangle())
        .onTapGesture(perform: onSelect)
        .fixedLTR()
    }

    @ViewBuilder private var statusIcon: some View {
        switch playlist.status {
        case .connected: Image(systemName: "checkmark.circle").font(.system(size: 26)).foregroundColor(Theme.green)
        case .failed, .expired: Image(systemName: "xmark.circle").font(.system(size: 26)).foregroundColor(.white)
        case .unknown: Image(systemName: "questionmark.circle").font(.system(size: 26)).foregroundColor(Theme.textTertiary)
        }
    }

    private var divider: some View { Rectangle().fill(Color.white.opacity(0.18)).frame(width: 1.2, height: 64) }

    private func countColumn(_ icon: String, _ label: String, _ count: Int) -> some View {
        VStack(spacing: 4) {
            Image(systemName: icon).font(.system(size: 22))
            Text(label).font(.system(size: 15, weight: .bold))
            Text("\(count)").font(.system(size: 22, weight: .heavy)).monoDigits()
        }
        .foregroundColor(.white)
        .frame(minWidth: 62, maxWidth: 112)
    }

    private func actionIcon(_ icon: String, _ accent: NeonAccent, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 22, weight: .semibold)).foregroundColor(.white)
                .frame(width: size.width < 1500 ? 44 : 54, height: size.width < 1500 ? 44 : 54)
                .background(RoundedRectangle(cornerRadius: 14).fill(accent == .red ? Theme.redGradient : linear([Theme.blue.opacity(0.75), Color(hex: 0x0D2A7A)])))
                .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(accent.stroke, lineWidth: 1.4))
                .shadow(color: accent.glow.opacity(0.6), radius: 6)
        }.buttonStyle(PlainButtonStyle())
    }

    private func smallIcon(_ icon: String, _ accent: NeonAccent, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 14, weight: .bold)).foregroundColor(.white)
                .frame(width: 34, height: 26)
                .background(RoundedRectangle(cornerRadius: 7).fill(Theme.blue.opacity(0.6)))
        }.buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Add / Edit playlist

struct PlaylistFormView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.contentSize) private var size
    let playlistID: Int64?

    /// Width of the illustration / status column (approved 540–560 pt; narrower on small windows).
    private var sideWidth: CGFloat {
        let available = size.width - (model.playlists.isEmpty ? 120 : Responsive.settingsNavWidth(size.width) + 90)
        return min(560, max(300, available * 0.42))
    }

    @State private var name = ""
    @State private var username = ""
    @State private var password = ""
    @State private var url = ""
    @State private var busy = false
    @State private var testing = false
    @State private var testResult: String?
    @State private var testOK = false
    @State private var showQR = false
    @State private var confirmDelete = false
    @State private var loaded = false

    private var editing: Playlist? { playlistID.flatMap { id in model.playlists.first(where: { $0.id == id }) } }

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            formPanel.padding(.bottom, 12)
        }
        .onAppear(perform: loadFields)
        .sheet(isPresented: $showQR) {
            QRImportSheet { input in
                name = input.name; username = input.username; password = input.password; url = input.url
                showQR = false
            } onCancel: { showQR = false }
        }
        .alert(isPresented: $confirmDelete) {
            Alert(title: Text(tr("حذف القائمة؟", "Delete playlist?")),
                  primaryButton: .destructive(Text(tr("حذف", "Delete"))) {
                      if let p = editing { model.deletePlaylist(p); model.open(.settings(.playlists)) }
                  },
                  secondaryButton: .cancel(Text(tr("إلغاء", "Cancel"))))
        }
    }

    private var formPanel: some View {
        NeonPanel(accent: .mixed, corner: 26, padding: 22) {
            VStack(spacing: 18) {
                header
                Rectangle().fill(Color.white.opacity(0.1)).frame(height: 1)
                HStack(alignment: .top, spacing: 24) {
                    formCard
                    if let p = editing { statusCard(p) } else { addIllustration }
                }
                .fixedLTR()
                if let r = testResult {
                    HStack(spacing: 10) {
                        Image(systemName: testOK ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                        Text(r).font(.system(size: 15, weight: .bold)).lineLimit(2)
                        Spacer()
                    }
                    .foregroundColor(testOK ? Theme.green : Theme.redBright)
                    .contentDirection()
                }
                buttons
            }
        }
    }

    private func loadFields() {
        guard !loaded else { return }
        loaded = true
        if let p = editing {
            name = p.name
            username = p.username
            url = p.serverURL
            password = model.secrets.password(for: p.id) ?? ""
        }
    }

    private var header: some View {
        ZStack {
            if editing != nil {
                Image("PlaylistsBanner").resizable().aspectRatio(contentMode: .fill).frame(height: 110).clipped().opacity(0.85)
                    .mask(linear([.black, .clear], .leading, .trailing))
            }
            HStack(spacing: 16) {
                Button(action: { if model.playlists.isEmpty { return }; model.open(.settings(.playlists)) }) {
                    Image(systemName: "arrow.left").font(.system(size: 30, weight: .bold)).foregroundColor(.white)
                        .frame(width: 92, height: 64)
                        .background(RoundedRectangle(cornerRadius: 16).fill(Theme.darkPill))
                        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.white.opacity(0.25), lineWidth: 1.3))
                }
                .buttonStyle(PlainButtonStyle())
                .opacity(model.playlists.isEmpty ? 0.3 : 1)
                Spacer()
                VStack(alignment: .trailing, spacing: 6) {
                    HStack(spacing: 14) {
                        Text(editing == nil ? tr("إضافة قائمة جديدة", "Add New Playlist") : tr("تعديل القائمة", "Edit Playlist"))
                            .font(.system(size: 44, weight: .heavy))
                        Image(systemName: editing == nil ? "plus.circle" : "pencil").font(.system(size: 44, weight: .light))
                    }
                    if editing != nil {
                        Text(tr("تحديث بيانات السيرفر وإعدادات القائمة", "Update the server details and playlist settings"))
                            .font(.system(size: 19, weight: .semibold)).foregroundColor(.white.opacity(0.9))
                    }
                }
                .foregroundColor(.white)
            }
        }
        .frame(height: 110)
        .fixedLTR()
    }

    private var formCard: some View {
        VStack(spacing: 16) {
            HStack(spacing: 12) {
                Image(systemName: "externaldrive.connected.to.line.below").font(.system(size: 34)).foregroundColor(Theme.blueBright)
                    .frame(width: 70, height: 56)
                    .background(RoundedRectangle(cornerRadius: 10).fill(Theme.blue.opacity(0.2)))
                HStack(spacing: 8) {
                    Text("Xtream Codes").font(.system(size: 26, weight: .heavy)).lineLimit(1).minimumScaleFactor(0.7)
                    Text("(Username / Password)").font(.system(size: 20, weight: .semibold)).lineLimit(1).minimumScaleFactor(0.6)
                }
                .foregroundColor(.white)
                .padding(.horizontal, 18).frame(height: 54)
                .background(Capsule().fill(Theme.blue.opacity(0.18)))
                .overlay(Capsule().strokeBorder(Theme.blueBright.opacity(0.7), lineWidth: 1.3))
                Spacer()
                Button(action: { showQR = true }) {
                    Image(systemName: "qrcode.viewfinder").font(.system(size: 26)).foregroundColor(.white)
                        .frame(width: 50, height: 50)
                        .background(Circle().fill(Theme.darkPill))
                        .overlay(Circle().strokeBorder(Color.white.opacity(0.25), lineWidth: 1))
                }
                .buttonStyle(PlainButtonStyle())
                .help(tr("استيراد من رمز QR", "Import from QR code"))
            }
            FormFieldRow(systemImage: "square.stack.3d.up", label: tr("اسم القائمة", "Playlist name"), placeholder: tr("مثال: Nova IPTV", "e.g. Nova IPTV"), text: $name)
            FormFieldRow(systemImage: "person", label: tr("اسم المستخدم", "Username"), placeholder: tr("أدخل اسم المستخدم", "Enter the username"), text: $username)
            FormFieldRow(systemImage: "lock", label: tr("كلمة المرور", "Password"), placeholder: tr("أدخل كلمة المرور", "Enter the password"), text: $password, secure: true)
            FormFieldRow(systemImage: "link", label: tr("رابط السيرفر (URL)", "Server URL"), placeholder: tr("أدخل الرابط http:// أو https://", "Enter http:// or https:// URL"), text: $url)
        }
        .padding(20)
        .background(RoundedRectangle(cornerRadius: 22).fill(Color(hex: 0x0A0D1C).opacity(0.85)))
        .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(NeonAccent.blue.stroke, lineWidth: 1.3))
        .frame(maxWidth: .infinity)
    }

    private var addIllustration: some View {
        VStack(spacing: 18) {
            Image("AddPlaylistArt").resizable().aspectRatio(contentMode: .fit).frame(maxWidth: 520, maxHeight: 380)
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .trailing, spacing: 4) {
                    Text(tr("قم بإضافة بيانات اشتراكك بنظام", "Add your subscription details using")).font(.system(size: 18, weight: .bold))
                    Text("Xtream Codes").font(.system(size: 22, weight: .heavy))
                    Text(tr("لتشغيل القنوات والأفلام والمسلسلات.", "to play channels, movies and series.")).font(.system(size: 18, weight: .bold))
                }
                Image(systemName: "info.circle").font(.system(size: 30)).foregroundColor(Theme.blueBright)
            }
            .foregroundColor(.white)
            .padding(20)
            .frame(maxWidth: 520)
            .background(RoundedRectangle(cornerRadius: 20).fill(Theme.blue.opacity(0.12)))
            .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(NeonAccent.blue.stroke, lineWidth: 1.4))
        }
        .frame(width: sideWidth)
    }

    private func statusCard(_ p: Playlist) -> some View {
        VStack(spacing: 14) {
            HStack(spacing: 16) {
                Image("AppIconArt").resizable().aspectRatio(contentMode: .fit).frame(width: sideWidth < 480 ? 120 : 190, height: sideWidth < 480 ? 110 : 160)
                    .clipShape(RoundedRectangle(cornerRadius: 22))
                VStack(spacing: 10) {
                    Text(p.name).font(.system(size: 32, weight: .heavy)).foregroundColor(.white).lineLimit(1)
                    Text("Xtream Codes").font(.system(size: 19, weight: .bold)).foregroundColor(.white)
                        .padding(.horizontal, 18).padding(.vertical, 5)
                        .background(Capsule().fill(Theme.blue.opacity(0.3)))
                        .overlay(Capsule().strokeBorder(Theme.blueBright, lineWidth: 1))
                    HStack(spacing: 10) {
                        Text(statusLabel(p)).font(.system(size: 24, weight: .heavy))
                        Image(systemName: p.status == .connected ? "checkmark.circle" : "xmark.circle").font(.system(size: 26))
                    }
                    .foregroundColor(p.status == .connected ? Theme.green : Theme.redBright)
                    .padding(.horizontal, 24).frame(height: 50)
                    .background(Capsule().fill((p.status == .connected ? Theme.greenDeep : Theme.redDeep).opacity(0.5)))
                    .overlay(Capsule().strokeBorder(p.status == .connected ? Theme.green : Theme.redBright, lineWidth: 1.5))
                }
            }
            HStack(spacing: 0) {
                stat("square.stack.3d.up", tr("المسلسلات", "Series"), p.seriesCount)
                Rectangle().fill(Color.white.opacity(0.2)).frame(width: 1.2, height: 80)
                stat("film", tr("الأفلام", "Movies"), p.movieCount)
                Rectangle().fill(Color.white.opacity(0.2)).frame(width: 1.2, height: 80)
                stat("tv", tr("القنوات", "Channels"), p.liveCount)
            }
            .padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: 18).fill(Color.black.opacity(0.35)))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NeonAccent.blue.stroke, lineWidth: 1))
            VStack(alignment: .trailing, spacing: 10) {
                infoLine("calendar", tr("آخر تحديث: ", "Last update: ") + (p.lastSync.map(formatDate) ?? "—"))
                infoLine("calendar.badge.plus", tr("تاريخ الإضافة: ", "Added: ") + formatDay(p.createdAt))
            }
            .padding(16)
            .frame(maxWidth: .infinity)
            .background(RoundedRectangle(cornerRadius: 18).fill(Color.black.opacity(0.35)))
            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(NeonAccent.blue.stroke, lineWidth: 1))
        }
        .padding(18)
        .frame(width: sideWidth)
        .background(RoundedRectangle(cornerRadius: 22).fill(Color(hex: 0x0D0A18).opacity(0.85)))
        .overlay(RoundedRectangle(cornerRadius: 22).strokeBorder(NeonAccent.red.stroke, lineWidth: 1.6))
        .shadow(color: Theme.red.opacity(0.45), radius: 12)
    }

    private func statusLabel(_ p: Playlist) -> String {
        switch p.status {
        case .connected: return tr("متصل", "Connected")
        case .expired: return tr("منتهي", "Expired")
        case .failed: return tr("غير متصل", "Not connected")
        case .unknown: return tr("غير معروف", "Unknown")
        }
    }

    private func stat(_ icon: String, _ label: String, _ value: Int) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon).font(.system(size: 30))
            Text(label).font(.system(size: 18, weight: .bold))
            Text("\(value)").font(.system(size: 30, weight: .heavy)).monoDigits()
        }
        .foregroundColor(.white)
        .frame(maxWidth: .infinity)
    }

    private func infoLine(_ icon: String, _ text: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "info.circle").font(.system(size: 22)).foregroundColor(Theme.blueBright).opacity(icon == "calendar" ? 1 : 0)
            Spacer()
            Text(text).font(.system(size: 18, weight: .bold)).foregroundColor(.white)
            Image(systemName: icon).font(.system(size: 22)).foregroundColor(.white)
        }
    }

    private func formatDate(_ d: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: Lang.isArabic ? "ar" : "en_US")
        f.dateFormat = "HH:mm - dd MMMM yyyy"
        return f.string(from: d)
    }

    private func formatDay(_ d: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: Lang.isArabic ? "ar" : "en_US")
        f.dateFormat = "dd MMMM yyyy"
        return f.string(from: d)
    }

    private var input: PlaylistInput { PlaylistInput(name: name, username: username, password: password, url: url) }

    private func validate() -> Bool {
        let issues = PlaylistValidator.validate(input, existing: model.playlists, editingID: playlistID, passwordOptional: editing != nil)
        guard let first = issues.first else { return true }
        let message: String
        switch first {
        case .nameMissing: message = tr("أدخل اسم القائمة", "Enter a playlist name")
        case .usernameMissing: message = tr("أدخل اسم المستخدم", "Enter the username")
        case .passwordMissing: message = tr("أدخل كلمة المرور", "Enter the password")
        case .urlMissing: message = tr("أدخل رابط السيرفر", "Enter the server URL")
        case .urlInvalid: message = tr("رابط السيرفر غير صالح", "The server URL is not valid")
        case .duplicate: message = tr("هذه القائمة مضافة مسبقاً", "This playlist already exists")
        }
        testOK = false
        testResult = message
        return false
    }

    @ViewBuilder private var buttons: some View {
        if let p = editing {
            HStack(spacing: 14) {
                NeonButton(title: tr("حذف القائمة", "Delete"), systemImage: "trash", kind: .outlineRed, height: 64, fontSize: 21, expand: true) { confirmDelete = true }
                NeonButton(title: tr("تحديث المحتوى", "Refresh Content"), systemImage: "arrow.triangle.2.circlepath", kind: .outlineBlue, height: 64, fontSize: 21, expand: true) { model.sync(p) }
                NeonButton(title: tr("اختبار الاتصال", "Test Connection"), systemImage: "chart.bar.fill", kind: .outlineGreen, height: 64, fontSize: 21, expand: true, isLoading: testing) { test() }
                NeonButton(title: tr("إلغاء", "Cancel"), systemImage: "xmark.circle", kind: .outlineBlue, height: 64, fontSize: 21, expand: true) { model.open(.settings(.playlists)) }
                NeonButton(title: tr("حفظ التغييرات", "Save Changes"), systemImage: "checkmark.circle", kind: .red, height: 64, fontSize: 21, expand: true, isLoading: busy) { save(p) }
            }
            .fixedLTR()
        } else {
            HStack(spacing: 18) {
                NeonButton(title: tr("اختبار الاتصال", "Test Connection"), systemImage: "arrow.triangle.2.circlepath", kind: .outlineBlue, height: 70, fontSize: 24, expand: true, isLoading: testing) { test() }
                NeonButton(title: tr("إلغاء", "Cancel"), systemImage: "xmark.circle", kind: .outlineRed, height: 70, fontSize: 24, expand: true) {
                    if model.playlists.isEmpty { clear() } else { model.open(.settings(.playlists)) }
                }
                NeonButton(title: tr("إضافة القائمة", "Add Playlist"), systemImage: "plus.circle", kind: .red, height: 70, fontSize: 24, expand: true, isLoading: busy) { add() }
            }
            .fixedLTR()
        }
    }

    private func clear() { name = ""; username = ""; password = ""; url = ""; testResult = nil }

    private func test() {
        guard validate() else { return }
        testing = true
        testResult = nil
        var i = input
        if i.password.isEmpty, let p = editing { i.password = model.secrets.password(for: p.id) ?? "" }
        let probe = i
        Task {
            do {
                let (_, account) = try await model.testConnection(probe)
                await MainActor.run {
                    testing = false
                    testOK = true
                    let exp = account.expiresAt.map { TimeFormat.dayMonthYear.string(from: $0) } ?? tr("غير محدود", "unlimited")
                    testResult = tr("تم الاتصال بنجاح · ينتهي في \(exp)", "Connected successfully · expires \(exp)")
                }
            } catch {
                await MainActor.run {
                    testing = false
                    testOK = false
                    testResult = AkaaError.from(error).message
                }
            }
        }
    }

    private func add() {
        guard validate() else { return }
        busy = true
        let i = input
        Task {
            do {
                let p = try await model.addPlaylist(i)
                await MainActor.run {
                    busy = false
                    model.activate(p)
                    model.showToast(tr("تمت إضافة القائمة", "Playlist added"), .success)
                    model.open(.home)
                }
            } catch {
                await MainActor.run {
                    busy = false
                    testOK = false
                    testResult = AkaaError.from(error).message
                }
            }
        }
    }

    private func save(_ p: Playlist) {
        guard validate() else { return }
        busy = true
        let i = input
        Task {
            do {
                try await model.updatePlaylist(p, input: i)
                await MainActor.run {
                    busy = false
                    model.showToast(tr("تم حفظ التغييرات", "Changes saved"), .success)
                    model.open(.settings(.playlists))
                }
            } catch {
                await MainActor.run {
                    busy = false
                    testOK = false
                    testResult = AkaaError.from(error).message
                }
            }
        }
    }
}

/// First-run screen: the Add Playlist form inside the settings layout.
struct WelcomeView: View {
    var body: some View {
        VStack(spacing: 0) {
            HStack {
                LogoView(height: 50)
                Spacer()
            }
            .padding(.horizontal, 28).padding(.vertical, 14)
            PlaylistFormView(playlistID: nil)
                .padding(.horizontal, 60)
                .padding(.bottom, 30)
        }
    }
}
