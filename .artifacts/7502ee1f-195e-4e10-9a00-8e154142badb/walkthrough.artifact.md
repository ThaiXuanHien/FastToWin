# Task 2: Game Engine and Adaptive UI Walkthrough

I have implemented the core game mechanics and a responsive UI for "Fast To Win".

## Key Features

### 1. Game Modes
- **Order Mode**: Players must find and click numbers from 1 to 100 in sequence.
- **Time Attack**: Players have 5 minutes to get as many numbers as possible in sequence.

### 2. Randomized Number Grid
- The grid displays numbers 1-100 in a shuffled order.
- It uses `LazyVerticalGrid` with adaptive columns, ensuring a great look on both narrow phones and wide tablets.
- **Visual Feedback**:
    - **Target Number**: Highlighted with `primaryContainer` color and extra-bold text.
    - **Completed Numbers**: Dimmed to show progress.
    - **Error Feedback**: Displays a brief message if the wrong number is clicked.

### 3. Adaptive & Material 3 Design
- **Responsive Layout**: The grid scales automatically based on screen width.
- **Dynamic Color**: Leverages Material 3 dynamic color schemes for a personalized look.
- **Edge-to-Edge**: Fully supports system bar insets for a modern, immersive feel.

## Technical Details

- **Architecture**: State-driven using `GameViewModel` and `GameState` flow.
- **Navigation**: Built with Jetpack Navigation 3, passing game parameters between screens safely.
- **Logic**:
    - Timer managed via `viewModelScope` and `delay`.
    - Shuffling using Kotlin's `.shuffled()`.

## Screenshots / Previews
(Simulated previews included in code for `GameScreenMobilePreview` and `GameScreenTabletPreview`)

## Verification Results
- **Build**: Successful via `./gradlew :app:assembleDebug`.
- **Navigation**: Verified flow from Lobby -> Game -> Result -> Lobby.
- **Logic**: ascending click validation and timer countdown verified in ViewModel logic.
