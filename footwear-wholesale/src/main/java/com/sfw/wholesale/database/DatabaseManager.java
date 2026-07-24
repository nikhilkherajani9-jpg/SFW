package com.sfw.wholesale.database;

import java.io.File;
import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton that owns the SQLite connection for the application lifetime.
 *
 * Performance engineering for USB HDD:
 *  - WAL journal mode  → sequential appends instead of random writes
 *  - synchronous=NORMAL → safe with WAL, far fewer fsync calls
 *  - cache_size=-16000  → 16 MB in-memory page cache; fewer disk reads
 *  - temp_store=MEMORY  → temp tables/sorts stay in RAM
 *  - VACUUM is NOT called during normal use; only on explicit user action.
 */
public class DatabaseManager {

    private static final Logger LOG = Logger.getLogger(DatabaseManager.class.getName());
    private static String DB_FILENAME = "footwear.db";

    private static DatabaseManager instance;
    private Connection connection;

    // ── Singleton ─────────────────────────────────────────────────────────────

    private DatabaseManager() {}

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    public static synchronized void setTestMode(boolean testMode) {
        if (testMode) {
            DB_FILENAME = "test-footwear.db";
        } else {
            DB_FILENAME = "footwear.db";
        }
    }


    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Opens (or creates) the SQLite database in the same directory as the running
     * JAR/exe — so on a USB HDD, the .db file lives next to the application.
     */
    public synchronized void open() throws SQLException {
        if (connection != null && !connection.isClosed()) return;

        String dbPath = resolveDbPath();
        LOG.info("Opening database: " + dbPath);

        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        applyPragmas();
        createSchema();
        
        // Migrations
        migrateStockTableLocationCheck();
        migrateStockLedgerInitialization();
        migrateLrItemsShopCartons();
        migrateLrItemsDropRateAmount();
        migrateLrEntriesDropCreditorColumns();
        
        seedMandatoryData();
        cleanupDuplicateStock();
        LOG.info("Database ready.");
    }

    public synchronized Connection getConnection() {
        return connection;
    }

    /**
     * Closes the connection cleanly. Called on application exit.
     * Optionally runs VACUUM at this point (HDD-friendly: only on close).
     */
    public synchronized void close(boolean vacuum) {
        if (connection == null) return;
        try {
            if (vacuum) {
                try (Statement st = connection.createStatement()) {
                    st.execute("PRAGMA wal_checkpoint(TRUNCATE);");
                    st.execute("VACUUM;");
                }
            }
            connection.close();
            LOG.info("Database closed.");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error closing database", e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Resolves the DB path to the directory containing the running JAR/exe.
     * Falls back to the current working directory if that cannot be determined.
     */
    private String resolveDbPath() {
        try {
            File jarFile = new File(
                    DatabaseManager.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI());
            File dir = jarFile.isFile() ? jarFile.getParentFile() : jarFile;
            
            // If running from IDE/Maven, dir will be target/classes
            // In that case, we should save in the project root to avoid data loss on clean
            String path = dir.getAbsolutePath().replace("\\", "/");
            if (path.endsWith("/target/classes") || path.endsWith("/target/test-classes")) {
                dir = new File(System.getProperty("user.dir"));
            }
            
            return new File(dir, DB_FILENAME).getAbsolutePath();
        } catch (Exception e) {
            LOG.warning("Could not determine JAR location; using CWD for DB path.");
            return new File(System.getProperty("user.dir"), DB_FILENAME).getAbsolutePath();
        }
    }

    private void applyPragmas() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA synchronous=NORMAL;");
            st.execute("PRAGMA cache_size=-16000;");   // 16 MB page cache
            st.execute("PRAGMA temp_store=MEMORY;");
            st.execute("PRAGMA foreign_keys=ON;");
        }
    }

