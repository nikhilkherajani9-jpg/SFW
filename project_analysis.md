# SFW Footwear Wholesale — Project Analysis Report

## What Is This Project?

**SFW** stands for **SFW Footwear Wholesale**. This is a **fully offline, locally-run business management system** for a footwear wholesale operation. It is designed specifically to run without any internet connection — all data lives in a local SQLite database file (`footwear.db`). The system is built to run from a USB hard drive, meaning it can be carried around and plugged into any Windows PC.

The project is split into **two sub-applications** that work together:

---

## Sub-Application 1: `footwear-wholesale` — The Desktop App

### What It Is
A **Java 21 + JavaFX desktop application** that serves as the **primary management system** and single source of truth. It is the data-entry and operations control hub.

### Technology Stack
| Component | Technology |
|---|---|
| Language | Java 21 |
| UI Framework | JavaFX 21.0.4 |
| Database | SQLite 3.45.3 via `sqlite-jdbc` |
| QR Code | Google ZXing 3.5.3 |
| Excel Import | Apache POI 5.2.5 (legacy `.xls`) |
| Build | Maven |
| HTTP Server | JDK built-in `com.sun.net.httpserver` |
| Testing | JUnit 5 + TestFX |

### Architecture
The codebase is cleanly layered:

```
com.sfw.wholesale
├── App.java              ← Entry point, splash screen, startup sequence
├── MainWindow.java       ← Main 5-tab window + menu bar
├── database/
│   ├── DatabaseManager   ← Singleton SQLite connection, schema creation, migrations
│   └── DataCache         ← In-memory cache of all data (LRs, stock, transports)
├── model/                ← JavaFX Observable POJOs (LrEntry, LrItem, GdTransfer, StockRow, LedgerEntry, StockDeduction)
├── service/              ← All business logic
│   ├── LrService         ← LR save/load, stock delta on edit
│   ├── StockService      ← Stock add/subtract with FIFO, ledger logging
│   ├── GdTransferService ← Warehouse-to-warehouse transfers, shop dispatch
│   ├── SyncService       ← HTTP server, QR code generation, JSON payload builder
│   ├── ExportImportService ← CSV export/import for backup
│   └── LegacyImportService ← Apache POI import from old Excel files
├── ui/
│   ├── tab/              ← Five main screens (LR Entry, All LRs, Stock, GD Transfers, QR Sync)
│   └── component/        ← Shared UI widgets (AutoSuggest, ConfirmDialog, StatusBadge, LocationMappingDialog)
└── util/
    └── NetworkUtil       ← Network interface detection for WiFi sync
```

### The 5 Main Screens (Tabs)

1. **📋 LR Entry Tab** — The primary data entry screen. Staff enter incoming Lorry Receipts (LR) which represent shipments arriving at warehouses. Each LR has a header (LR number, date, transport company) and multiple line items (product, cartons, pairs-per-carton, location). Saving an LR automatically updates stock.

2. **📄 All LRs Tab** — A searchable/filterable list view of all recorded LRs. Clicking an entry loads it back into the LR Entry tab for editing. Stock is adjusted on edit using a **delta method** — only the *difference* is applied to stock, not a full reverse-and-reapply.

3. **📦 Stock Tab** — Shows current warehouse inventory organized by product and location (G1–G5, RK2). Supports manual stock adjustments (for corrections), adding opening balances, and deleting stock entries. Each product-location combination has a full ledger history (in/out/balance) accessible via drill-down.

4. **🔄 GD Transfers Tab** — Manages internal stock movements between warehouses (e.g., G4 → G2) or from a warehouse to "Shop" (retail dispatch). Two-stage workflow: first **plan** the transfer, then **mark as Done** to atomically execute the stock movement. Shop transfers only subtract from source — no stock row is ever created for "Shop."

5. **📡 QR Sync Tab** — Generates a QR code that the mobile app scans to pull all data over local WiFi. Also supports USB sync via ADB port forwarding (127.0.0.1:8742). Has options to sync Stock only, LRs only, or both. Includes file-based sync: generates an AES-256-encrypted, gzipped `.sfwdata` file that can be shared via WhatsApp.

### Key Business Logic

**Stock Management (FIFO):**
- Stock is tracked in **FIFO batches** — each batch remembers its LR source and receive date
- When deducting stock for a transfer, the oldest batch is consumed first
- A `stock_ledger` table maintains a full audit trail of every in/out movement

**GD Transfer Rules (strictly enforced):**
- Stock validation happens **before** any transaction begins
- Always subtract from source first — if that fails, destination is never touched
- "Shop" is a dispatch target only — never a stock location
- All movements are logged to the ledger with a typed transaction (LR_ENTRY, GD_TRANSFER, MANUAL_ADJ, DISPATCH)

**LR Delta Editing:**
- When an existing LR is edited, the system computes the *delta* between old and new items
- Only the difference in stock quantities is applied — this prevents errors if stock has already been partially transferred from the original LR

**Database Engineering (optimized for USB HDD):**
- WAL journal mode for sequential writes instead of random
- `synchronous=NORMAL` — safe with WAL, far fewer `fsync` calls
- 16 MB in-memory page cache
- Temp tables stay in RAM
- VACUUM is **never** called on close — only on explicit user action
- Schema migrations are applied incrementally on startup

---

## Sub-Application 2: `sfw_mobile` — The Android Companion App

