import SwiftUI
import Shared

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
        }
    }
}

/// Весь интерфейс нарисован в Compose (модуль shared), здесь только обёртка.
struct ComposeView: UIViewControllerRepresentable {
    private static let bridge = AppBridge()

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(bridge: ComposeView.bridge)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
