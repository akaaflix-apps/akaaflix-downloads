import Foundation

/// Minimal HTTP transport so the client can be tested with a stub.
protocol HTTPTransport {
    func get(_ url: URL, timeout: TimeInterval) async throws -> (Data, Int)
}

final class URLSessionTransport: HTTPTransport {
    let session: URLSession

    init(session: URLSession? = nil) {
        if let session = session {
            self.session = session
        } else {
            let config = URLSessionConfiguration.default
            config.timeoutIntervalForRequest = 30
            config.timeoutIntervalForResource = 300
            config.httpAdditionalHeaders = ["User-Agent": AppInfo.userAgent, "Accept": "application/json, */*"]
            config.requestCachePolicy = .reloadIgnoringLocalCacheData
            config.httpMaximumConnectionsPerHost = 4
            self.session = URLSession(configuration: config)
        }
    }

    /// `URLSession.data(from:)` is macOS 12+; this wrapper keeps macOS 11 support.
    func get(_ url: URL, timeout: TimeInterval) async throws -> (Data, Int) {
        var request = URLRequest(url: url)
        request.timeoutInterval = timeout
        let box = TaskBox()
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<(Data, Int), Error>) in
                let task = session.dataTask(with: request) { data, response, error in
                    if let error = error {
                        continuation.resume(throwing: AkaaError.from(error))
                        return
                    }
                    let code = (response as? HTTPURLResponse)?.statusCode ?? 0
                    continuation.resume(returning: (data ?? Data(), code))
                }
                box.task = task
                task.resume()
            }
        } onCancel: {
            box.task?.cancel()
        }
    }

    private final class TaskBox: @unchecked Sendable {
        private let lock = NSLock()
        private var _task: URLSessionDataTask?
        var task: URLSessionDataTask? {
            get { lock.lock(); defer { lock.unlock() }; return _task }
            set { lock.lock(); _task = newValue; lock.unlock() }
        }
    }
}

enum AppInfo {
    static let name = "Akaa Flix"
    static let version = "4.0.7"
    static let displayVersion = "V4.0.7"
    static var userAgent: String { "AkaaFlix/\(version) (Macintosh; macOS)" }
}

/// Talks to the Xtream Codes `player_api.php` endpoint. The authenticated server response is the
/// only source of account information — nothing is invented locally.
final class XtreamClient {

    let transport: HTTPTransport
    var requestTimeout: TimeInterval = 25
    var catalogTimeout: TimeInterval = 120

    init(transport: HTTPTransport = URLSessionTransport()) {
        self.transport = transport
    }

    static func apiURL(_ creds: XtreamCredentials, action: String?, extra: [String: String] = [:]) -> URL? {
        guard var comps = URLComponents(string: creds.base + "player_api.php") else { return nil }
        var items = [URLQueryItem(name: "username", value: creds.username), URLQueryItem(name: "password", value: creds.password)]
        if let action = action { items.append(URLQueryItem(name: "action", value: action)) }
        for key in extra.keys.sorted() { items.append(URLQueryItem(name: key, value: extra[key])) }
        comps.queryItems = items
        // URLComponents leaves "+" unescaped, which PHP decodes as a space.
        comps.percentEncodedQuery = comps.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B")
        return comps.url
    }

    private func request(_ creds: XtreamCredentials, action: String?, extra: [String: String] = [:], timeout: TimeInterval? = nil) async throws -> Any {
        guard let url = XtreamClient.apiURL(creds, action: action, extra: extra) else { throw AkaaError.invalidURL }
        let (data, code) = try await transport.get(url, timeout: timeout ?? requestTimeout)
        switch code {
        case 200..<300: break
        case 401, 403: throw AkaaError.invalidCredentials
        case 404: throw AkaaError.notXtreamServer
        case 0: throw AkaaError.unreachable
        default: throw AkaaError.server(code)
        }
        if data.isEmpty {
            if action == nil { throw AkaaError.invalidCredentials }
            return [Any]()
        }
        return try LenientJSON.parse(data)
    }

    // MARK: Authentication

    /// Authenticates. When the user typed no scheme, HTTPS is tried first and HTTP as a fallback.
    func authenticate(serverInput: String, username: String, password: String) async throws -> (XtreamCredentials, AccountInfo) {
        guard let normalized = ServerURL.normalize(serverInput, defaultScheme: "https") else { throw AkaaError.invalidURL }
        let primary = XtreamCredentials(baseURL: normalized.baseURL, username: username, password: password)
        do {
            return (primary, try await authenticate(primary))
        } catch let error as AkaaError {
            guard normalized.schemeWasGuessed, [.unreachable, .tls, .timeout, .invalidResponse, .notXtreamServer].contains(error),
                  let httpBase = ServerURL.withScheme(normalized.baseURL, scheme: "http") else { throw error }
            let fallback = XtreamCredentials(baseURL: httpBase, username: username, password: password)
            return (fallback, try await authenticate(fallback))
        }
    }

    func authenticate(_ creds: XtreamCredentials) async throws -> AccountInfo {
        let json = try await request(creds, action: nil)
        return try XtreamParser.account(json, baseURL: creds.baseURL)
    }

    // MARK: Catalog

    func categories(_ creds: XtreamCredentials, type: ContentType) async throws -> [ContentCategory] {
        let action: String
        switch type {
        case .live: action = "get_live_categories"
        case .movie: action = "get_vod_categories"
        case .series, .episode: action = "get_series_categories"
        }
        return XtreamParser.categories(try await request(creds, action: action, timeout: catalogTimeout), type: type == .episode ? .series : type)
    }

    func liveStreams(_ creds: XtreamCredentials) async throws -> [Channel] {
        XtreamParser.channels(try await request(creds, action: "get_live_streams", timeout: catalogTimeout))
    }

    func vodStreams(_ creds: XtreamCredentials) async throws -> [Movie] {
        XtreamParser.movies(try await request(creds, action: "get_vod_streams", timeout: catalogTimeout))
    }

    func series(_ creds: XtreamCredentials) async throws -> [Series] {
        XtreamParser.series(try await request(creds, action: "get_series", timeout: catalogTimeout))
    }

    func movieDetails(_ creds: XtreamCredentials, movie: Movie) async throws -> MovieDetails {
        let json = try await request(creds, action: "get_vod_info", extra: ["vod_id": String(movie.id)])
        return XtreamParser.movieDetails(json, base: movie)
    }

    func seriesDetails(_ creds: XtreamCredentials, series: Series) async throws -> SeriesDetails {
        let json = try await request(creds, action: "get_series_info", extra: ["series_id": String(series.id)])
        if let arr = json as? [Any], arr.isEmpty { throw AkaaError.notFound }
        return XtreamParser.seriesDetails(json, base: series)
    }

    func shortEPG(_ creds: XtreamCredentials, streamID: Int, limit: Int = 6) async throws -> [EPGEntry] {
        let json = try await request(creds, action: "get_short_epg", extra: ["stream_id": String(streamID), "limit": String(limit)])
        return XtreamParser.shortEPG(json)
    }
}
