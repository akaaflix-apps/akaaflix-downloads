import AppKit
import SwiftUI

@main
struct AkaaFlixApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            AppRootContainer()
                .frame(minWidth: 1100, minHeight: 680)
        }
        .windowToolbarStyle(UnifiedCompactWindowToolbarStyle())
        .commands {
            CommandGroup(replacing: .newItem) {}
            CommandGroup(replacing: .appInfo) {
                Button(tr("حول Akaa Flix", "About Akaa Flix")) { AppDelegate.shared?.model?.open(.settings(.about)) }
            }
            CommandGroup(replacing: .appSettings) {
                Button(tr("الإعدادات…", "Settings…")) { AppDelegate.shared?.model?.open(.settings(.general)) }
                    .keyboardShortcut(",", modifiers: .command)
            }
            CommandMenu(tr("التشغيل", "Playback")) {
                Button(tr("تشغيل / إيقاف مؤقت", "Play / Pause")) { AppDelegate.shared?.model?.player?.togglePlay() }
                    .keyboardShortcut("p", modifiers: .command)
                Button(tr("إغلاق المشغل", "Close Player")) { AppDelegate.shared?.model?.closePlayer() }
                    .keyboardShortcut("w", modifiers: [.command, .shift])
                Divider()
                Button(tr("تحديث المحتوى", "Refresh Content")) { AppDelegate.shared?.model?.sync() }
                    .keyboardShortcut("r", modifiers: .command)
            }
        }
    }
}

/// Creates the model once the app delegate has decided where data lives (normal or self-test).
struct AppRootContainer: View {
    @ObservedObject private var holder = ModelHolder.shared

    var body: some View {
        Group {
            if let model = holder.model {
                RootView().environmentObject(model)
            } else if let error = holder.error {
                VStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle").font(.system(size: 40)).foregroundColor(Theme.redBright)
                    Text(error).foregroundColor(.white).multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Theme.background)
            } else {
                SplashView()
            }
        }
        .preferredColorScheme(.dark)
    }
}

final class ModelHolder: ObservableObject {
    static let shared = ModelHolder()
    @Published var model: AppModel?
    @Published var error: String?
}

struct SplashView: View {
    var body: some View {
        ZStack {
            AppBackground()
            VStack(spacing: 22) {
                LogoView(height: 110)
                ProgressView().scaleEffect(0.9)
            }
        }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    static weak var shared: AppDelegate?
    var model: AppModel? { ModelHolder.shared.model }
    private var selfTest: SelfTestRunner?
    private var windowObserver: NSObjectProtocol?

    override init() {
        super.init()
        AppDelegate.shared = self
    }

    func applicationDidFinishLaunching(_ notification: Notification) {
        let args = SelfTestArguments(arguments: CommandLine.arguments)
        do {
            if let test = args {
                let model = try AppModel(paths: AppPaths(root: test.dataDirectory), secrets: InMemorySecretStore())
                ModelHolder.shared.model = model
                let runner = SelfTestRunner(arguments: test, model: model)
                selfTest = runner
                DispatchQueue.main.asyncAfter(deadline: .now() + 1) { runner.run() }
            } else {
                let model = try AppModel(paths: AppPaths.standard(), secrets: KeychainSecretStore())
                ModelHolder.shared.model = model
                LocalNotifier.requestAuthorization()
                DispatchQueue.main.asyncAfter(deadline: .now() + 2) { model.autoRefreshIfNeeded() }
            }
        } catch {
            ModelHolder.shared.error = AkaaError.from(error).message
        }
        DispatchQueue.main.async { self.configureWindows() }
        windowObserver = NotificationCenter.default.addObserver(forName: NSWindow.didBecomeKeyNotification, object: nil, queue: .main) { [weak self] _ in
            self?.configureWindows()
        }
    }

    /// Dark title bar with the app name, as in the approved window design.
    func configureWindows() {
        for window in NSApp.windows where window.styleMask.contains(.titled) && !(window is NSPanel) {
            guard window.identifier?.rawValue != "akaa.configured" else { continue }
            window.identifier = NSUserInterfaceItemIdentifier("akaa.configured")
            window.title = AppInfo.name
            window.titlebarAppearsTransparent = true
            window.backgroundColor = NSColor(red: 0.02, green: 0.024, blue: 0.043, alpha: 1)
            window.appearance = NSAppearance(named: .darkAqua)
            window.minSize = NSSize(width: 1100, height: 680)
            window.collectionBehavior.insert(.fullScreenPrimary)
            window.tabbingMode = .disallowed
            if let screen = window.screen ?? NSScreen.main {
                let visible = screen.visibleFrame
                let width = min(1600, visible.width * 0.94)
                let height = min(940, visible.height * 0.94)
                window.setFrame(NSRect(x: visible.midX - width / 2, y: visible.midY - height / 2, width: width, height: height), display: true)
            }
        }
    }

    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }

    func applicationWillTerminate(_ notification: Notification) {
        // Resource cleanup: persist progress and release the playback engine and database.
        model?.player?.stop()
        model?.database.db.close()
    }
}
