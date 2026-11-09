import AppKit
import AVKit
import SwiftUI

/// Hosts the engine's video view. The same NSView is re-parented when the player moves between
/// the Live TV preview panel and the full player, so playback continues without reconnecting.
struct VideoSurface: NSViewRepresentable {
    let engine: PlaybackEngine

    func makeNSView(context: Context) -> VideoContainerView {
        let v = VideoContainerView()
        v.attach(engine.view)
        return v
    }

    func updateNSView(_ nsView: VideoContainerView, context: Context) {
        nsView.attach(engine.view)
    }
}

final class VideoContainerView: NSView {
    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        wantsLayer = true
        layer?.backgroundColor = NSColor.black.cgColor
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    func attach(_ video: NSView) {
        guard video.superview !== self else { return }
        video.removeFromSuperview()
        video.frame = bounds
        video.autoresizingMask = [.width, .height]
        addSubview(video)
    }

    override func layout() {
        super.layout()
        subviews.forEach { $0.frame = bounds }
    }

    override var acceptsFirstResponder: Bool { false }
}

/// Reports mouse movement (to reveal the auto-hiding player controls) and double clicks.
struct MouseActivityView: NSViewRepresentable {
    let onMove: () -> Void
    var onDoubleClick: (() -> Void)?
    var onClick: (() -> Void)?

    func makeNSView(context: Context) -> TrackingView {
        let v = TrackingView()
        v.onMove = onMove
        v.onDoubleClick = onDoubleClick
        v.onClick = onClick
        return v
    }

    func updateNSView(_ nsView: TrackingView, context: Context) {
        nsView.onMove = onMove
        nsView.onDoubleClick = onDoubleClick
        nsView.onClick = onClick
    }

    final class TrackingView: NSView {
        var onMove: (() -> Void)?
        var onDoubleClick: (() -> Void)?
        var onClick: (() -> Void)?
        private var area: NSTrackingArea?

        override func updateTrackingAreas() {
            super.updateTrackingAreas()
            if let a = area { removeTrackingArea(a) }
            let a = NSTrackingArea(rect: bounds, options: [.mouseMoved, .mouseEnteredAndExited, .activeAlways, .inVisibleRect], owner: self, userInfo: nil)
            addTrackingArea(a)
            area = a
        }

        override func mouseMoved(with event: NSEvent) { onMove?() }
        override func mouseEntered(with event: NSEvent) { onMove?() }
        override func mouseDown(with event: NSEvent) {
            if event.clickCount == 2 { onDoubleClick?() } else { onClick?(); onMove?() }
        }
    }
}

/// Keyboard shortcuts while the player is visible (space, arrows, F, M, Esc).
final class PlayerKeyMonitor {
    private var monitor: Any?

    func install(_ handler: @escaping (NSEvent) -> Bool) {
        remove()
        monitor = NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
            // Never steal keys from text fields.
            if let responder = event.window?.firstResponder, responder is NSTextView { return event }
            return handler(event) ? nil : event
        }
    }

    func remove() {
        if let m = monitor { NSEvent.removeMonitor(m) }
        monitor = nil
    }

    deinit { remove() }
}

/// AirPlay / audio route picker ("Cast" button).
struct RoutePickerButton: NSViewRepresentable {
    func makeNSView(context: Context) -> AVRoutePickerView {
        let v = AVRoutePickerView()
        v.isRoutePickerButtonBordered = false
        v.setRoutePickerButtonColor(.white, for: .normal)
        v.setRoutePickerButtonColor(NSColor(red: 1, green: 0.17, blue: 0.18, alpha: 1), for: .active)
        return v
    }
    func updateNSView(_ nsView: AVRoutePickerView, context: Context) {}
}