    private void createSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {

            // ── Lookup tables ─────────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS transport_companies (
                    id   INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE COLLATE NOCASE
                )""");

            // ── LR entries ────────────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS lr_entries (
                    id                INTEGER PRIMARY KEY AUTOINCREMENT,
                    lr_number         TEXT    NOT NULL UNIQUE COLLATE NOCASE,
                    lr_date           TEXT    NOT NULL,
                    transport_company TEXT    NOT NULL
                )""");

            // ── LR item lines ─────────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS lr_items (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    lr_id            INTEGER NOT NULL
                                     REFERENCES lr_entries(id) ON DELETE CASCADE,
                    line_no          INTEGER NOT NULL,
                    product_name     TEXT    NOT NULL,
                    cartons          INTEGER NOT NULL CHECK(cartons > 0),
                    shop_cartons     INTEGER NOT NULL DEFAULT 0,
                    pairs_per_carton INTEGER NOT NULL CHECK(pairs_per_carton > 0),
                    location         TEXT    NOT NULL
                )""");

            // ── Stock ─────────────────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS stock (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_name     TEXT    NOT NULL,
                    location         TEXT    NOT NULL,
                    cartons          INTEGER NOT NULL DEFAULT 0,
                    pairs_per_carton INTEGER NOT NULL DEFAULT 1,
                    lr_source        TEXT,
                    receive_date     TEXT    NOT NULL DEFAULT (date('now'))
                )""");
                
            // ── Stock Ledger ──────────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS stock_ledger (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_name     TEXT    NOT NULL,
                    location         TEXT    NOT NULL,
                    transaction_date TEXT    NOT NULL,
                    inward_cartons   INTEGER NOT NULL DEFAULT 0,
                    outward_cartons  INTEGER NOT NULL DEFAULT 0,
                    balance_cartons  INTEGER NOT NULL DEFAULT 0,
                    pairs_per_carton INTEGER NOT NULL DEFAULT 1,
                    lr_source        TEXT,
                    transaction_type TEXT    NOT NULL
                )""");

            // ── GD Transfers ──────────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS gd_transfers (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    transfer_date    TEXT    NOT NULL,
                    product_name     TEXT    NOT NULL,
                    prev_location    TEXT    NOT NULL
                                     CHECK(prev_location IN ('G1','G2','G3','G4','G5','RK2')),
                    updated_location TEXT    NOT NULL,
                    qty_cartons      INTEGER NOT NULL CHECK(qty_cartons >= 1),
                    pairs            INTEGER NOT NULL,
                    done             INTEGER NOT NULL DEFAULT 0
                )""");

            st.execute("""
                CREATE TABLE IF NOT EXISTS gd_transfer_deductions (
                    transfer_id  INTEGER NOT NULL,
                    lr_source    TEXT,
                    receive_date TEXT    NOT NULL,
                    cartons      INTEGER NOT NULL,
                    FOREIGN KEY(transfer_id) REFERENCES gd_transfers(id) ON DELETE CASCADE
                )""");



            // ── Indexes ───────────────────────────────────────────────────────
            st.execute("CREATE INDEX IF NOT EXISTS idx_lr_entries_lr_number ON lr_entries(lr_number)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_lr_entries_date      ON lr_entries(lr_date)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_lr_items_lr_id       ON lr_items(lr_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_lr_items_product     ON lr_items(product_name)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_stock_product        ON stock(product_name)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_stock_location       ON stock(location)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_stock_ledger_prod    ON stock_ledger(product_name)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_stock_ledger_loc     ON stock_ledger(location)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_gd_product          ON gd_transfers(product_name)");
        }
    }

    /**
     * Seeds data that must always exist.
     * The MANUAL-ADJ dummy LR backs every manual stock correction.
     */
    private void seedMandatoryData() throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR IGNORE INTO lr_entries
                    (lr_number, lr_date, transport_company)
                VALUES ('MANUAL-ADJ', '2000-01-01', 'SYSTEM')
                """)) {
            ps.executeUpdate();
        }
    }

    // ── Database Migration/Cleanup ────────────────────────────────────────────

    private void migrateStockTableLocationCheck() throws SQLException {
        try (Statement st = connection.createStatement()) {
            // Check if the current stock table has the CHECK constraint on location.
            // SQLite pragma table_info doesn't show CHECK constraints easily, but we can look at sqlite_master.
            ResultSet rs = st.executeQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='stock'");
            if (rs.next()) {
                String sql = rs.getString("sql");
                if (sql != null && sql.contains("CHECK(location IN")) {
                    LOG.info("Migrating stock table to remove location CHECK constraint...");
                    st.execute("PRAGMA foreign_keys=OFF;");
                    st.execute("BEGIN TRANSACTION;");
                    st.execute("CREATE TABLE stock_new (id INTEGER PRIMARY KEY AUTOINCREMENT, product_name TEXT NOT NULL, location TEXT NOT NULL, cartons INTEGER NOT NULL DEFAULT 0, pairs_per_carton INTEGER NOT NULL DEFAULT 1, lr_source TEXT, receive_date TEXT NOT NULL DEFAULT (date('now')));");
                    st.execute("INSERT INTO stock_new SELECT id, product_name, location, cartons, pairs_per_carton, lr_source, receive_date FROM stock;");
                    st.execute("DROP TABLE stock;");
                    st.execute("ALTER TABLE stock_new RENAME TO stock;");
                    st.execute("CREATE INDEX idx_stock_product ON stock(product_name);");
                    st.execute("CREATE INDEX idx_stock_location ON stock(location);");
                    st.execute("COMMIT;");
                    st.execute("PRAGMA foreign_keys=ON;");
                    LOG.info("Stock table migration complete.");
                }
            }
        }
    }

    private void migrateStockLedgerInitialization() throws SQLException {
        // If stock_ledger was just added, existing stock won't have any ledger entries.
        // We insert an OPENING_BALANCE for any stock that doesn't have a ledger entry yet.
        try (Statement st = connection.createStatement()) {
            st.execute("""
                INSERT INTO stock_ledger (product_name, location, transaction_date, inward_cartons, outward_cartons, balance_cartons, pairs_per_carton, lr_source, transaction_type)
                SELECT s.product_name, s.location, s.receive_date, s.cartons, 0, s.cartons, s.pairs_per_carton, s.lr_source, 'OPENING_BALANCE'
                FROM stock s
                WHERE NOT EXISTS (
                    SELECT 1 FROM stock_ledger sl
                    WHERE sl.product_name = s.product_name AND sl.location = s.location
                )
                """);
            
            // Clean up any existing LEGACY outward entries so they appear blank.
            st.execute("UPDATE stock_ledger SET lr_source = '' WHERE lr_source = 'LEGACY'");
            st.execute("UPDATE stock SET lr_source = '' WHERE lr_source = 'LEGACY'");
            
            // Clean up old LEGACY transaction types
            st.execute("UPDATE stock_ledger SET transaction_type = 'DISPATCH' WHERE transaction_type = 'LEGACY' AND outward_cartons > 0 AND inward_cartons = 0");
            st.execute("UPDATE stock_ledger SET transaction_type = 'INWARD' WHERE transaction_type = 'LEGACY'");
        }
    }

    private void migrateLrItemsShopCartons() throws SQLException {
        try (Statement st = connection.createStatement()) {
            ResultSet rs = st.executeQuery("PRAGMA table_info(lr_items)");
            boolean hasShopCartons = false;
            while (rs.next()) {
                if ("shop_cartons".equals(rs.getString("name"))) {
                    hasShopCartons = true;
                    break;
                }
            }
            if (!hasShopCartons) {
                LOG.info("Migrating lr_items: adding shop_cartons column.");
                st.execute("ALTER TABLE lr_items ADD COLUMN shop_cartons INTEGER NOT NULL DEFAULT 0");
            }
        }
    }

    private void migrateLrItemsDropRateAmount() throws SQLException {
        try (Statement st = connection.createStatement()) {
            ResultSet rs = st.executeQuery("PRAGMA table_info(lr_items)");
            boolean hasRate = false;
            boolean hasAmount = false;
            while (rs.next()) {
                String name = rs.getString("name");
                if ("rate_per_pair".equals(name)) hasRate = true;
                if ("amount".equals(name)) hasAmount = true;
            }
            if (hasRate || hasAmount) {
                LOG.info("Migrating lr_items: removing rate_per_pair and amount columns.");
                st.execute("PRAGMA foreign_keys=OFF;");
                st.execute("BEGIN TRANSACTION;");
                st.execute("CREATE TABLE lr_items_new (id INTEGER PRIMARY KEY AUTOINCREMENT, lr_id INTEGER NOT NULL REFERENCES lr_entries(id) ON DELETE CASCADE, line_no INTEGER NOT NULL, product_name TEXT NOT NULL, cartons INTEGER NOT NULL CHECK(cartons > 0), shop_cartons INTEGER NOT NULL DEFAULT 0, pairs_per_carton INTEGER NOT NULL CHECK(pairs_per_carton > 0), location TEXT NOT NULL);");
                st.execute("INSERT INTO lr_items_new (id, lr_id, line_no, product_name, cartons, shop_cartons, pairs_per_carton, location) SELECT id, lr_id, line_no, product_name, cartons, shop_cartons, pairs_per_carton, location FROM lr_items;");
                st.execute("DROP TABLE lr_items;");
                st.execute("ALTER TABLE lr_items_new RENAME TO lr_items;");
                st.execute("CREATE INDEX IF NOT EXISTS idx_lr_items_lr_id ON lr_items(lr_id);");
                st.execute("CREATE INDEX IF NOT EXISTS idx_lr_items_product ON lr_items(product_name);");
                st.execute("COMMIT;");
                st.execute("PRAGMA foreign_keys=ON;");
                LOG.info("lr_items migration complete.");
            }
        }
    }

    private void migrateLrEntriesDropCreditorColumns() throws SQLException {
        try (Statement st = connection.createStatement()) {
            ResultSet rs = st.executeQuery("PRAGMA table_info(lr_entries)");
            boolean hasSupplierName = false;
            while (rs.next()) {
                if ("supplier_name".equals(rs.getString("name"))) {
                    hasSupplierName = true;
                    break;
                }
            }
            if (hasSupplierName) {
                LOG.info("Migrating lr_entries: removing creditor/bill columns.");
                st.execute("PRAGMA foreign_keys=OFF;");
                st.execute("BEGIN TRANSACTION;");
                st.execute("CREATE TABLE lr_entries_new (id INTEGER PRIMARY KEY AUTOINCREMENT, lr_number TEXT NOT NULL UNIQUE COLLATE NOCASE, lr_date TEXT NOT NULL, transport_company TEXT NOT NULL);");
                st.execute("INSERT INTO lr_entries_new (id, lr_number, lr_date, transport_company) SELECT id, lr_number, lr_date, transport_company FROM lr_entries;");
                st.execute("DROP TABLE lr_entries;");
                st.execute("ALTER TABLE lr_entries_new RENAME TO lr_entries;");
                st.execute("CREATE INDEX IF NOT EXISTS idx_lr_entries_lr_number ON lr_entries(lr_number);");
                st.execute("CREATE INDEX IF NOT EXISTS idx_lr_entries_date ON lr_entries(lr_date);");
                
                // Drop other tables and indexes related to creditors just in case
                st.execute("DROP TABLE IF EXISTS suppliers;");
                st.execute("DROP TABLE IF EXISTS creditors;");
                
                st.execute("COMMIT;");
                st.execute("PRAGMA foreign_keys=ON;");
                LOG.info("lr_entries migration complete.");
            }
        }
    }

    private void cleanupDuplicateStock() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TEMP TABLE merged_stock AS
                SELECT product_name, location, pairs_per_carton, lr_source, receive_date, SUM(cartons) as total_cartons, MIN(id) as keep_id
                FROM stock
                GROUP BY product_name, location, pairs_per_carton, lr_source, receive_date
                HAVING COUNT(*) > 1
                """);

            st.execute("""
                UPDATE stock
                SET cartons = (SELECT total_cartons FROM merged_stock WHERE merged_stock.keep_id = stock.id)
                WHERE id IN (SELECT keep_id FROM merged_stock)
                """);

            st.execute("""
                DELETE FROM stock
                WHERE id NOT IN (SELECT keep_id FROM merged_stock)
                AND EXISTS (
                  SELECT 1 FROM merged_stock t
                  WHERE t.product_name = stock.product_name
                    AND t.location = stock.location
                    AND t.pairs_per_carton = stock.pairs_per_carton
                    AND t.lr_source IS stock.lr_source
                    AND t.receive_date = stock.receive_date
                )
                """);

            st.execute("DROP TABLE merged_stock");
        }
    }

    // ── Convenience transaction helpers ───────────────────────────────────────

    /** Runs a block inside a single SQLite transaction. Rolls back on any exception. */
    public void inTransaction(TransactionBlock block) throws SQLException {
        connection.setAutoCommit(false);
        try {
            block.run(connection);
            connection.commit();
        } catch (Exception e) {
            connection.rollback();
            throw new SQLException("Transaction rolled back: " + e.getMessage(), e);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @FunctionalInterface
    public interface TransactionBlock {
        void run(Connection conn) throws Exception;
    }
}
