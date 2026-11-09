import AppKit
import Foundation

/// Downloads movies/episodes (when the provider allows it) to ~/Movies/Akaa Flix for offline playback.
final class DownloadManager: NSObject, ObservableObject, URLSessionDownloadDelegate {
    static let shared = DownloadManager()

    @Published private(set) var active: [Int64: Double] = [:]
    private var session: URLSession!
    private var tasks: [Int: Int64] = [:]           // task id -> download record id
    private var destinations: [Int64: URL] = [:]
    private var playlistIDs: [Int64: Int64] = [:]
    private weak var model: AppModel?
    private let lock = NSLock()

    override init() {
        super.init()
        let config = URLSessionConfiguration.default
        config.httpAdditionalHeaders = ["User-Agent": AppInfo.userAgent]
        config.timeoutIntervalForResource = 60 * 60 * 12
        session = URLSession(configuration: config, delegate: self, delegateQueue: OperationQueue.main)
    }

    private func fileName(_ title: String, ext: String) -> String {
        let cleaned = title.components(separatedBy: CharacterSet(charactersIn: "/\\:?%*|\"<>")).joined(separator: " ")
            .trimmingCharacters(in: .whitespaces)
        return "\(cleaned.isEmpty ? "Akaa Flix" : cleaned).\(ext)"
    }

    func enqueueMovie(model: AppModel, movie: Movie) {
        guard let playlist = model.activePlaylist, let creds = model.credentials(for: playlist),
              let candidate = StreamURLBuilder.movie(creds, streamID: movie.id, containerExtension: movie.containerExtension) else { return }
        let ext = StreamURLBuilder.sanitizeExtension(movie.containerExtension) ?? "mp4"
        start(model: model, playlistID: playlist.id, type: .movie, itemID: String(movie.id), title: ContentText.displayTitles(movie.name).primary,
              image: movie.poster, ext: ext, url: candidate.url, seriesID: nil, season: nil, episode: nil)
    }

    func enqueueEpisode(model: AppModel, episode: Episode) {
        guard let playlist = model.activePlaylist, let creds = model.credentials(for: playlist),
              let candidate = StreamURLBuilder.episode(creds, episodeID: episode.id, containerExtension: episode.containerExtension) else { return }
        let ext = StreamURLBuilder.sanitizeExtension(episode.containerExtension) ?? "mp4"
        let series = try? model.database.catalog.series(playlistID: playlist.id, id: episode.seriesID)
        let title = "\(series.map { ContentText.displayTitles($0.name).primary } ?? "") S\(episode.season)E\(episode.episodeNumber) \(episode.title)"
        start(model: model, playlistID: playlist.id, type: .episode, itemID: episode.id, title: title, image: episode.thumbnail ?? series?.cover,
              ext: ext, url: candidate.url, seriesID: episode.seriesID, season: episode.season, episode: episode.episodeNumber)
    }

    private func start(model: AppModel, playlistID: Int64, type: ContentType, itemID: String, title: String, image: String?, ext: String, url: URL,
                       seriesID: Int?, season: Int?, episode: Int?) {
        self.model = model
        let store = model.database.userData
        if let existing = store.findDownload(playlistID, type, itemID), existing.status != .failed {
            model.showToast(existing.status == .completed ? tr("تم تحميل هذا المحتوى مسبقاً", "Already downloaded") : tr("التحميل قيد التنفيذ", "Download in progress"), .info)
            return
        }
        if let old = store.findDownload(playlistID, type, itemID) { try? store.deleteDownload(old.id) }
        guard let id = try? store.addDownload(playlistID, type: type, itemID: itemID, title: title, image: image, ext: ext, seriesID: seriesID, season: season, episode: episode) else { return }
        let dir = model.paths.downloads
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var dest = dir.appendingPathComponent(fileName(title, ext: ext))
        if FileManager.default.fileExists(atPath: dest.path) {
            dest = dir.appendingPathComponent(fileName("\(title) \(id)", ext: ext))
        }
        let task = session.downloadTask(with: url)
        lock.lock()
        tasks[task.taskIdentifier] = id
        destinations[id] = dest
        playlistIDs[id] = playlistID
        lock.unlock()
        active[id] = 0
        try? store.updateDownload(id, status: .downloading, progress: 0)
        task.resume()
        model.showToast(tr("بدأ التحميل: ", "Downloading: ") + title, .success)
    }

    func cancel(_ record: DownloadRecord) {
        session.getAllTasks { all in
            DispatchQueue.main.async {
                self.lock.lock()
                let match = self.tasks.first(where: { $0.value == record.id })?.key
                self.lock.unlock()
                if let m = match { all.first(where: { $0.taskIdentifier == m })?.cancel() }
                self.active[record.id] = nil
            }
        }
    }

    func delete(_ record: DownloadRecord, model: AppModel) {
        cancel(record)
        if let path = record.filePath { try? FileManager.default.removeItem(atPath: path) }
        try? model.database.userData.deleteDownload(record.id)
    }

    // MARK: URLSessionDownloadDelegate (main queue)

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
        lock.lock(); let id = tasks[downloadTask.taskIdentifier]; lock.unlock()
        guard let rid = id, totalBytesExpectedToWrite > 0 else { return }
        let fraction = Double(totalBytesWritten) / Double(totalBytesExpectedToWrite)
        if (active[rid] ?? 0) + 0.01 < fraction || fraction >= 1 {
            active[rid] = fraction
            try? model?.database.userData.updateDownload(rid, status: .downloading, progress: fraction, totalBytes: totalBytesExpectedToWrite)
        }
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask, didFinishDownloadingTo location: URL) {
        lock.lock()
        let id = tasks[downloadTask.taskIdentifier]
        let dest = id.flatMap { destinations[$0] }
        lock.unlock()
        guard let rid = id, let destination = dest else { return }
        let code = (downloadTask.response as? HTTPURLResponse)?.statusCode ?? 200
        guard (200..<300).contains(code) else {
            try? model?.database.userData.updateDownload(rid, status: .failed, progress: 0)
            return
        }
        do {
            try? FileManager.default.removeItem(at: destination)
            try FileManager.default.moveItem(at: location, to: destination)
            try model?.database.userData.updateDownload(rid, status: .completed, progress: 1, filePath: destination.path)
            model?.showToast(tr("اكتمل التحميل", "Download complete"), .success)
        } catch {
            try? model?.database.userData.updateDownload(rid, status: .failed, progress: 0)
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        lock.lock()
        let id = tasks.removeValue(forKey: task.taskIdentifier)
        lock.unlock()
        guard let rid = id else { return }
        active[rid] = nil
        if let error = error {
            let cancelled = (error as NSError).code == NSURLErrorCancelled
            try? model?.database.userData.updateDownload(rid, status: cancelled ? .paused : .failed, progress: 0)
            if !cancelled { model?.showError(error) }
        }
    }
}
