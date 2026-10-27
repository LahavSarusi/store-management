# Clothing Store Network – Server

Pure-Java TCP server: concurrent clients, single-session login, role/branch access control, live sync,
polymorphic customer pricing, sales reports (.txt export), inter-branch chat with a queue, audit logging.

## Build & run

Requires JDK 17+ and Maven.

The server's entry point is [`ServerMain`](src/main/java/com/store/server/ServerMain.java). It depends on the
rest of the project and on Gson, so always run it through the project (from an editor or as the built jar) —
not as a single file.

### Option A – from an editor

1. Open the `store-server` folder (the one containing `pom.xml`) in VS Code (with the *Extension Pack for Java*)
   or IntelliJ IDEA, and wait until it finishes loading the project and downloading its libraries.
2. Open `src/main/java/com/store/server/ServerMain.java` and click **Run** on the `main` method.

### Option B – from the command line

The `target/` folder (with the jar) is not stored in git, so after cloning you must build once before running.
Run these commands **inside `store-server/`**, the folder that contains `pom.xml`:

```
mvn package                                   # compiles, runs all unit tests, builds target/store-server.jar
java -jar target/store-server.jar             # starts the server
```

Wait for `BUILD SUCCESS` before starting the jar. Run `mvn package` again after changing the code — the jar does
not update by itself.

### Checking that it runs

When the server is ready it prints:

```
INFO: Store server listening on port 5050
```

Keep that window open while you use the server; stop it with `Ctrl+C` (or the editor's stop button).

The jar accepts three optional arguments, in this order:

| Argument | Default | Meaning |
|---|---|---|
| `port` | `5050` | TCP port clients connect to |
| `baseDir` | `.` | folder where `data/`, `logs/` and `reports/` are created |
| `maxClients` | `100` | connections accepted at the same time; extra clients get `SERVER_BUSY` |

Example: `java -jar target/store-server.jar 6000 . 50`

On first start the server creates demo data under `baseDir`:

| User | Password | Role |
|---|---|---|
| `admin` | `Admin1234` | ADMIN |
| `tlv.manager` / `tlv.cashier` / `tlv.sales` (also `hfa.*`, `jlm.*`) | `Store1234` | SHIFT_MANAGER / CASHIER / SALESPERSON |

Runtime folders: `data/` (JSON "database"), `logs/actions.log`, `logs/chats/<session>.txt`, `reports/*.txt`.

## Protocol

TCP, UTF-8, **one JSON object per line**. Three message kinds share the socket:

```jsonc
// client -> server
{"kind":"REQUEST","requestId":"c-1","type":"LOGIN","payload":{"username":"tlv.cashier","password":"Store1234"}}
// server -> client, matched by requestId
{"kind":"RESPONSE","requestId":"c-1","status":"OK","payload":{...}}
{"kind":"RESPONSE","requestId":"c-1","status":"ERROR","error":{"code":"ALREADY_LOGGED_IN","message":"..."}}
// server -> client, unsolicited
{"kind":"EVENT","event":"INVENTORY_UPDATED","payload":{...},"timestamp":"2026-10-27T10:15:00Z"}
```

The client should run one reader thread that hands RESPONSEs to the waiting call (by `requestId`)
and EVENTs to UI listeners.

- Routes: [`RequestType`](src/main/java/com/store/server/protocol/RequestType.java)
- Payload shapes (mirror these in the client): [`Dtos`](src/main/java/com/store/server/protocol/dto/Dtos.java)
- Events: [`EventType`](src/main/java/com/store/server/protocol/EventType.java), errors: [`ErrorCode`](src/main/java/com/store/server/protocol/ErrorCode.java)
- Who may call what: [`AuthorizationService`](src/main/java/com/store/server/service/AuthorizationService.java) + [`Role`](src/main/java/com/store/server/model/Role.java)

Branch-scoped routes (inventory, sales, shift-manager reports) always use the caller's own branch from the session.

## Architecture

```
net (sockets, sessions, dispatch) -> controller (thin, per route group) -> service (rules) -> dao (JSON files)
                                                      \-> event.EventBus (live push)   \-> logging.ActionLogger
```

| Concern | Pattern | Where |
|---|---|---|
| Customer purchasing route | Polymorphism + Template Method, Factory | `model/customer/*` – `Customer.purchase()` is fixed, each subclass supplies its discount/benefits; `CustomerFactory` creates and auto-upgrades (NEW → RETURNING → VIP) |
| Request routing | Command | `RequestDispatcher` + `RequestHandler` per route |
| Live sync | Observer / pub-sub | `EventBus` → user / branch / all scopes |
| Chat queue & reception | Observer + Command + Mediator + State | `ChatQueueManager`, `ChatRequest`, `ChatSession`, `AvailabilityStatus` |
| Single login | Atomic `putIfAbsent` | `SessionManager` |
| Wiring | Composition root (no static singletons) | `ServerContext` |

### Employees
`EMPLOYEE_CREATE` needs `username, password, nationalId, fullName, bankAccount, role` (+ `branchId` unless ADMIN; `phone` optional).
The ID number must pass the Israeli check digit and be unique; the server assigns a sequential `employeeNumber` (from 1001).

### Reports
`REPORT_SALES` / `REPORT_EXPORT` payload: `{groupBy: BRANCH|PRODUCT|CATEGORY, branchId?, skus?: [...], categories?: [...], date? | from?/to?}`.
`date` gives a daily report; `from`/`to` an inclusive range. Shift managers are limited to their own branch.

### Logs (admin)
`LOG_LIST {type?, from?, to?, limit?}` returns action-log entries, newest first.
`CHAT_TRANSCRIPT_LIST` lists saved chats; `CHAT_TRANSCRIPT_GET {sessionId}` returns one transcript.

### Customers
Each customer in JSON carries `type` and `track` (a description of that type's purchasing track).

### Chat flow
1. `CHAT_REQUEST {targetBranchId}` → connected right away if someone there is free, otherwise `QUEUED` (FIFO per branch, persisted in `data/pending_chats.json`).
2. When an employee of that branch becomes free (logs in / ends a chat) they get `CHAT_PENDING_REQUEST`.
3. `CHAT_ACCEPT {requestId}` starts the chat (`CHAT_STARTED` to both), or `CHAT_DECLINE` passes it to a colleague.
4. Shift managers can `CHAT_LIST_ACTIVE` and `CHAT_JOIN` any chat.
5. `CHAT_LEAVE {sessionId, saveTranscript}` – metadata is always logged; the transcript only when requested.
