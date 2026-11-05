import ActivityKit
import Shared
import UIKit
import UniformTypeIdentifiers
import WidgetKit

/// Реализация IosBridge из Kotlin: всё, что проще сделать на Swift.
final class AppBridge: NSObject, IosBridge {
    private var picker: FilePicker?

    // MARK: Виджет и Live Activity

    func statusChanged(json: String) {
        AppGroup.defaults?.set(json, forKey: AppGroup.statusKey)
        WidgetCenter.shared.reloadAllTimelines()
        if #available(iOS 16.1, *), let status = BabyStatus.decode(json) {
            LiveActivityManager.apply(status)
        }
    }

    // MARK: Поделиться файлом

    func shareFile(name: String, data: Data) {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        do {
            try data.write(to: url, options: .atomic)
        } catch {
            return
        }
        let controller = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        AppBridge.topController()?.present(controller, animated: true)
    }

    // MARK: Выбор файла резервной копии

    func pickTextFile(callback: @escaping (String?) -> Void) {
        let picker = FilePicker { [weak self] text in
            callback(text)
            self?.picker = nil
        }
        self.picker = picker
        let controller = UIDocumentPickerViewController(forOpeningContentTypes: [.json, .plainText, .data], asCopy: true)
        controller.delegate = picker
        controller.allowsMultipleSelection = false
        AppBridge.topController()?.present(controller, animated: true)
    }

    // MARK: PDF

    func renderPdf(json: String) -> Data {
        guard let raw = json.data(using: .utf8),
              let pages = try? JSONDecoder().decode([PdfPageDTO].self, from: raw),
              let first = pages.first
        else { return Data() }
        let bounds = CGRect(x: 0, y: 0, width: first.width, height: first.height)
        let renderer = UIGraphicsPDFRenderer(bounds: bounds)
        return renderer.pdfData { context in
            for page in pages {
                context.beginPage()
                for item in page.items {
                    let color = UIColor(rgb: item.color)
                    if item.kind == "rect" {
                        color.setFill()
                        UIRectFill(CGRect(x: item.x, y: item.y, width: item.w, height: item.h))
                    } else {
                        let font = item.bold ? UIFont.boldSystemFont(ofSize: item.size) : UIFont.systemFont(ofSize: item.size)
                        (item.text as NSString).draw(
                            at: CGPoint(x: item.x, y: item.y),
                            withAttributes: [.font: font, .foregroundColor: color]
                        )
                    }
                }
            }
        }
    }

    static func topController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive } ?? UIApplication.shared.connectedScenes.first as? UIWindowScene
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController ?? scene?.windows.first?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}

private final class FilePicker: NSObject, UIDocumentPickerDelegate {
    private let done: (String?) -> Void

    init(done: @escaping (String?) -> Void) {
        self.done = done
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let url = urls.first else { return done(nil) }
        let access = url.startAccessingSecurityScopedResource()
        defer { if access { url.stopAccessingSecurityScopedResource() } }
        done(try? String(contentsOf: url, encoding: .utf8))
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        done(nil)
    }
}

/// Страница PDF из Kotlin (shared/.../logic/Report.kt).
private struct PdfPageDTO: Decodable {
    var width: CGFloat = 595
    var height: CGFloat = 842
    var items: [PdfItemDTO]
}

private struct PdfItemDTO: Decodable {
    var kind: String
    var x: CGFloat
    var y: CGFloat
    var w: CGFloat = 0
    var h: CGFloat = 0
    var text: String = ""
    var size: CGFloat = 10
    var bold: Bool = false
    var color: Int = 0x222222

    enum CodingKeys: String, CodingKey { case kind, x, y, w, h, text, size, bold, color }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        kind = try c.decode(String.self, forKey: .kind)
        x = try c.decode(CGFloat.self, forKey: .x)
        y = try c.decode(CGFloat.self, forKey: .y)
        w = try c.decodeIfPresent(CGFloat.self, forKey: .w) ?? 0
        h = try c.decodeIfPresent(CGFloat.self, forKey: .h) ?? 0
        text = try c.decodeIfPresent(String.self, forKey: .text) ?? ""
        size = try c.decodeIfPresent(CGFloat.self, forKey: .size) ?? 10
        bold = try c.decodeIfPresent(Bool.self, forKey: .bold) ?? false
        color = try c.decodeIfPresent(Int.self, forKey: .color) ?? 0x222222
    }
}

extension PdfPageDTO {
    enum CodingKeys: String, CodingKey { case width, height, items }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        width = try c.decodeIfPresent(CGFloat.self, forKey: .width) ?? 595
        height = try c.decodeIfPresent(CGFloat.self, forKey: .height) ?? 842
        items = try c.decode([PdfItemDTO].self, forKey: .items)
    }
}

private extension UIColor {
    convenience init(rgb: Int) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}
