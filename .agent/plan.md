# Project Plan

Fast To Win - Game đối kháng 2 người chơi qua Socket Realtime.
Tính năng:
1. Kết nối Realtime: Sử dụng WebSockets để kết nối 2 người chơi thực.
2. Phòng chờ: Người chơi tham gia phòng, đợi đối thủ. Khi đủ 2 người thì bắt đầu.
3. Đồng bộ Game: Đồng bộ grid số, điểm số và trạng thái click giữa 2 thiết bị.
4. Chế độ chơi: Theo thứ tự 1-100 và Theo thời gian.
5. Không có bot, chỉ chơi người với người.
6. UI adaptive cho Mobile và Tablet.

## Project Brief

# Project Brief: Fast To Win

## Features
- **Real-time Multiplayer (WebSockets):** Competitive 1v1 gameplay connecting two human players via low-latency WebSockets.
- **Matchmaking & Lobby System:** A dedicated waiting area where players can host or join rooms, with automatic game starting once both players are ready.
- **Game State Synchronization:** Seamless real-time syncing of the random 1-100 number grid, player scores, and click interactions between both devices.
- **Dual Competitive Modes:** Support for both 'Sequence' mode (tapping 1-100 in order) and 'Time-limited' mode (highest score within a set duration).
- **Adaptive UI Design:** A fully responsive interface optimized for both Mobile and Tablet form factors, ensuring a consistent experience across devices.

## High-Level Technical Stack
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Navigation:** Jetpack Navigation 3 (State-driven)
- **Adaptive Layouts:** Compose Material Adaptive Library
- **Concurrency:** Kotlin Coroutines & Flow (for real-time event handling)
- **Networking:** Ktor Client (WebSocket support)
- **Architecture:** MVVM with centralized state management for game synchronization.

> [!NOTE]
> This MVP focuses strictly on human-vs-human interaction over the network, intentionally excluding bot logic to ensure a pure competitive experience.

## Implementation Steps
**Total Duration:** 16m 58s

### Task_1_Setup_Navigation_and_State: Set up the Compose Multiplatform project structure, integrate Jetpack Navigation 3, and establish the state-driven architecture for transitioning between the Lobby, Game, and Result screens.
- **Status:** COMPLETED
- **Updates:** Thiết lập cấu trúc dự án Compose Multiplatform, tích hợp Jetpack Navigation 3 và kiến trúc state-driven. Đã tạo các màn hình Lobby, Game, Result placeholder và GameViewModel. Dự án build thành công.
- **Acceptance Criteria:**
  - Project builds successfully
  - Navigation between screen placeholders works via state changes
  - Shared game state is initialized
- **Duration:** 5m 14s

### Task_2_Game_Engine_and_Adaptive_UI: Implement the randomized 1-100 number grid and the core game logic for 'Order' and 'Time Attack' modes. Design the gameplay UI using Compose Material Adaptive to ensure responsiveness across different devices.
- **Status:** COMPLETED
- **Updates:** Đã triển khai Game Engine cho cả 2 chế độ (Order và Time Attack). Tạo grid 1-100 ngẫu nhiên. UI GameScreen sử dụng LazyVerticalGrid adaptive để hiển thị tốt trên cả điện thoại và tablet. Tích hợp timer và logic tính điểm. Cập nhật LobbyScreen để chọn chế độ chơi và ResultScreen để hiển thị kết quả.
- **Acceptance Criteria:**
  - Randomized number grid is displayed
  - 'Order' mode correctly tracks ascending hits
  - 'Time Attack' timer and scoring are functional
  - UI adapts to phone and tablet layouts
- **Duration:** 2m 8s

### Task_3_Lobby_and_Victory_Logic: Build the Multiplayer Waiting Room UI and the post-match Result screen. Integrate the victory logic to compare player performance and declare a winner in real-time.
- **Status:** COMPLETED
- **Updates:** Đã xây dựng UI phòng chờ (Lobby) với logic giả lập kết nối 2 người chơi. Khi cả 2 sẵn sàng, trận đấu tự động bắt đầu sau 3 giây đếm ngược. Màn hình kết quả (ResultScreen) đã hoàn thiện, so sánh điểm số giữa người chơi và đối thủ (giả lập) để tuyên bố thắng/thua. Đã thêm icon ứng dụng và hoàn thiện thiết kế Material 3.
- **Acceptance Criteria:**
  - Lobby UI displays player status
  - Match starts automatically when both players are ready
  - Result screen shows correct winner based on game data
- **Duration:** 9m 36s

### Task_4_Realtime_WebSocket_Integration: Integrate Ktor WebSockets to connect two real players. Implement GameSocketClient for network communication, update GameViewModel to handle socket events (join, grid sync, moves), and remove bot logic to ensure pure human-vs-human competitive play.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - Ktor dependencies added and WebSocket client functional
  - Grid 1-100 is synchronized between devices via sockets
  - Game moves and scores are updated in real-time across connections
  - Bot logic is completely removed
- **StartTime:** 2026-08-09 12:45:07 GMT+07:00

### Task_5_Run_and_Verify: Perform final end-to-end verification of the real-time multiplayer experience. Test on both Phone and Tablet to ensure stability, responsiveness, and correct win/loss logic across the network.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Build pass
  - App does not crash
  - Real-time multiplayer flow works from Lobby to Result
  - UI is responsive on phone and tablet
  - All existing tests pass

