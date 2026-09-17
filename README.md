# SFW Footwear Wholesale Management System

This project is a comprehensive wholesale management solution tailored for the footwear industry. It features a multi-platform architecture consisting of a desktop administration application and a mobile companion app.

## Project Structure

The repository is divided into the following main components:

### 1. Footwear Wholesale (Desktop Application)
Located in the `footwear-wholesale/` directory, this is a high-performance, offline-first desktop application engineered for managing footwear wholesale operations directly from USB Hard Disk Drives (HDDs).

**Key Features (Desktop):**
* **Advanced License Management:** Hardware-bound product keys with a **4-day offline grace period**, cryptographically secured. Realtime revocation via Firebase.
* **Wholesale Operations:** Manage stock ledgers, bulk Lorry Receipts (LR), and Godown (GD) transfers.
* **HDD Performance:** Optimized SQLite WAL mode, in-memory temp tables, and heavy multithreading decoupled from the UI thread to prevent freezing on mechanical drives.

**Key Technologies:**
- Java 21
- JavaFX 21.0.4 (Desktop UI framework)
- SQLite (Database)
- Firebase Realtime Database (Licensing Backend)
- Maven (Build tool)
- ZXing (Barcode/QR code generation)

### 2. SFW Mobile (Mobile Application)
Located in the `sfw_mobile/` directory, this is the mobile companion application designed for on-the-go access, quick scanning, and easy management.

**Key Technologies:**
- Flutter (Cross-platform mobile UI framework)
- Dart
- SQLite (`sqflite` for local storage)
- `mobile_scanner` (QR/Barcode scanning)

## Getting Started

### Desktop Application
To build and run the desktop application, navigate to the `footwear-wholesale/` directory. You can use the provided `run.bat` script or run it manually using Maven.

**Using the batch script (Windows):**
```bash
cd footwear-wholesale
run.bat
```

**Using Maven:**
```bash
cd footwear-wholesale
mvn clean javafx:run
```

### Mobile Application
To build and run the mobile application, navigate to the `sfw_mobile/` directory. You will need the Flutter SDK installed.
```bash
cd sfw_mobile
flutter pub get
flutter run
```
