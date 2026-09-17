# SFW Footwear Wholesale Management System

This project is a comprehensive wholesale management solution tailored for the footwear industry. It features a multi-platform architecture consisting of a desktop administration application and a mobile companion app.

## Project Structure

The repository is divided into the following main components:

### 1. Footwear Wholesale (Desktop Application)
Located in the `footwear-wholesale/` directory, this is the core desktop application used for robust administration and inventory management.

**Key Technologies:**
- Java 21
- JavaFX (Desktop UI framework)
- SQLite (Database)
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
