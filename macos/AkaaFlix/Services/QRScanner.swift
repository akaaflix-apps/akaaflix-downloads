import AppKit
import AVFoundation
import CoreImage
import SwiftUI

/// QR decoding (CoreImage, macOS 10.10+) from images and from the Mac's camera.
enum QRDecoder {
    private static let detector = CIDetector(ofType: CIDetectorTypeQRCode, context: nil, options: [CIDetectorAccuracy: CIDetectorAccuracyHigh])

    static func decode(_ image: CIImage) -> [String] {
        (detector?.features(in: image) ?? []).compactMap { ($0 as? CIQRCodeFeature)?.messageString }
    }

    static func decode(fileURL: URL) -> [String] {
        guard let ci = CIImage(contentsOf: fileURL) else { return [] }
        return decode(ci)
    }

    /// Generates a QR image (used for the WhatsApp contact card on the About screen).
    static func generate(_ text: String, size: CGFloat) -> NSImage? {
        guard let filter = CIFilter(name: "CIQRCodeGenerator") else { return nil }
        filter.setValue(Data(text.utf8), forKey: "inputMessage")
        filter.setValue("M", forKey: "inputCorrectionLevel")
        guard let output = filter.outputImage else { return nil }
        let scale = size / output.extent.width
        let scaled = output.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        let rep = NSCIImageRep(ciImage: scaled)
        let image = NSImage(size: rep.size)
        image.addRepresentation(rep)
        return image
    }
}

/// Camera preview that reports the first QR payload it sees.
final class CameraQRScanner: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    @Published var status: String = ""
    @Published var found: String?
    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "akaaflix.qr")
    private var lastScan = Date.distantPast

    func start() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: configure()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { ok in
                DispatchQueue.main.async { ok ? self.configure() : (self.status = tr("لم يتم السماح باستخدام الكاميرا", "Camera access was not allowed")) }
            }
        default:
            status = tr("يرجى السماح باستخدام الكاميرا من إعدادات النظام", "Allow camera access in System Settings")
        }
    }

    private func configure() {
        guard let device = AVCaptureDevice.default(for: .video), let input = try? AVCaptureDeviceInput(device: device) else {
            status = tr("لا توجد كاميرا متاحة", "No camera available")
            return
        }
        session.beginConfiguration()
        if session.canAddInput(input) { session.addInput(input) }
        let output = AVCaptureVideoDataOutput()
        output.setSampleBufferDelegate(self, queue: queue)
        if session.canAddOutput(output) { session.addOutput(output) }
        session.commitConfiguration()
        queue.async { self.session.startRunning() }
        status = tr("وجّه رمز QR نحو الكاميرا", "Point the QR code at the camera")
    }

    func stop() { queue.async { if self.session.isRunning { self.session.stopRunning() } } }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard Date().timeIntervalSince(lastScan) > 0.3, let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        lastScan = Date()
        if let text = QRDecoder.decode(CIImage(cvPixelBuffer: buffer)).first {
            DispatchQueue.main.async { if self.found == nil { self.found = text } }
        }
    }
}

struct CameraPreview: NSViewRepresentable {
    let session: AVCaptureSession
    func makeNSView(context: Context) -> NSView {
        let v = NSView()
        v.wantsLayer = true
        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        layer.autoresizingMask = [.layerWidthSizable, .layerHeightSizable]
        v.layer = layer
        return v
    }
    func updateNSView(_ nsView: NSView, context: Context) {}
}

/// "Import from QR code": camera scan or an image file containing the QR.
struct QRImportSheet: View {
    let onImport: (PlaylistInput) -> Void
    let onCancel: () -> Void
    @StateObject private var scanner = CameraQRScanner()
    @State private var cameraOn = false
    @State private var message: String?

    var body: some View {
        VStack(spacing: 16) {
            Text(tr("استيراد قائمة من رمز QR", "Import a playlist from a QR code")).font(.system(size: 20, weight: .heavy)).foregroundColor(.white)
            ZStack {
                RoundedRectangle(cornerRadius: 16).fill(Color.black.opacity(0.6))
                if cameraOn {
                    CameraPreview(session: scanner.session).clipShape(RoundedRectangle(cornerRadius: 16))
                } else {
                    Image(systemName: "qrcode.viewfinder").font(.system(size: 80, weight: .light)).foregroundColor(.white.opacity(0.5))
                }
            }
            .frame(width: 420, height: 280)
            Text(message ?? scanner.status).font(.system(size: 14, weight: .semibold)).foregroundColor(Theme.textSecondary).multilineTextAlignment(.center)
            HStack(spacing: 12) {
                NeonButton(title: tr("الكاميرا", "Camera"), systemImage: "camera", kind: .blue, height: 44, fontSize: 15) {
                    cameraOn = true
                    scanner.start()
                }
                NeonButton(title: tr("من صورة", "From image"), systemImage: "photo", kind: .blue, height: 44, fontSize: 15) { pickImage() }
                NeonButton(title: tr("إلغاء", "Cancel"), systemImage: "xmark", kind: .dark, height: 44, fontSize: 15) { scanner.stop(); onCancel() }
            }
        }
        .padding(24)
        .frame(width: 500)
        .background(Theme.panel)
        .contentDirection()
        .onChange(of: scanner.found) { value in
            guard let v = value else { return }
            handle([v])
        }
        .onDisappear { scanner.stop() }
    }

    private func pickImage() {
        let panel = NSOpenPanel()
        panel.allowedFileTypes = ["png", "jpg", "jpeg", "heic", "tiff", "gif", "bmp"]
        guard panel.runModal() == .OK, let url = panel.url else { return }
        handle(QRDecoder.decode(fileURL: url))
    }

    private func handle(_ payloads: [String]) {
        for p in payloads {
            if let input = PlaylistLinkParser.parse(p) {
                scanner.stop()
                onImport(input)
                return
            }
        }
        scanner.found = nil
        message = payloads.isEmpty ? tr("لم يتم العثور على رمز QR", "No QR code found") : tr("رمز QR لا يحتوي على بيانات Xtream Codes", "The QR code does not contain Xtream Codes details")
    }
}
