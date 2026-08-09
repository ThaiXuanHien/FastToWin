# Implementation Plan - Task 1: Setup Navigation and State

Set up the core navigation and state management for "Fast To Win" using Jetpack Navigation 3 and a shared game state.

## Proposed Changes

### Navigation Component
Define the navigation keys for the app screens.

#### [NEW] [NavKey.kt](file:///D:/HienTX/Work/Android/FastToWin/app/src/main/java/com/hienthai/fastowin/navigation/NavKey.kt)
Define `@Serializable` keys for Lobby, Game, and Result screens.

#### [NEW] [FastToWinNavHost.kt](file:///D:/HienTX/Work/Android/FastToWin/app/src/main/java/com/hienthai/fastowin/navigation/FastToWinNavHost.kt)
Implement the `NavDisplay` to handle screen transitions based on the backstack.

### State Management
Establish a shared state to be used across screens.

#### [NEW] [GameState.kt](file:///D:/HienTX/Work/Android/FastToWin/app/src/main/java/com/hienthai/fastowin/state/GameState.kt)
Define data classes representing the game state.

#### [NEW] [GameViewModel.kt](file:///D:/HienTX/Work/Android/FastToWin/app/src/main/java/com/hienthai/fastowin/state/GameViewModel.kt)
A ViewModel scoped to the navigation backstack to manage the shared state.

### UI Screens (Placeholders)
Create placeholder Composables for each screen.

#### [NEW] [LobbyScreen.kt](file:///D:/HienTX/Work/Android/FastToWin/app/src/main/java/com/hienthai/fastowin/ui/screens/LobbyScreen.kt)
#### [NEW] [GameScreen.kt](file:///D:/HienTX/Work/Android/FastToWin/app/src/main/java/com/hienthai/fastowin/ui/screens/GameScreen.kt)
#### [NEW] [ResultScreen.kt](file:///D:/HienTX/Work/Android/FastToWin/app/src/main/java/com/hienthai/fastowin/ui/screens/ResultScreen.kt)

### Main Entry Point
Update `MainActivity` to use the new navigation host.

#### [MODIFY] [MainActivity.kt](file:///D:/HienTX/Work/Android/FastToWin/app/src/main/java/com/hienthai/fastowin/MainActivity.kt)

## Verification Plan

### Automated Tests
- Run `./gradlew :app:assembleDebug` to ensure it builds.

### Manual Verification
- Verify navigation from Lobby -> Game -> Result -> Lobby using buttons on placeholder screens.
