# CLAUDE.md – store-client

Guidance for Claude Code when working in `store-client/`. The repository overview, course spec mapping and environment notes are in the root `CLAUDE.md`.

## Status

**Not started.** This folder is reserved for the client application. Nothing here exists yet; the server (`../store-server/`) is complete and is the source of truth for the protocol.

## What this project must be

A Java desktop application (separate Maven project, Java 17+, Gson for JSON) that employees use to work with the store network. It contains **no business rules** — discounts, stock checks, permissions, the chat queue and logging all happen on the server. The client connects, sends requests, shows results and reacts to pushed events.

The GUI toolkit (Swing or JavaFX) has not been chosen yet — ask the user before starting.

## Screens required by the course spec

| Screen | Spec | Server routes |
|---|---|---|
| Login | req 2; show `ALREADY_LOGGED_IN` clearly (no double login) | `LOGIN`, `LOGOUT`, `PASSWORD_CHANGE` (offer it when `passwordExpired` is true) |
| Admin – employees & password policy | req 3, 8 | `EMPLOYEE_LIST/CREATE/UPDATE/DELETE`, `PASSWORD_POLICY_GET/SET`, `BRANCH_LIST` |
| Branch inventory – view, buy (restock), sell | req 4, 5 | `INVENTORY_LIST`, `INVENTORY_RESTOCK`, `SALE_QUOTE`, `SALE_CREATE` |
| Customers – full details incl. type and purchase track | req 6 | `CUSTOMER_LIST/CREATE/UPDATE` (each customer has `type` and `track`) |
| Reports – by branch / products / categories, daily, open in Notepad | req 7 | `REPORT_SALES`, `REPORT_EXPORT` |
| Chat – request, accept/decline, send, leave (save transcript?), manager join | req 9 | `CHAT_REQUEST/ACCEPT/DECLINE/SEND/LEAVE`, `CHAT_JOIN`, `CHAT_LIST_ACTIVE` |
| Logs viewer (admin) | req 10 | `LOG_LIST`, `CHAT_TRANSCRIPT_LIST`, `CHAT_TRANSCRIPT_GET` |

Show only what the logged-in user may do: the LOGIN response contains the employee, their branch and their `permissions`; enable/hide screens and buttons from that set (the server enforces it anyway and answers `FORBIDDEN`).

## Protocol essentials

Full details: `../store-server/README.md` and `../store-server/src/main/java/com/store/server/protocol/` (`RequestType`, `EventType`, `ErrorCode`, `dto/Dtos.java`).

- TCP to `localhost:5050` by default, UTF-8, **one JSON object per line**.
- Request: `{"kind":"REQUEST","requestId":"c-1","type":"...","payload":{...}}`. Response: same `requestId`, `status` `OK` (with `payload`) or `ERROR` (with `error.code` / `error.message`).
- Events arrive at any time on the same socket: `{"kind":"EVENT","event":"...","payload":{...}}` — `INVENTORY_UPDATED` (own branch), `CUSTOMER_UPDATED` (everyone), `CHAT_*`, `FORCED_LOGOUT`.
- The session is the connection: no token; log in once per socket. On `FORCED_LOGOUT` or a dropped connection, return to the login screen.
- Mirror the payload records from `Dtos.java` with the same field names. When the server protocol changes, update the client in the same change.

## Recommended structure

- **One connection class** owning the socket, a single **reader thread**, and a map `requestId → CompletableFuture<Response>`; `send(type, payload)` returns the future. The reader completes futures for RESPONSE lines and passes EVENT lines to registered listeners.
- **Never touch UI components from the reader thread** — hand events to the UI thread (`SwingUtilities.invokeLater` / `Platform.runLater`). Don't block the UI thread waiting for responses.
- Packages along the lines of `net` (connection, codec), `dto` (mirrored records), `ui` (one screen per area), `app` (entry point, session state).
- Reports: save the text from `REPORT_EXPORT` to a local `.txt` file and open it with `new ProcessBuilder("notepad.exe", file.toString()).start()` (req 7c).
- Unit tests for the connection layer (e.g. against a small fake server on port 0) and for any client-side formatting.

## Running against the server

Start the server first (see `../store-server/CLAUDE.md`). Demo users: `admin` / `Admin1234`; `tlv.manager`, `tlv.cashier`, `tlv.sales` (also `hfa.*`, `jlm.*`) / `Store1234`. Log in from two client windows to see live inventory/customer updates and the chat flow.