### What It Is
A **Flutter (Dart) Android mobile app** that acts as a **read-only view** of the desktop data. It is designed to be synced from the desktop app and used by salespeople or managers on the go to check stock levels and LR records.

### Technology Stack
| Component | Technology |
|---|---|
| Framework | Flutter (Dart, SDK ^3.12.2) |
| Database | SQLite via `sqflite` |
| QR Scanning | `mobile_scanner` |
| QR Display | `qr_flutter` |
| HTTP | `http` package |
| Encryption | `encrypt` (AES-256-CBC) |
| Storage | `path_provider`, `shared_preferences` |
| Sharing | `share_plus` |

### Architecture

```
sfw_mobile/lib/
├── main.dart              ← App entry, MainScreen (Stock + LR tabs), StockLedgerScreen, QrScannerScreen
├── models.dart            ← Plain Dart POJOs: StockRow, LrEntry, LrItem, StockLedger
├── database_helper.dart   ← SQLite singleton, wipeAndLoadPayload(), exportToJson()
├── file_sync_service.dart ← AES encrypt/decrypt + gzip for .sfwdata files, WhatsApp folder scan
├── qr_sync_server.dart    ← TCP server for phone-to-phone data transfer
├── qr_sync_client.dart    ← TCP client for receiving from another phone
├── qr_share_screen.dart   ← UI for phone-to-phone QR sync (send or receive mode)
├── lr_detail_screen.dart  ← Drill-down LR detail: header + line items
└── device_utils.dart      ← Device ID and name helpers
```

### Key Features

**Two Screens:**
- **Stock Tab** — Lists all stock rows (product, location, cartons, pairs). Tapping a row shows the full stock ledger history for that product+location in a scrollable data table.
- **LRs Tab** — Lists all LR records with transport company, status, total cartons, shop cartons, and total amount. Tapping shows a detail screen with all line items.

**Four Sync Modes (all offline):**

| Mode | How it works |
|---|---|
| **WiFi QR Sync** | Scan the QR code displayed by desktop app → connects to desktop's HTTP server (port 8742) → downloads gzipped JSON payload |
| **USB Sync** | ADB port-forward `adb forward tcp:8742 tcp:8742` → same HTTP endpoint, localhost |
| **Phone-to-Phone** | One phone runs a TCP server → displays QR → other phone scans and downloads via raw TCP socket |
| **File Sync (.sfwdata)** | Desktop exports AES-256-CBC + gzip encrypted file → shared via WhatsApp → mobile auto-detects in WhatsApp Documents folder on startup |

**Auto-sync on Startup:**
- On every app launch, the app scans the WhatsApp Documents directory for `.sfwdata` files
- If a file newer than the last sync timestamp is found, it is automatically decrypted, decompressed, and loaded
- The `exported_at` timestamp inside the file is compared against `last_synced_at` in SharedPreferences

**Encryption Protocol:**
- AES-256-CBC with PKCS7 padding
- Key: `SfwMobileAppSyncDataKey123456789` (32 bytes, hardcoded)
- IV: `SfwMobileAppIV12` (16 bytes, hardcoded)
- Same keys used on both desktop (Java `javax.crypto`) and mobile (`encrypt` Dart package)

---

## The Data Model

The system revolves around these core entities:

| Entity | Description |
|---|---|
| `lr_entries` | LR header — number, date, transport company |
| `lr_items` | LR line items — product, cartons, shop cartons, PPC, location |
| `stock` | Current warehouse inventory — product, location, cartons, PPC, LR source, receive date (FIFO batches) |
| `stock_ledger` | Full audit trail of every stock movement (inward, outward, running balance) |
| `gd_transfers` | Internal transfer plans and their execution status |
| `transport_companies` | Reference list of transport company names (auto-populated from LR entries) |

### Locations Known to the System
- **Warehouses:** G1, G2, G3, G4, G5, RK2
- **Shop:** Retail dispatch target — stock is consumed here, never stored

---

## Domain Context

The business domain is a **footwear wholesale operation in India**:
- Goods arrive via **lorries** (trucks) documented with **LR (Lorry Receipt)** numbers
- Products are measured in **cartons** and **pairs per carton (PPC)**
- Stock is held across **multiple warehouses** (G1–G5, RK2)
- Some cartons go directly to a retail **Shop** at time of receipt
- Goods are internally moved between warehouses using **GD Transfers** (Goods Dispatch transfers)
- The currency shown in the mobile app is **₹ (Indian Rupee)**

---

## Deployment Model

This system is explicitly designed for **fully air-gapped, offline use**:
- The desktop app + `footwear.db` live on a **USB hard drive** next to each other
- No internet, no server, no cloud
- The QR sync over local WiFi hotspot requires no router — Windows Mobile Hotspot is sufficient
- The CSV export/import provides a backup/restore mechanism
- The `.sfwdata` file + WhatsApp provides an informal "courier" sync mechanism

---

## Summary

This is a thoughtfully designed, **production-ready internal business tool** for a footwear wholesale company. It is a **two-tier offline system**: a full-featured JavaFX desktop application for data entry and operations, paired with a Flutter Android companion app for read-only field access. The entire architecture is built around the constraint of having **zero internet dependency**, with multiple creative sync mechanisms (QR/WiFi, USB, phone-to-phone TCP, WhatsApp file) to bridge the air gap when needed.
