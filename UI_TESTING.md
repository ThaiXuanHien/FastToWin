# UI testing

## Automated Compose UI tests

The `devDebug` instrumentation suite is independent from the backend. It renders each screen with controlled state and verifies user interaction callbacks.

Covered flows:

- Email/password login, text input, keyboard-safe scrolling, and submit.
- Home dashboard and quick-match mode selection.
- Create a private room and join a password-protected room.
- Render the 50-number board, select number 1, and scroll to number 50.
- Match result summary and return to the lobby.
- Friend item profile navigation and the separate sensitive-actions menu.
- A friend and recent opponent sharing the same user ID without duplicate list keys.
- Responsive home layout at 320x568, 430x932, 840x1180, and 720x400 dp.
- 1.6x font scale and a long display name on a small viewport.

Run from the Android Studio terminal on Windows:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME='D:\HienTX\Work\Android\FastToWin\.gradle'
.\gradlew.bat :app:connectedDevDebugAndroidTest --no-daemon
```

At least one emulator or Android device must be online. The HTML result is generated at:

```text
app/build/reports/androidTests/connected/debug/flavors/dev/index.html
```

Compile the test APK without running a device:

```powershell
.\gradlew.bat :app:compileDevDebugAndroidTestKotlin --no-daemon
```

## Manual Android matrix

Before a release, repeat the critical flows on these actual emulator/device configurations:

| Class | Suggested viewport | Required checks |
| --- | --- | --- |
| Small phone | 320-360 dp wide | Login keyboard, room dialogs, bottom navigation, board cells |
| Large phone | 420-480 dp wide | Home shortcuts, profile charts, friend menus |
| Tablet | 800+ dp wide | Centered content width, 10-column number board, dialogs |
| Landscape | Phone and tablet | Home scrolling, room waiting, game board, result screen |

Also test system font at default and the largest accessibility size. Use long room names, long player names, empty lists, and populated lists.

## iOS and iPadOS verification

Run this stage on an Apple Silicon Mac with Xcode after Android tests pass:

1. Start the dev server and PostgreSQL on the Mac, or use a server reachable on the local network.
2. Open `iosApp/iosApp.xcodeproj` and select the `iosApp` scheme.
3. For Simulator, the Debug setting `GAME_SERVER_URL=ws://127.0.0.1:8080/game` reaches a server on the Mac.
4. For a physical iPhone/iPad, change the Debug `GAME_SERVER_URL` to the Mac LAN address, for example `ws://192.168.1.20:8080/game`, and allow port 8080 through the firewall.
5. Run on a small iPhone simulator, a Pro Max simulator, and an iPad simulator in portrait and landscape.
6. Repeat on at least one physical iPhone or iPad before release.

Verify on every selected iOS device:

- Register/login and reopen the app with the stored session.
- Home navigation and pull-to-refresh behavior.
- Create/join a room with two devices and complete a 50-number match.
- Result, rematch, Elo, and return-to-lobby flows.
- Friend profile, recent opponents, invite-to-room menu, remove/block confirmation, and copy player code.
- Software keyboard dismissal and scrolling in login/room dialogs.
- Long content, safe areas, home indicator, rotation, and large accessibility text.

Record the device, OS version, orientation, server URL, and pass/fail result for each run.
