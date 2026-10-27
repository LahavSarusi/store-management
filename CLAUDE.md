# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository

Course final project ("פרוייקט סיכום"): a management system for a chain of clothing stores, built as a pure-Java client-server application. Two independent Maven projects that share **only the JSON-over-TCP protocol** — no shared code module:

| Folder | What | Status | Details |
|---|---|---|---|
| `store-server/` | TCP server: all business rules, data, permissions, chat queue, logs | Done, 68 tests | `store-server/CLAUDE.md`, `store-server/README.md` |
| `store-client/` | Desktop GUI used by employees and the admin | Not started | `store-client/CLAUDE.md` |

The protocol is defined by the server: `store-server/src/main/java/com/store/server/protocol/` (`RequestType`, `EventType`, `ErrorCode`, `dto/Dtos.java`). Any protocol change must be applied to both projects.

## Course requirements (from the spec PDF) → where they live

| # | Requirement | Server | Client |
|---|---|---|---|
| 1 | Client-server system | TCP server | connects to it |
| 2 | Login screen with authentication; no double login | `LOGIN`, `SessionManager` | login screen |
| 3 | Admin screen: employee accounts, password policy | `EMPLOYEE_*`, `PASSWORD_POLICY_*` | admin screen |
| 4 | Data shown by user type and branch | permissions + branch from session | hide/disable by `permissions` |
| 5 | Branch inventory: view, buy, sell; live for the branch | `INVENTORY_*`, `SALE_*`, `INVENTORY_UPDATED` | inventory screen |
| 6 | Customers (name, ID, phone, type New/Returning/VIP); **one class per type**, each with its own promotion; live for all | `model/customer/*`, `CUSTOMER_*`, `CUSTOMER_UPDATED` | customers screen |
| 7 | Reports by branch / products / categories, daily; open in **Notepad** | `REPORT_SALES`, `REPORT_EXPORT` (.txt) | report screen, launch notepad |
| 8 | Employees: name, ID, phone, bank account, branch, employee number, role | `Employee`, `EmployeeService` | admin screen |
| 9 | Inter-branch 1-on-1 chat, shift manager can join, queue for a free employee, saved pending requests + notification; **must use a design pattern** | `chat/ChatQueueManager` (Observer, Command, Mediator, State) | chat screen |
| 10 | Logs by action type: employee/customer registration, buy/sell, chat details + optional transcript | `logging/*`, `LOG_LIST`, `CHAT_TRANSCRIPT_*` | admin log viewer |

The spec also requires clean, well-documented code and unit tests in both projects.

## Environment (this machine)

- JDK 25 (Eclipse Adoptium) at `C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot`; projects target Java 17. **Maven is not installed** — use IntelliJ's bundled Maven or install it; set `JAVA_HOME` if `mvn` complains.
- The repo path contains Hebrew characters (`שולחן העבודה`). Passing it as an argument to `java.exe` from Git Bash gets mangled ("Unable to access jarfile") — `cd` into the folder and use relative paths, or copy the jar elsewhere.
- `.gitignore` (root) excludes `target/`, `dependency-reduced-pom.xml`, the server's runtime `data/`, `logs/`, `reports/`, and IDE folders — for both projects.
