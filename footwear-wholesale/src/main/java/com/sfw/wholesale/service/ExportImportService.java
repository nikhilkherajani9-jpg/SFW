package com.sfw.wholesale.service;

import com.sfw.wholesale.database.DataCache;
import com.sfw.wholesale.database.DatabaseManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Export (backup) and Import (recovery) functionality.
 *
 * Export:  Writes 5 CSV files into a timestamped folder. Format is accountant-friendly.
 * Import:  Reads the 5 CSVs, validates headers, wipes all tables, re-inserts data.
 *          Import is a recovery-only operation — requires explicit user confirmation.
 */
public class ExportImportService {

    private static final Logger LOG   = Logger.getLogger(ExportImportService.class.getName());
    private final DatabaseManager db  = DatabaseManager.getInstance();
    private final DataCache cache     = DataCache.getInstance();

    private static final DateTimeFormatter FNAME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Exports all data to 5 CSV files in a new folder under the given directory.
     * @param outputDir  directory chosen by user via DirectoryChooser
     * @return  the created folder path (shown to user after export)
     */
    public Path export(Path outputDir) throws Exception {
        String folderName = "SFW_Export_" + LocalDateTime.now().format(FNAME_FMT);
        Path folder = outputDir.resolve(folderName);
        Files.createDirectories(folder);

        exportLrEntries(folder);
        exportLrItems(folder);
        exportStock(folder);
        exportGdTransfers(folder);

        LOG.info("Export complete: " + folder.toAbsolutePath());
        return folder;
    }

    private void exportLrEntries(Path folder) throws Exception {
        String[] headers = {"LR Number","Received Date","Transport Company"};
        String sql = """
            SELECT lr_number, lr_date, transport_company
            FROM lr_entries WHERE lr_number != 'MANUAL-ADJ'
            ORDER BY lr_date DESC
            """;
        writeCsv(folder.resolve("lr_entries.csv"), headers, sql);
    }

    private void exportLrItems(Path folder) throws Exception {
        String[] headers = {"LR Number","Line No","Product Name",
                "Cartons","Pairs Per Carton","Location"};
        String sql = """
            SELECT e.lr_number, i.line_no, i.product_name,
                   i.cartons, i.pairs_per_carton, i.location
            FROM lr_items i JOIN lr_entries e ON e.id = i.lr_id
            ORDER BY e.lr_date DESC, e.id, i.line_no
            """;
        writeCsv(folder.resolve("lr_items.csv"), headers, sql);
    }

    private void exportStock(Path folder) throws Exception {
        String[] headers = {"Product Name","Location","Cartons",
                "Pairs Per Carton","Total Pairs","LR Source","Receive Date"};
        String sql = """
            SELECT product_name, location, cartons, pairs_per_carton,
                   (cartons * pairs_per_carton) AS total_pairs, lr_source, receive_date
            FROM stock ORDER BY product_name, location, receive_date
            """;
        writeCsv(folder.resolve("stock.csv"), headers, sql);
    }

    private void exportGdTransfers(Path folder) throws Exception {
        String[] headers = {"Date","Product Name","From Location",
                "To Location","Qty Cartons","Pairs","Done"};
        String sql = """
            SELECT transfer_date, product_name, prev_location,
                   updated_location, qty_cartons, pairs,
                   CASE done WHEN 1 THEN 'Yes' ELSE 'No' END
            FROM gd_transfers ORDER BY transfer_date DESC
            """;
        writeCsv(folder.resolve("gd_transfers.csv"), headers, sql);
    }



