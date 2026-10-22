import SwiftUI
import UIKit

@objc(NiobiumSceneDelegate)
final class NiobiumSceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    private var store: AppStore?

    func scene(_ scene: UIScene,
               willConnectTo session: UISceneSession,
               options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }
        let store = AppStore()
        self.store = store
        let host = UIHostingController(rootView: RootView().environmentObject(store))
        host.view.backgroundColor = UIColor(red: 7/255, green: 5/255, blue: 12/255, alpha: 1)
        let window = UIWindow(windowScene: windowScene)
        window.rootViewController = host
        self.window = window
        window.makeKeyAndVisible()
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        // DSHAppDelegate checks the guest; the native transport also needs to
        // reattach after iOS suspends its loopback WebSocket.
        Task { await store?.resumeConnection() }
    }
}
