import SwiftUI
import FirebaseCore
import FirebaseMessaging
import Shared
import UserNotifications

final class PushNotificationCoordinator {
    static let shared = PushNotificationCoordinator()

    private let disabledKey = "fasttowin.push.disabled"
    private var pendingToken: String?

    lazy var bridge = IosHostPushBridge(
        enableNative: { PushNotificationCoordinator.shared.enable() },
        disableNative: { PushNotificationCoordinator.shared.disable() }
    )

    private var isDisabledByUser: Bool {
        get { UserDefaults.standard.bool(forKey: disabledKey) }
        set { UserDefaults.standard.set(newValue, forKey: disabledKey) }
    }

    func start() {
        refreshAuthorizationStatus(registerIfEnabled: true)
    }

    func enable() {
        UNUserNotificationCenter.current().getNotificationSettings { [weak self] settings in
            guard let self else { return }
            if settings.authorizationStatus == .denied {
                DispatchQueue.main.async {
                    self.bridge.markDenied()
                    guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                    UIApplication.shared.open(url)
                }
                return
            }

            let options: UNAuthorizationOptions = [.alert, .badge, .sound]
            UNUserNotificationCenter.current().requestAuthorization(options: options) { granted, error in
                DispatchQueue.main.async {
                    if error != nil {
                        self.bridge.markError()
                    } else if granted {
                        self.isDisabledByUser = false
                        self.bridge.markEnabled()
                        UIApplication.shared.registerForRemoteNotifications()
                        self.refreshFcmToken()
                    } else {
                        self.bridge.markDenied()
                    }
                }
            }
        }
    }

    func disable() {
        isDisabledByUser = true
        pendingToken = nil
        UIApplication.shared.unregisterForRemoteNotifications()
        bridge.updateToken(token: "")
        bridge.markDisabled()
        Messaging.messaging().deleteToken { error in
            if let error {
                print("FastToWin: could not delete the iOS FCM token: \(error.localizedDescription)")
            }
        }
    }

    func refreshAuthorizationStatus(registerIfEnabled: Bool) {
        UNUserNotificationCenter.current().getNotificationSettings { [weak self] settings in
            guard let self else { return }
            DispatchQueue.main.async {
                if self.isDisabledByUser {
                    self.bridge.markDisabled()
                    return
                }
                switch settings.authorizationStatus {
                case .notDetermined:
                    self.bridge.markPrompt()
                case .denied:
                    self.bridge.markDenied()
                case .authorized, .provisional, .ephemeral:
                    self.bridge.markEnabled()
                    if registerIfEnabled {
                        UIApplication.shared.registerForRemoteNotifications()
                        self.refreshFcmToken()
                    }
                @unknown default:
                    self.bridge.markError()
                }
            }
        }
    }

    func receiveFcmToken(_ token: String?) {
        guard !isDisabledByUser, let token, !token.isEmpty else { return }
        pendingToken = token
        bridge.updateToken(token: token)
    }

    func refreshFcmToken() {
        Messaging.messaging().token { [weak self] token, error in
            if let error {
                print("FastToWin: could not read the iOS FCM token: \(error.localizedDescription)")
                return
            }
            DispatchQueue.main.async {
                self?.receiveFcmToken(token)
            }
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, MessagingDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self
        PushNotificationCoordinator.shared.start()
        return true
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        PushNotificationCoordinator.shared.refreshAuthorizationStatus(registerIfEnabled: true)
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Messaging.messaging().apnsToken = deviceToken
        PushNotificationCoordinator.shared.refreshFcmToken()
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("FastToWin: APNs registration failed: \(error.localizedDescription)")
        PushNotificationCoordinator.shared.bridge.markError()
    }

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        DispatchQueue.main.async {
            PushNotificationCoordinator.shared.receiveFcmToken(fcmToken)
        }
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound, .badge])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        let destination = userInfo["destination"] as? String ?? "/notifications"
        DispatchQueue.main.async {
            _ = IosAppNavigationBridge.shared.openRoute(route: destination)
        }
        completionHandler()
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
