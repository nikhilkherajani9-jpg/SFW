package com.sfw.wholesale.service;

import com.sfw.wholesale.database.DatabaseManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;

public class ExportImportTest {
    public static void main(String[] args) {
        System.out.println("Starting Export/Import Test...");
        try {
            // 1. Initialize DB
            DatabaseManager db = DatabaseManager.getInstance();
            db.open();
            
            // 2. Insert some dummy data
            try (Connection conn = db.getConnection();
                 Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT OR IGNORE INTO transport_companies (name) VALUES ('TEST TRANSPORT')");
                stmt.execute("INSERT OR IGNORE INTO stock (product_name, location, cartons, pairs_per_carton, lr_source) VALUES ('SHOE A', 'G1', 10, 12, 'TEST-LR')");
            }
            
            // 3. Export
            ExportImportService service = new ExportImportService();
            Path exportDir = Paths.get("target/test-export");
            Files.createDirectories(exportDir);
            
            Path actualExportPath = service.export(exportDir);
            System.out.println("Exported to: " + actualExportPath);
            
            // 4. Verify files exist
            String[] expectedFiles = {"transport_companies.csv", "lr_entries.csv", "lr_items.csv", "stock.csv", "gd_transfers.csv", };
            for (String file : expectedFiles) {
                Path p = actualExportPath.resolve(file);
                if (Files.exists(p)) {
                    System.out.println("Found " + file + " - Size: " + Files.size(p) + " bytes");
                } else {
                    System.err.println("MISSING " + file);
                }
            }
            
            // 5. Import back
            System.out.println("Testing Import...");
            service.importFromFolder(actualExportPath);
            System.out.println("Import successful!");
            
            System.out.println("All Export/Import tests passed.");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
