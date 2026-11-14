# Clothing Store Network – Client

Swing desktop application for the employees and the administrator of the store network. It talks to the
server (`../store-server`) over TCP; all business rules live on the server.

## Build & run

Requires JDK 17+ and Maven. **Start the server first** (see `../store-server/README.md`).

Run these commands inside `store-client/`, the folder that contains `pom.xml`:

```
mvn package                               # compiles, runs the unit tests, builds target/store-client.jar
java -jar target/store-client.jar         # connects to localhost:5050
java -jar target/store-client.jar 192.168.1.20 5050    # another host / port
```

From an editor: open `store-client` as a Maven project and run `com.store.client.ClientMain`.

## Demo accounts

| User | Password | Role | Tabs |
|---|---|---|---|
| `admin` | `Admin1234` | ADMIN | Customers, Reports (all branches), Employees, Password Policy, Logs |
| `tlv.manager` (also `hfa.`, `jlm.`) | `Store1234` | SHIFT_MANAGER | Inventory (+ restock), Sell, Customers, Reports (own branch), Chat (+ join chats) |
| `tlv.cashier` | `Store1234` | CASHIER | Inventory, Sell, Customers, Chat |
| `tlv.sales` | `Store1234` | SALESPERSON | Inventory, Sell (quotes only), Customers, Chat |

## Things to try (open two or more clients)

1. **No double login:** log in as `tlv.cashier` twice — the second window is refused.
2. **Live inventory:** `tlv.cashier` and `tlv.manager` side by side; sell from one and watch the stock change in the other.
3. **Live customers:** edit a customer in Tel Aviv while `hfa.sales` is on the Customers tab.
4. **Customer types:** quote the same cart for a NEW, a RETURNING and a VIP customer; the discount and the track differ.
5. **Reports:** as `tlv.manager` or `admin`, Reports → *Open in Notepad* (TextEdit on macOS).
6. **Chat:** `hfa.sales` requests a chat with Tel Aviv. If everyone there is busy the request waits in the queue
   and is offered to the next free employee. `jlm.manager` can join from *Active chats*. When leaving, choose
   whether to save the transcript; the admin sees it under Logs → Chat transcripts.
7. **Forced logout:** as `admin`, change the role of a logged-in user — their window returns to the login screen.

## Structure

```
com.store.client
  ClientMain            entry point
  net/                  Connection (socket + reader thread), ServerApi, ClientEventBus, JsonCodec
  dto/                  mirror of the server protocol (Dtos + enums)
  app/                  Session, Screen (tab per permission), ReportOpener
  ui/                   LoginFrame, MainFrame, one panel per area (inventory, customers, reports, chat, admin)
```
