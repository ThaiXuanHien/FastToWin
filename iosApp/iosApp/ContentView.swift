import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let serverUrl = Bundle.main.object(forInfoDictionaryKey: "GAME_SERVER_URL") as? String
            ?? "ws://127.0.0.1:8080/game"
        return MainViewControllerKt.MainViewController(serverUrl: serverUrl)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.container, edges: .all)
            .ignoresSafeArea(.keyboard)
            .onOpenURL { url in
                AppDeepLinkRouter.shared.openUri(uri: url.absoluteString)
            }
    }
}
