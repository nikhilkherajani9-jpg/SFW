# SFW Footwear Wholesale Management System

![Java 21](https://img.shields.io/badge/Java-21-orange.svg)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue.svg)
![SQLite](https://img.shields.io/badge/Database-SQLite-lightgrey.svg)
![Build](https://img.shields.io/badge/Build-Maven-success.svg)

A high-performance, offline-first desktop application engineered specifically for managing footwear wholesale operations. Designed to run portably from USB Hard Disk Drives (HDDs), this application handles intensive database operations locally without requiring a constant internet connection, while enforcing a secure, hardware-bound licensing model.

---

## 🚀 Key Features

* **Advanced License Management (Anti-Piracy):**
  * **Hardware Binding:** Product keys are permanently bound to a PC's unique Motherboard/System UUID via Firebase Realtime Database.
  * **Offline Grace Period:** Users can operate the software completely offline for up to **4 days (96 hours)**. A secure, cryptographically hashed local `license.dat` file tracks the last online verification.
  * **Instant Revocation:** Administrators can revoke product keys from the Firebase console, which the app will detect via periodic background syncs or upon the next launch, immediately blocking access.

* **Wholesale & Inventory Operations:**
  * **Stock Management:** Add, update, and track footwear stock comprehensively.
  * **Lorry Receipts (LR):** Track dispatches, logistics, and bulk transfers seamlessly via the dedicated LR Entry Tab.
  * **Godown (GD) Transfers:** Perform bulk cart transfers between godowns/warehouses.

* **Performance Engineered for USB HDDs:**
  * **SQLite WAL Mode:** Optimized Write-Ahead Logging ensures sequential disk writes instead of random I/O, preventing freezes on mechanical USB drives.
  * **Multithreading:** Heavy database operations, Excel exports, and license verification are completely decoupled from the JavaFX Application Thread.
  * **Connection Pooling:** Dedicated read-only connections for background tasks ensure the UI never hangs during concurrent reads and writes.

---

## 🛠️ Technology Stack

* **Language:** Java 21
* **UI Framework:** JavaFX 21.0.4
* **Database:** SQLite (JDBC)
* **License Backend:** Firebase Realtime Database (REST API)
* **Build Tool:** Apache Maven 3+

---

## 💻 Getting Started

### Prerequisites
* Java Development Kit (JDK) 21 or higher.
* Apache Maven 3.6+.
* A Firebase Realtime Database (for licensing).

### Build and Run
1. **Clone the repository:**
   ```bash
   git clone https://github.com/nikhilkherajani9-jpg/SFW.git
   cd SFW/footwear-wholesale
   ```

2. **Compile and Run:**
   ```bash
   mvn clean compile javafx:run
   ```

3. **Package as a standalone JAR:**
   ```bash
   mvn clean package
   ```
   The compiled artifact will be located in the `/target` directory.

---

## 🔒 Firebase Security Rules (Licensing)

To ensure the licensing system is secure, your Firebase Realtime Database MUST be configured with the following rules:

```json
{
  "rules": {
    "licenses": {
      "$productKey": {
        ".read": "true",
        ".write": "(!data.exists()) || (data.child('hardwareId').val() == null) || (data.child('hardwareId').val() == '')"
      }
    }
  }
}
```
This ensures a customer can only bind their hardware ID to a fresh product key, preventing them from overwriting someone else's activated key.

---

## 📝 License
Proprietary software. Unauthorized copying, modification, distribution, or execution of this software via USB drives or otherwise is strictly prohibited without a valid Product Key.
