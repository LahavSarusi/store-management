# CLAUDE.md – store-server

Guidance for Claude Code when working in `store-server/`. The repository overview, course spec mapping and environment notes are in the root `CLAUDE.md`.

## Project

The server: pure Java 17 TCP sockets, Gson, JUnit 5, Maven, package `com.store.server`. It owns all business rules and data; the client (`../store-client/`) only talks to it through the JSON protocol. `README.md` here is the human-facing protocol summary and demo-account list.

## Commands

Run from `store-server/` (see root `CLAUDE.md` for the JDK path and the missing Maven).

```
mvn package                                      # compile + all tests + target/store-server.jar (shaded, runnable)
mvn test                                         # all tests
mvn test -Dtest=ChatQueueManagerTest             # one class
mvn test -Dtest=ChatQueueManagerTest#chatMustBeInterBranch   # one method
java -jar target/store-server.jar [port=5050] [baseDir=.] [maxClients=100]
```

Running the server writes `data/`, `logs/`, `reports/` under `baseDir` (git-ignored); on first start `DataSeeder` creates demo branches, staff, stock and customers. Use a scratch directory for manual runs if you need clean data. No linter/formatter is configured.

## Architecture

Layering: `net → controller → service → dao`. Controllers never touch DAOs; services never touch sockets — they publish through `event.EventBus`.

- **Composition root:** `ServerContext` builds and wires everything by hand (no static singletons, no DI framework) and registers each `Controller`'s routes with the `RequestDispatcher`. New services/controllers must be wired here. It also implements `SessionLifecycle` (login/logout hooks → chat presence + audit log).
- **Request path:** `StoreServer` (accept loop, cached pool, connection cap → `SERVER_BUSY`) → one `ClientHandler` thread per socket reading newline-delimited JSON → `RequestDispatcher` checks login + `AuthorizationService` permission, then calls the route's `RequestHandler` (Command pattern). `DomainException(ErrorCode, msg)` thrown anywhere becomes an error response; other exceptions become `INTERNAL`.
- **Adding a route:** add to `protocol.RequestType`, add payload records to `protocol.dto.Dtos` (field names = JSON keys; the client mirrors these — update `../store-client` too), map it to a `Permission` in `AuthorizationService` (unmapped = any logged-in user), register a handler in the relevant controller.
- **Identity & branch scoping:** the logged-in `Employee` is bound to the connection (`ClientHandler.login`); there is no token. Branch-scoped operations always take the branch from the session, never from the payload. `Role` → `EnumSet<Permission>`; ADMIN has no branch.
- **Single login:** `SessionManager.register` is an atomic `putIfAbsent` on the lowercased username; `unregister` only removes if the same connection owns the entry.
- **Post-response work:** `ClientHandler.login` defers `SessionLifecycle.onLogin` until after the LOGIN response is written, so the client sees its login result before any pushed event (e.g. `CHAT_PENDING_REQUEST`).
- **Live sync:** `EventBus` has user / branch / all scopes (`SessionEventBus` fans out over `SessionManager`). Inventory changes go to the branch, customer changes to everyone.
- **Inventory concurrency:** every stock change must go through `InventoryService.modify(branchId, fn)` — per-branch lock, load → mutate → save, then publishes `INVENTORY_UPDATED`. If `fn` throws, nothing is saved or published. `SalesService.sell` does stock check + pricing + decrement + sale record inside one `modify` call.
- **Customer polymorphism (course requirement: one class per customer type):** `Customer.purchase()` is a final template method; `NewCustomer`/`ReturningCustomer`/`VipCustomer` override `calculateDiscount`/`extraBenefits`. Always create or change type via `CustomerFactory` (`create`, `changeType`, `upgradeIfEligible`) — changing type means replacing the instance. Gson (de)serializes the hierarchy through `CustomerJsonAdapter` keyed on `"type"`, registered in `JsonCodec`; it also writes an output-only `"track"` (`getTrackDescription()`) so clients can show each type's purchasing track (req 6b).
- **Chat (course requirement: design pattern for queue/call reception):** all logic is in `chat.ChatQueueManager`, every method `synchronized`. Observer (`AvailabilityListener` fires on transition to AVAILABLE and offers the next queued `ChatRequest`), Command (`ChatRequest` queued FIFO per target branch, persisted via `PendingChatDao`), Mediator (`ChatSession`), State (`AvailabilityStatus`). An employee holds at most one outstanding offer (`offered`/`offeredTo`); offers are requeued to the front on decline, disconnect, or when the holder enters another chat. Chats must be inter-branch; only `SUPERVISE_CHAT` (shift manager) can join. Messages are relayed through the server (user's decision).
- **Employees:** besides login data, an `Employee` has `nationalId` (Israeli ID, unique across employees), `bankAccount`, and a server-assigned sequential `employeeNumber` (`EmployeeService.FIRST_EMPLOYEE_NUMBER` = 1001) — all required by the course spec (req 8a).
- **Persistence:** JSON files under `data/` via DAO interfaces in `dao/`; implementations in `dao/json/`. `JsonCollectionDao` keeps a keyed map in memory and rewrites the whole file atomically (temp file + move) on each write. `JsonInventoryDao` returns copies — mutate then `saveBranch`. Other DAOs return live objects.
- **JSON:** use `JsonCodec.gson()` for everything (wire + files). Don't put `java.time` types in serialized objects (no adapters registered; dates travel as ISO strings or epoch millis). `JsonCodec.decodeRequest` treats `"payload": null` as absent.
- **Logging:** `FileActionLogger` writes `timestamp | ACTION | actor | details` to `logs/actions.log` on a single background thread; chat transcripts go to `logs/chats/<sessionId>.txt` only when the leaver asks to save. Call `close()` to flush (tests on `@TempDir` must close `ServerContext`, or Windows cleanup can fail). Reads (`query`, `listTranscripts`, `readTranscript`) also run on that writer thread, so they never see a half-written line; they back the admin-only `LOG_LIST` / `CHAT_TRANSCRIPT_*` routes in `LogController`. `readTranscript` only accepts ids matching `CHAT-[0-9a-f]{8}` (path-traversal guard).
- **Reports:** `ReportService` aggregates `Sale` records by branch/product/category, filtered by lists of SKUs/categories (empty = all) and a date range; `from == to` is a daily report (`ReportCriteria.daily`, `SalesReport.day`). Net revenue spreads each sale's discount over its lines proportionally. `TxtReportExporter` writes CRLF text (Notepad). Non-admins are forced to their own branch in `ReportController`.

## Tests

`src/test/java` mirrors the main packages. No mocking library — use the fakes in `TestSupport` (`RecordingEventBus`, `RecordingLogger`, `InMemoryPendingChatDao`, `employee(...)`) and real JSON DAOs on `@TempDir`. `ServerIntegrationTest` starts a real server on port 0 and talks to it over sockets with a minimal client; seed passwords come from `DataSeeder.STAFF_PASSWORD`. Usernames must be 3–30 chars (`Validation.isValidUsername`), and customer/employee ID numbers must pass the Israeli ID checksum (`Validation.isValidIsraeliId`) — e.g. `314159260`, `271828188`, `161803390`, `123456782`.
