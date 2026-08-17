# Running Fast To Win on iOS

The shared Compose UI, game state, room protocol, and WebSocket client live in the `shared` Kotlin Multiplatform module.

## Requirements

- An Apple Silicon Mac
- Xcode with an iOS 14+ simulator or a provisioned iPhone
- JDK 17 or newer

## Run

1. Clone the project on macOS.
2. Open `iosApp/iosApp.xcodeproj` in Xcode.
3. Select the `iosApp` scheme and an iOS simulator.
4. If running on a physical device, select your Development Team and change the bundle identifier if necessary.
5. Run the app. Xcode invokes `:shared:embedAndSignAppleFrameworkForXcode` automatically.

The project targets `iosArm64` for physical devices and `iosSimulatorArm64` for Apple Silicon simulators.

See [UI_TESTING.md](UI_TESTING.md) for the iPhone/iPad test matrix, local server URL setup, and release verification checklist.
