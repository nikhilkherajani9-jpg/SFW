# SFW Footwear Wholesale — Build & Run Instructions

## Requirements

| Tool | Version | Notes |
|---|---|---|
| **BellSoft Liberica JDK 21 Full** | 21 LTS | **Required** — includes JavaFX and jmods for packaging |
| **Apache Maven** | 3.9+ | For building |
| **Windows 10** | 10 or 11 | App runs on Windows only |

### Why Liberica JDK Full?
Standard OpenJDK 21 does NOT include JavaFX. BellSoft Liberica JDK **Full** edition bundles
JavaFX 21, making jlink and jpackage work without a separate JavaFX SDK download.

**Download Liberica JDK 21 Full (Windows x64 .msi):**
https://bell-sw.com/pages/downloads/#jdk-21-lts
> Select: JDK 21 LTS → Windows → x86 64-bit → Full JDK (.msi)

Install to default path: `C:\Program Files\BellSoft\LibericaJDK-21-Full\`

**Download Maven:**
https://maven.apache.org/download.cgi
> Unzip and add `bin\` to your PATH.

---

## Logo Setup

Before building, place your SFW logo in `src\main\resources\icons\`:
- `sfw-logo.png` — used by JavaFX at runtime (window title bar icon, splash screen)
- `sfw-logo.ico` — used by jpackage for the Windows taskbar icon and Start Menu shortcut

To convert your `sfw-logo.jpg`:
1. Open [CloudConvert](https://cloudconvert.com) or use Paint.NET / GIMP
2. Convert `.jpg` → `.png` (recommended: 256×256 px, transparent background if possible)
3. Convert `.jpg` → `.ico` (256×256 px, multi-resolution ICO)
4. Place both files in `src\main\resources\icons\`

---

## Development (Testing & Running)

```bat
cd c:\SFW\footwear-wholesale
run.bat
```

Or manually:
```bat
mvn javafx:run
```

The database file (`footwear.db`) is created in the folder where the app JAR is located.
During development (`mvn javafx:run`), this is typically your project root or `target\` directory.

---

## Loading Sample Data

On first launch, the app auto-creates the database schema and the MANUAL-ADJ dummy LR.

To load the sample/test data (optional but recommended for testing):
1. Open the database with [DB Browser for SQLite](https://sqlitebrowser.org/)
2. Open `footwear.db`
3. Click **Execute SQL** tab
4. Paste the contents of `src\main\resources\sample-data\seed.sql`
5. Click **Run**

Alternatively, the seed data can be loaded programmatically — see `DatabaseManager.seedMandatoryData()`.

---

## Building the Self-Contained Bundle (for USB HDD Deployment)

```bat
cd c:\SFW\footwear-wholesale
package.bat
```

If Liberica JDK is installed to a different path, edit `package.bat` and change:
```bat
set JAVA_HOME=C:\Program Files\BellSoft\LibericaJDK-21-Full
```

The script runs 4 steps:
1. `mvn clean package` — builds the JAR + collects all dependency JARs into `target\libs\`
2. `jlink` — creates a minimal JRE (no JDK installation needed on the target machine)
3. `jpackage` — wraps everything into `target\SFW-Wholesale\`
4. Prints deployment instructions

---

## Deploying to USB HDD

1. After `package.bat` completes, copy the **entire** `target\SFW-Wholesale\` folder to your USB HDD.
2. To launch: double-click `SFW-Wholesale\SFW-Wholesale.exe` from the USB HDD.
3. On **first launch**, `footwear.db` is created in the same folder as the `.exe` — this keeps the database on the USB HDD alongside the app.

> **Important:** Always use the app from the USB HDD. Do not move `footwear.db` separately from the `SFW-Wholesale\` folder.

---

## Backup and Restore

### Backup (manual, on demand)
- Go to **File → Export Backup (CSV)...**
- Choose a destination folder (e.g., a local drive or another USB key)
- 5 CSV files are created in a timestamped `SFW_Export_*` folder

### Restore (emergency only)
- If `footwear.db` is lost or corrupted, go to **File → Import / Restore...**
- Select the `SFW_Export_*` folder from your backup
- **All current data will be replaced**

---

## QR Sync (Desktop → Mobile)

1. On the laptop, open **Windows Settings → Network & Internet → Mobile Hotspot** and turn it ON.
2. Note the hotspot name (SSID).
3. In the app, go to the **QR Sync** tab.
4. Type the hotspot SSID in the field and click **Generate Pairing QR**.
5. On the phone, open the SFW companion Flutter app → tap **Sync** → scan the QR.
6. Data transfers over the local hotspot. No internet required.

The QR token is **one-time use** — generate a new QR for each sync session.

---

## Troubleshooting

| Problem | Solution |
|---|---|
| App won't start on USB HDD | Ensure the full `SFW-Wholesale\` folder was copied, not just the `.exe` |
| "No suitable address detected" in QR Sync | Turn on Windows Mobile Hotspot first, then click Generate QR |
| Database locked error | Close any other program that might have `footwear.db` open (e.g., DB Browser) |
| Slow startup | Expected on HDD — splash screen shows while loading. Typical: 3–8 seconds |
| Build fails with "jlink not found" | Ensure `JAVA_HOME` in `package.bat` points to Liberica JDK Full, not standard JDK |

---

## Project Structure Reference

```
footwear-wholesale/
├── pom.xml                                # Maven build
├── run.bat                                # Development launcher
├── package.bat                            # Self-contained bundle builder
├── build-instructions.md                  # This file
└── src/main/
    ├── java/com/sfw/wholesale/
    │   ├── App.java                       # Entry point + splash
    │   ├── MainWindow.java                # Root window + tabs
    │   ├── database/
    │   │   ├── DatabaseManager.java       # SQLite connection, WAL, schema
    │   │   └── DataCache.java             # In-memory caches (HDD perf)
    │   ├── model/                         # JavaFX property models
    │   ├── service/                       # Business logic
    │   │   ├── LrService.java             # LR save/load, stock trigger
    │   │   ├── StockService.java          # Merge rule, Shop guard
    │   │   ├── GdTransferService.java     # Done/Undo, stock movement
    │   │   ├── CreditorService.java       # Auto-create from LR
    │   │   ├── ExportImportService.java   # CSV backup/restore
    │   │   └── SyncService.java           # QR + HTTP server
    │   ├── ui/
    │   │   ├── tab/                       # 5 tab classes
    │   │   └── component/                 # Reusable widgets
    │   └── util/                          # NetworkUtil
    └── resources/
        ├── css/app.css                    # Dark theme
        ├── icons/                         # sfw-logo.png + sfw-logo.ico
        └── sample-data/seed.sql           # Test data
```
