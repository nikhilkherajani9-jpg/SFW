package com.sfw.wholesale.service;

import com.sfw.wholesale.database.DatabaseManager;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class LegacyImportService {

    private static final Logger LOG = Logger.getLogger(LegacyImportService.class.getName());
    
    // Valid standard locations
    public static final Set<String> VALID_LOCATIONS = Set.of("G1", "G2", "G3", "G4", "G5", "RK2", "Shop");

    public static class LegacyTransaction {
        public LocalDate date;
        public int inward;
        public int outward;
        public String lrSource;
    }

    public static class ParsedLegacyData {
        public File file;
        public String originalFileName;
        public String extractedLocation;
        public String mappedLocation; // After user fixes invalid ones
        public String productName;
        public int finalBalance;
        public String lrSource;
        public LocalDate receiveDate;
        public List<LegacyTransaction> transactions = new ArrayList<>();
        
        public String getOriginalFileName() { return originalFileName; }
        public String getExtractedLocation() { return extractedLocation; }
        public String getMappedLocation() { return mappedLocation; }
        public void setMappedLocation(String mappedLocation) { this.mappedLocation = mappedLocation; }
    }

    /**
     * Phase 1: Scans all .xls files in the directory.
     * Extracts filenames, determines locations, and parses the Excel contents to find the final balance.
     * Skips files where final balance < 1.
     */
    public List<ParsedLegacyData> scanFiles(File directory) {
        List<ParsedLegacyData> results = new ArrayList<>();
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".xls"));
        
        if (files == null) return results;

        for (File file : files) {
            String name = file.getName();
            // e.g. "2340 PUBG EVA 7X10 G4.xls"
            String baseName = name.substring(0, name.toLowerCase().lastIndexOf(".xls")).trim();
            int lastSpace = baseName.lastIndexOf(' ');
            if (lastSpace == -1) {
                LOG.warning("Skipping file due to unexpected name format: " + name);
                continue;
            }
            
            String location = baseName.substring(lastSpace + 1).trim().toUpperCase();
            String product = baseName.substring(0, lastSpace).trim();
            
            ParsedLegacyData data = new ParsedLegacyData();
            data.file = file;
            data.originalFileName = name;
            data.extractedLocation = location;
            data.mappedLocation = location; // Default to extracted
            data.productName = product;
            
            if (parseFileContents(data)) {
                results.add(data);
            }
        }
        return results;
    }

    /**
     * Reads the Excel file. Sets finalBalance, lrSource, and receiveDate.
     * Returns true if balance >= 1 and should be imported, false otherwise.
     */
    private boolean parseFileContents(ParsedLegacyData data) {
        try (FileInputStream fis = new FileInputStream(data.file);
             Workbook workbook = new HSSFWorkbook(fis)) {
             
            Sheet sheet = workbook.getSheetAt(0);
            
            double sumInward = 0;
            double sumOutward = 0;
            String lrSource = "";
            LocalDate receiveDate = LocalDate.now(); // fallback
            int runningBalance = 0;
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) { // Skip header row
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                int rowInward = 0;
                int rowOutward = 0;
                String rowLrSource = "";
                LocalDate rowDate = LocalDate.now();
                boolean hasTransaction = false;

                try {
                    // Date
                    Cell dateCell = row.getCell(0);
                    if (dateCell != null && dateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell)) {
                        rowDate = dateCell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    }

                    // LR Source (moved out so inferred transactions can read it)
                    Cell lrCell = row.getCell(4);
                    if (lrCell != null) {
                        CellType lrType = lrCell.getCellType() == CellType.FORMULA ? lrCell.getCachedFormulaResultType() : lrCell.getCellType();
                        if (lrType == CellType.STRING && !lrCell.getStringCellValue().isBlank()) {
                            rowLrSource = lrCell.getStringCellValue().trim();
                        } else if (lrType == CellType.NUMERIC) {
                            rowLrSource = String.valueOf((long) lrCell.getNumericCellValue());
                        }
                    }

                    // Inward
                    Cell inwardCell = row.getCell(1);
                    if (inwardCell != null) {
                        CellType inType = inwardCell.getCellType() == CellType.FORMULA ? inwardCell.getCachedFormulaResultType() : inwardCell.getCellType();
                        if (inType == CellType.NUMERIC) {
                            double val = inwardCell.getNumericCellValue();
                            if (val > 0) {
                                rowInward = (int) Math.round(val);
                                hasTransaction = true;
                            }
                        }
                    }

                    // Outward
                    Cell outwardCell = row.getCell(2);
                    if (outwardCell != null) {
                        CellType outType = outwardCell.getCellType() == CellType.FORMULA ? outwardCell.getCachedFormulaResultType() : outwardCell.getCellType();
                        if (outType == CellType.NUMERIC) {
                            double val = outwardCell.getNumericCellValue();
                            if (val > 0) {
                                rowOutward = (int) Math.round(val);
                                hasTransaction = true;
                            }
                        }
                    }
                    
                    // Balance Inference (implicit transactions)
                    Cell balanceCell = row.getCell(3);
                    int actualBalance = runningBalance + rowInward - rowOutward;
                    if (balanceCell != null) {
                        CellType balType = balanceCell.getCellType() == CellType.FORMULA ? balanceCell.getCachedFormulaResultType() : balanceCell.getCellType();
                        if (balType == CellType.NUMERIC) {
                            actualBalance = (int) Math.round(balanceCell.getNumericCellValue());
                        }
                    }

                    if (rowInward == 0 && rowOutward == 0 && actualBalance != runningBalance) {
                        if (actualBalance > runningBalance) {
                            rowInward = actualBalance - runningBalance;
                        } else {
                            rowOutward = runningBalance - actualBalance;
                        }
                        hasTransaction = true;
                    }
                    runningBalance = actualBalance;
                    
                    if (hasTransaction) {
                        LegacyTransaction tx = new LegacyTransaction();
                        tx.date = rowDate;
                        tx.inward = rowInward;
                        tx.outward = rowOutward;
                        
                        tx.lrSource = rowLrSource;
                        
                        data.transactions.add(tx);
                        
                        sumInward += rowInward;
                        sumOutward += rowOutward;
                        lrSource = rowLrSource; // keep last
                        receiveDate = rowDate;  // keep last
                    }
                } catch (Exception rowEx) {
                    // Ignore rows that fail to parse (e.g., text headers)
                }
            }
            
            int finalBalance = (int) Math.round(sumInward - sumOutward);
            
            // Accept 0 balances as well now!
            if (finalBalance >= 0 || !data.transactions.isEmpty()) {
                data.finalBalance = finalBalance;
                data.lrSource = lrSource;
                data.receiveDate = receiveDate;
                return true;
            }
            return finalBalance >= 0 || !data.transactions.isEmpty();
        } catch (Exception e) {
            LOG.warning("Failed to parse file: " + data.file.getName() + " - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Returns a list of parsed items that have invalid locations.
     */
    public List<ParsedLegacyData> getInvalidLocations(List<ParsedLegacyData> parsedData) {
        return parsedData.stream()
                .filter(d -> !VALID_LOCATIONS.contains(d.mappedLocation))
                .collect(Collectors.toList());
    }

    /**
     * Phase 3: Actually injects the mapped items into the database.
     */
    public void importData(List<ParsedLegacyData> parsedData, StockService stockService, DatabaseManager dbManager) throws Exception {
        dbManager.inTransaction(conn -> {
            for (ParsedLegacyData data : parsedData) {
                stockService.importLegacyStockWithLedger(
                    conn,
                    data.productName,
                    data.mappedLocation,
                    data.finalBalance,
                    1, // Default pairsPerCarton as requested by user
                    data.transactions
                );
            }
        });
    }
}
