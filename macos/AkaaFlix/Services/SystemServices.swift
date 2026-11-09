import AppKit
import Foundation
import Security
import UserNotifications

/// Detects the running architecture so the app can report it and pick compatible playback options.
enum SystemInfo {

    /// Architecture this binary slice was compiled for (the slice macOS chose to run).
    static var binaryArchitecture: String {
        #if arch(arm64)
        return "arm64"
        #elseif arch(x86_64)
        return "x86_64"
        #else
        return "unknown"
        #endif
    }

    /// True when an x86_64 slice is being translated by Rosetta 2 on Apple Silicon.
    static var isTranslatedByRosetta: Bool {
        var value: Int32 = 0
        var size = MemoryLayout<Int32>.size
        guard sysctlbyname("sysctl.proc_translated", &value, &size, nil, 0) == 0 else { return false }
        return value == 1
    }

    /// Physical CPU family regardless of translation.
    static var hardwareArchitecture: String {
        if binaryArchitecture == "arm64" || isTranslatedByRosetta { return "arm64" }
        var value: Int32 = 0
        var size = MemoryLayout<Int32>.size
        if sysctlbyname("hw.optional.arm64", &value, &size, nil, 0) == 0, value == 1 { return "arm64" }
        return "x86_64"
    }

    static var isAppleSilicon: Bool { hardwareArchitecture == "arm64" }

    static func sysctlString(_ name: String) -> String? {
        var size = 0
        guard sysctlbyname(name, nil, &size, nil, 0) == 0, size > 0 else { return nil }
        var buffer = [CChar](repeating: 0, count: size)
        guard sysctlbyname(name, &buffer, &size, nil, 0) == 0 else { return nil }
        return String(cString: buffer)
    }

    static var cpuName: String { sysctlString("machdep.cpu.brand_string") ?? (isAppleSilicon ? "Apple Silicon" : "Intel") }
    static var modelIdentifier: String { sysctlString("hw.model") ?? "Mac" }

    static var macOSVersion: String {
        let v = ProcessInfo.processInfo.operatingSystemVersion
        return "\(v.majorVersion).\(v.minorVersion).\(v.patchVersion)"
    }

    static var macOSName: String {
        let major = ProcessInfo.processInfo.operatingSystemVersion.majorVersion
        switch major {
        case 11: return "Big Sur"
        case 12: return "Monterey"
        case 13: return "Ventura"
        case 14: return "Sonoma"
        case 15: return "Sequoia"
        case 26: return "Tahoe"
        default: return "macOS \(major)"
        }
    }

    static var physicalMemoryGB: Int { Int((Double(ProcessInfo.processInfo.physicalMemory) / 1_073_741_824).rounded()) }

    static var summary: String {
        "\(binaryArchitecture)\(isTranslatedByRosetta ? " (Rosetta)" : "") · macOS \(macOSVersion) \(macOSName) · \(modelIdentifier)"
    }
}

/// Playlist passwords in the user's login Keychain (works for Developer ID and ad-hoc builds).
final class KeychainSecretStore: SecretStore {
    private let service = "com.akaaflix.mac.playlists"

    private func query(_ id: Int64) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service,
         kSecAttrAccount as String: "playlist-\(id)"]
    }

    func password(for playlistID: Int64) -> String? {
        var q = query(playlistID)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func setPassword(_ password: String, for playlistID: Int64) throws {
        let data = Data(password.utf8)
        let q = query(playlistID)
        let status = SecItemUpdate(q as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecItemNotFound {
            var add = q
            add[kSecValueData as String] = data
            add[kSecAttrLabel as String] = "Akaa Flix playlist"
            let s = SecItemAdd(add as CFDictionary, nil)
            guard s == errSecSuccess else { throw AkaaError.storage("Keychain error \(s)") }
        } else if status != errSecSuccess {
            throw AkaaError.storage("Keychain error \(status)")
        }
    }

    func removePassword(for playlistID: Int64) {
        SecItemDelete(query(playlistID) as CFDictionary)
    }
}

/// Local (on-device) notifications for new content and upcoming expiry.
enum LocalNotifier {
    static func requestAuthorization() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    static func post(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        let request = UNNotificationRequest(identifier: UUID().uuidString, content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
    }
}

/// Runs blocking work (SQLite queries, sync writes) off the main thread.
func runInBackground<T>(_ work: @escaping () throws -> T) async throws -> T {
    try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<T, Error>) in
        DispatchQueue.global(qos: .userInitiated).async {
            do { continuation.resume(returning: try work()) } catch { continuation.resume(throwing: error) }
        }
    }
}

extension AkaaError {
    /// User-facing message in the active language.
    var message: String {
        switch self {
        case .invalidURL: return tr("رابط السيرفر غير صالح", "The server URL is not valid")
        case .invalidCredentials: return tr("اسم المستخدم أو كلمة المرور غير صحيحة", "Wrong username or password")
        case .accountExpired: return tr("انتهى الاشتراك. يرجى التجديد لدى مزود الخدمة", "The subscription has expired. Please renew with your provider")
        case .accountBanned: return tr("تم حظر هذا الحساب", "This account is banned")
        case .accountDisabled: return tr("هذا الحساب معطل", "This account is disabled")
        case .notXtreamServer: return tr("هذا الرابط ليس سيرفر Xtream Codes", "This URL is not an Xtream Codes server")
        case .server(let code): return tr("خطأ من السيرفر (\(code))", "Server error (\(code))")
        case .timeout: return tr("انتهت مهلة الاتصال بالسيرفر", "The server took too long to respond")
        case .offline: return tr("لا يوجد اتصال بالإنترنت", "No internet connection")
        case .unreachable: return tr("تعذر الوصول إلى السيرفر", "The server can't be reached")
        case .tls: return tr("فشل الاتصال الآمن (HTTPS)", "Secure connection (HTTPS) failed")
        case .invalidResponse: return tr("استجابة غير متوقعة من السيرفر", "Unexpected response from the server")
        case .notFound: return tr("المحتوى غير موجود", "Content not found")
        case .noActivePlaylist: return tr("لا توجد قائمة تشغيل مفعلة", "No active playlist")
        case .cancelled: return tr("تم الإلغاء", "Cancelled")
        case .storage(let m): return tr("خطأ في التخزين: ", "Storage error: ") + m
        case .playback(let m): return m
        case .unknown(let m): return m.isEmpty ? tr("حدث خطأ غير معروف", "Unknown error") : m
        }
    }
}
