# CLAUDE.md – store-client

Guidance for Claude Code when working in `store-client/`. The repository overview, course spec mapping and environment notes are in the root `CLAUDE.md`.

## Project

The desktop client: Java 17, **Swing**, Gson, JUnit 5, Maven, package `com.store.client`, English UI. It contains **no business rules** — discounts, stock checks, permissions, the chat queue and logging all happen on the server. The client connects, sends requests, shows results and reacts to pushed events. `README.md` here is the human-facing run guide.

## Commands

Run from `store-client/` (see root `CLAUDE.md` for the JDK/Maven setup).

```
mvn package                                   # compile + tests + target/store-client.jar (shaded, runnable)
mvn test -Dtest=ConnectionTest                # one class
java -jar target/store-client.jar [host=localhost] [port=5050]
```

Start the server first. Tests run headless (`-Djava.awt.headless=true` in surefire) and never open windows.

## Architecture

Packages: `net` (connection, codec, typed API, event bus) → `ui` (Swing screens) with `app` (session state, screen list, report opener) and `dto` (protocol mirror).

- **`dto`:** `Dtos.java` mirrors the server's `protocol/dto/Dtos.java` **and** the model records that travel on the wire (`Branch`, `InventoryItem`, `Sale`, `SalesReport`, `ChatMessage`, ...) plus records for chat event payloads (`ChatPending`, `ChatStarted`, `ChatParticipant`, `ChatEnded`, `ForcedLogout`). Enums (`RequestType`, `EventType`, `ErrorCode`, `Permission`, `Role`, `Category`, `CustomerType`, `GroupBy`, `ActionType`) use the server's names. `Customer` is one flat record (`type` + server-written `track`); the client doesn't need the server's per-type classes. **Field names = JSON keys; when the server protocol changes, change these in the same commit.**
- **`net.Connection`:** one socket = one login session. Synchronized writes, a single daemon reader thread, `requestId → CompletableFuture<JsonElement>`. RESPONSE completes the future (`ERROR` → `ServerException(code, message)`); a RESPONSE with no `requestId` (e.g. `SERVER_BUSY`) fails every pending call; EVENT goes to listeners. On drop, pending futures fail and disconnect listeners fire — but not after the client's own `close()`.
- **`net.ServerApi`:** one typed method per `RequestType`, returning `CompletableFuture<Dto>`. Add a method here when the server adds a route.
- **`net.ClientEventBus`:** delivers events on the Swing thread (`invokeLater`). It **holds events until `start()`**; `MainFrame` calls `start()` after every panel registered its handlers, so events pushed right after login (e.g. `CHAT_PENDING_REQUEST`) are not lost.
- **Threading rule:** never touch Swing from the reader thread and never block the UI thread on `.get()`. Use `ui.Async.then(...)` for every server call — it runs the continuation on the Swing thread and shows errors in a dialog.
- **Permissions:** `app.Screen` lists the tabs with the permission each needs; `Session.screens()` decides what `MainFrame` adds. Inside a panel, buttons are added only when `session.has(...)` (e.g. Restock needs `RESTOCK_INVENTORY`, Complete Sale needs `SELL`, Join chat needs `SUPERVISE_CHAT`). The server still enforces everything (`FORBIDDEN`).
- **Session end:** `FORCED_LOGOUT` or a dropped connection → `MainFrame.endSession` closes the connection, explains why, and shows `LoginFrame`. Every login opens a new `Connection`.
- **Live data:** Inventory and Sell listen to `INVENTORY_UPDATED` (own branch); Customers and Sell listen to `CUSTOMER_UPDATED` (all users). Chat messages are shown only when the server relays them back (`CHAT_MESSAGE`), including your own.
- **Chat:** a shift manager who joins gets `CHAT_PARTICIPANT_JOINED` (not `CHAT_STARTED`) — that event opens the chat for them. A leaver gets no `CHAT_ENDED`, so the panel clears its own state on a successful `CHAT_LEAVE`. The server saves a transcript only when the leave ends the chat, so the panel asks "save transcript?" only when two participants remain.
- **Reports:** `REPORT_EXPORT` text is saved under `<tmp>/store-reports/<server file name>` and opened by `app.ReportOpener`: `notepad.exe` on Windows (req 7c), `open -e` (TextEdit) on macOS, `xdg-open` elsewhere, with `Desktop.open` as fallback.
- **Update requests:** Gson omits nulls, and the server treats a missing field as "unchanged" — dialogs send only changed fields (`EmployeeDialog`, `CustomerDialog`).

## Screens → spec

| Screen (class) | Spec | Routes |
|---|---|---|
| `LoginFrame`, `ChangePasswordDialog` | req 2 | `LOGIN` (ALREADY_LOGGED_IN shown in a dialog), `PASSWORD_CHANGE` forced when `passwordExpired`, `LOGOUT` |
| `admin.EmployeesPanel` / `EmployeeDialog` | req 3, 8 | `EMPLOYEE_*`, `BRANCH_LIST` |
| `admin.PasswordPolicyPanel` | req 3 | `PASSWORD_POLICY_GET/SET` |
| `inventory.InventoryPanel` | req 4, 5 | `INVENTORY_LIST`, `INVENTORY_RESTOCK`, event `INVENTORY_UPDATED` |
| `inventory.SalePanel` | req 5, 6 | `SALE_QUOTE`, `SALE_CREATE` (customer required), `CUSTOMER_LIST` |
| `customers.CustomersPanel` / `CustomerDialog` | req 6 | `CUSTOMER_LIST/CREATE/UPDATE`, event `CUSTOMER_UPDATED` |
| `reports.ReportsPanel` | req 7 | `REPORT_SALES`, `REPORT_EXPORT` |
| `chat.ChatPanel` | req 9 | `CHAT_*`, events `CHAT_*` |
| `admin.LogsPanel` | req 10 | `LOG_LIST`, `CHAT_TRANSCRIPT_LIST/GET` |

## Tests

`src/test/java` mirrors the main packages, no mocking library. `net.FakeServer` is a scripted server on port 0 (one client; `nextRequest()`, `ok`, `error`, `event`, `dropClient`). `ConnectionTest` covers request framing, out-of-order responses, errors, `SERVER_BUSY` without `requestId`, events, drops and the event bus backlog; `JsonCodecTest` decodes JSON in the exact shapes the server writes; `SessionTest` checks the tabs per role (permission sets copied from the server's `Role`); `ReportOpenerTest` and `LoginMessagesTest` cover the pure helpers. Swing screens themselves have no automated tests — check them by hand against a running server.
