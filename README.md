# RT Chatbot App

## What this project demonstrates (features)

* Real-time messages via WebSocket with reconnection & exponential backoff.
* Offline queueing of messages (persisted to Room) when sends fail, with automatic resend upon reconnect.
* Incoming message deduplication:

  * Primary: match by server `id` (ACK handling).
  * Fallback: simple hash-based dedupe window for servers that don't echo ids.
* Chat list with unread badge counts and thread previews.
* Navigation to per-thread Chat screen.
* Optimistic UI updates and ACK reconciliation.
* Dev toggle to simulate failure (`SimFail`) to test queueing/resend logic.
* Compose UI (Material3) with keyboard handling and scroll-to-bottom behavior.
* Readable, testable code and unit tests for critical ViewModel logic (send/queue/resend/dedupe).

---

## Tech stack & Architecture

**Preferred tech & libraries**

* Kotlin (Jetpack Compose UI)
* AndroidX (Activity, Lifecycle, Room, ViewModel)
* Hilt for DI
* OkHttp WebSocket for realtime transport
* Kotlin Coroutines & Flow for async / reactive flows
* Room for persistent queued messages & thread metadata
* Material3 components for UI
* JUnit + kotlinx-coroutines-test for unit testing

**High level architecture**

* **UI layer**: Compose screens and components (`ChatScreen`, `ChatListScreen`, `components/*`) powered by `ChatViewModel` and `ChatListViewModel`.
* **ViewModel**: Holds UI state flows, handles business logic (send, queue, resend, dedupe, mark read).
* **Repository**: `ChatRepository` abstracts local (Room) and remote (SocketClient) data sources.
* **Data sources**:

    * **Remote**: `SocketClient` — OkHttp WebSocket wrapper with reconnect/backoff.
    * **Local**: Room (entities: `ChatThreadEntity`, `QueuedMessageEntity`) used for persisted metadata and queued messages.
* **Session state**: ephemeral in-memory state (active threads for this app session) kept separate from persisted thread rows.

This pattern keeps UI testable and repo isolated for unit testing.

---

## Project layout (important folders)

Top-level package: `com.rtchatbotapp`

```
/src/main/java/com/rtchatbotapp
 ├─ data/
 │   ├─ model/                # Message, ChatThread, Room Entities
 │   ├─ repository/           # ChatRepository interface/impl
 │   └─ source/
 │       ├─ remote/           # SocketClient, network related
 │       └─ local/            # Room DAOs, DB
 ├─ di/                      # Hilt AppModule and dispatcher bindings
 ├─ ui/
 │   ├─ components/           # ChatComposer, MessageBubble, ChatListItem, TopBar
 │   ├─ screens/              # ChatScreen, ChatListScreen, AppNavHost
 │   ├─ theme/                # Color, Type, Theme
 │   └─ viewmodel/            # ChatViewModel, ChatListViewModel, ChatUiState
 └─ util/                     # NetworkObserver, Application class, etc.
```

(You can see the actual layout in the project IDE tree — `ui/components`, `data/model`, `data/source/remote`, `di`, `viewmodel`.)

---

## Android build / SDK config (project uses)

```gradle
defaultConfig {
    applicationId = "com.rtchatbotapp"
    minSdk = 29
    targetSdk = 36
}
```

---

## How to run locally

1. Clone repository
2. Open in Android Studio Flamingo/Chipmunk or later (AGP 8.13 recommended)
3. Provide a WebSocket URL:

    * Default sockets can be tested with tools such as [PieHost](https://piehost.com/websocket-tester) WebSocket Tester(e.g.`wss://<your-piehost-url>`).
    * The socket URL is configurable in `BuildConfig` or a central constants file, update before running.
4. Build & run on a device/emulator (API 29+).
5. Use the UI `SimFail` toggle to simulate offline/failure scenarios.

**Recommended debug flow**

* Open Logcat and filter by tag `ChatViewModel` and `SocketClient`.
* Use PieHost to send JSON payloads (see payload examples below) while showing the app to record demo.

---

## Message payload examples (for PieHost testing)

**Remote message (should appear as incoming)**

```json
{
  "threadId": "default",
  "id": "remote-1",
  "text": "Hello from PieHost (remote)",
  "timestamp": 1700000000000
}
```

**Client message (app sends with local id; server echo should be ACK)**

App sends:

```json
{ "id": "local-uuid-123", "threadId": "default", "text": "Hi there" }
```

Server echo (ACK) should send back the same message with same `id`. When the echo (ACK) arrives the ViewModel will mark the local message status as `SENT` and will not increment unread.

---

## Simulate failure & offline testing

* The UI includes a `SimFail` toggle, enabling it will make `sendUserMessage()` behave as if the network/send failed so the message is persisted to the queued table and shown with `PENDING`.
* When `SimFail` is toggled off, the app attempts to reconnect (if needed) and resend queued messages. Resend runs under a mutex to avoid overlapping runs and uses a timeout to prevent blocking.

---

## Dedupe & resilience details

* **Dedupe strategy**

    * If incoming JSON contains an `id`, ViewModel checks if a message exists locally with that `id`: treat it as an ACK or already processed.
    * If no `id`, a small in-memory dedupe window uses `text.hashCode()` and a short time window (default 5s) to drop duplicates due to repeated echoes.
* **Resilience**

    * `SocketClient` implements exponential backoff reconnect (capped), cancelable reconnect job, and idempotent connect/disconnect calls.
    * Messages are optimistically shown in UI and reconciled on ACK arrival.
* **Queue**

    * Failed sends are persisted as `QueuedMessageEntity` and retried on connection restored.

---

## Testing

**Unit tests**

* Uses JUnit + `kotlinx-coroutines-test`.
* Important tests included:

    * `sendUserMessage` -> when simulateFail true message is queued and UI shows `PENDING`.
    * Incoming dedupe tests: same message twice results in single UI message.
    * `attemptResendQueuedSafely()` behaviour when repo returns queued messages.

**How to run tests**

```bash
./gradlew test
./gradlew connectedAndroidTest
```

---

## Notes, trade-offs & future scope

**Design decisions**

* Session-only UI state (active threads set) is kept in-memory to avoid persisting UI ephemeral state across app restarts.
* JSON is used for message payloads because it's easy to inspect during the demo. Protocol Buffers would be a next step for compactness and schema enforcement.
* Room persists only essential metadata + queued messages to keep offline & resend logic reliable.

**Future improvements**

* Use Protocol Buffers for messages to avoid parsing issues and reduce payload size.
* Add end-to-end instrumentation tests using `MockWebServer`.
* Use WorkManager to handle background retries when the app is backgrounded/killed.
* Add message encryption for persisted queued messages.
* Add pagination and better performance for large conversation histories.

---

## Build & Test

1. Build and run the app.
2. Toggle `SimFail` and verify queued behavior.
3. Use PieHost to send JSON messages and verify incoming UI and unread badge behavior.
4. Observe logs (filter `ChatViewModel`, `SocketClient`) to confirm dedupe, upsert thread metadata and resend logs.

---
