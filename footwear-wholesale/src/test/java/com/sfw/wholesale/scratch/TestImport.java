package com.sfw.wholesale.scratch;

import com.sfw.wholesale.service.LegacyImportService;
import java.io.File;
import java.util.List;

public class TestImport {
    public static void main(String[] args) {
        File dir = new File("C:\\Users\\Sunil\\Downloads\\STOCK NEW\\STOCK NEW");
        LegacyImportService svc = new LegacyImportService();
        List<LegacyImportService.ParsedLegacyData> data = svc.scanFiles(dir);
        System.out.println("PARSED FILES: " + data.size());
    }
}