    /** Generic CSV writer for any SQL query. Handles NULL values as empty strings. */
    private void writeCsv(Path file, String[] headers, String sql) throws Exception {
        try (BufferedWriter bw = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             Statement st = db.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            // BOM for Excel UTF-8 compatibility
            bw.write('\uFEFF');
            bw.write(csvRow(headers));

            int colCount = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                String[] row = new String[colCount];
                for (int c = 1; c <= colCount; c++) {
                    String val = rs.getString(c);
                    row[c - 1] = val != null ? val : "";
                }
                bw.write(csvRow(row));
            }
        }
    }

    private String csvRow(String[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csvEscape(values[i]));
        }
        sb.append("\r\n");
        return sb.toString();
    }

    private String csvEscape(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Restores data from a previously exported folder.
     * WIPES ALL CURRENT DATA before importing. User must confirm in the UI before
     * calling this method.
     *
     * @param exportFolder  the SFW_Export_* folder to restore from
     */
    public void importFromFolder(Path exportFolder) throws Exception {
        validateExportFolder(exportFolder);

        db.inTransaction(conn -> {
            wipeTables(conn);
            importLrEntries(conn, exportFolder.resolve("lr_entries.csv"));
            importLrItems(conn, exportFolder.resolve("lr_items.csv"));
            importStock(conn, exportFolder.resolve("stock.csv"));
            importGdTransfers(conn, exportFolder.resolve("gd_transfers.csv"));
        });

        // Reload all caches
        cache.loadAll(db.getConnection());
        LOG.info("Import complete from: " + exportFolder.toAbsolutePath());
    }

    private void validateExportFolder(Path folder) throws IOException {
        String[] required = {"lr_entries.csv","lr_items.csv","stock.csv",
                "gd_transfers.csv"};
        for (String f : required) {
            if (!Files.exists(folder.resolve(f)))
                throw new IOException("Missing file in export folder: " + f);
        }
    }

    private void wipeTables(Connection conn) throws SQLException {
        String[] tables = {"gd_transfers","stock","lr_items","lr_entries",
                "transport_companies"};
        try (Statement st = conn.createStatement()) {
            for (String t : tables) st.execute("DELETE FROM " + t);
            // Re-seed mandatory MANUAL-ADJ LR
            st.execute("INSERT OR IGNORE INTO lr_entries " +
                    "(lr_number,lr_date,transport_company) " +
                    "VALUES('MANUAL-ADJ','2000-01-01','SYSTEM')");
        }
    }

    private void importLrEntries(Connection conn, Path file) throws Exception {
        String sql = """
            INSERT OR IGNORE INTO lr_entries
                (lr_number,lr_date,transport_company)
            VALUES (?,?,?)
            """;
        processCsv(file, 1, row -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, row[0]);
                ps.setString(2, row[1]);
                ps.setString(3, row[2]);
                ps.executeUpdate();
            }
            // Ensure transport exists
            ensureLookup(conn, "transport_companies", row[2]);
        });
    }

    private void importLrItems(Connection conn, Path file) throws Exception {
        String sql = """
            INSERT INTO lr_items
                (lr_id,line_no,product_name,cartons,pairs_per_carton,location)
            SELECT e.id, ?, ?, ?, ?, ?
            FROM lr_entries e WHERE e.lr_number=?
            """;
        processCsv(file, 1, row -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, parseInt(row[1]));
                ps.setString(2, row[2]);
                ps.setInt(3, parseInt(row[3]));
                ps.setInt(4, parseInt(row[4]));
                ps.setString(5, row[5]);
                ps.setString(6, row[0]); // lr_number for subquery
                ps.executeUpdate();
            }
        });
    }

    private void importStock(Connection conn, Path file) throws Exception {
        String sql = """
            INSERT OR REPLACE INTO stock
                (product_name,location,cartons,pairs_per_carton,lr_source,receive_date)
            VALUES (?,?,?,?,?,?)
            """;
        processCsv(file, 1, row -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, row[0]);
                ps.setString(2, row[1]);
                ps.setInt(3, parseInt(row[2]));
                ps.setInt(4, parseInt(row[3]));
                // Note: index 4 is Total Pairs (skip), index 5 is LR Source.
                setNullIfBlank(ps, 5, row.length > 5 ? row[5] : "");
                
                String rDate = row.length > 6 ? row[6] : "";
                if (rDate == null || rDate.isBlank()) {
                    rDate = LocalDate.now().toString();
                }
                ps.setString(6, rDate);
                
                ps.executeUpdate();
            }
        });
    }

    private void importGdTransfers(Connection conn, Path file) throws Exception {
        String sql = """
            INSERT INTO gd_transfers
                (transfer_date,product_name,prev_location,updated_location,
                 qty_cartons,pairs,done)
            VALUES (?,?,?,?,?,?,?)
            """;
        processCsv(file, 1, row -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, row[0]);
                ps.setString(2, row[1]);
                ps.setString(3, row[2]);
                ps.setString(4, row[3]);
                ps.setInt(5, parseInt(row[4]));
                ps.setInt(6, parseInt(row[5]));
                ps.setInt(7, "Yes".equalsIgnoreCase(row[6]) ? 1 : 0);
                ps.executeUpdate();
            }
        });
    }



    // ── CSV reading helper ────────────────────────────────────────────────────

    @FunctionalInterface
    interface RowProcessor { void process(String[] row) throws Exception; }

    private void processCsv(Path file, int skipRows, RowProcessor processor) throws Exception {
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int row = 0;
            while ((line = br.readLine()) != null) {
                if (row++ < skipRows) continue;
                if (line.isBlank()) continue;
                String[] cols = parseCsvLine(line);
                processor.process(cols);
            }
        }
    }

    /** Minimal RFC 4180 CSV line parser. */
    private String[] parseCsvLine(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"'); i++;
                } else inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                cols.add(cur.toString()); cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cols.add(cur.toString());
        return cols.toArray(new String[0]);
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0.0; }
    }

    private void setNullIfBlank(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val == null || val.isBlank()) ps.setNull(idx, Types.VARCHAR);
        else ps.setString(idx, val);
    }

    private void ensureLookup(Connection conn, String table, String name) throws SQLException {
        if (name == null || name.isBlank()) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO " + table + " (name) VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
    }
}
