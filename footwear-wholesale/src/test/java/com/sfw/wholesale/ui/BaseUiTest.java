package com.sfw.wholesale.ui;

import com.sfw.wholesale.MainWindow;
import com.sfw.wholesale.database.DataCache;
import com.sfw.wholesale.database.DatabaseManager;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.testfx.framework.junit5.ApplicationTest;

import java.io.File;

public abstract class BaseUiTest extends ApplicationTest {

    protected MainWindow mainWindow;

    @Override
    public void start(Stage stage) throws Exception {
        // Set test mode to use test-footwear.db instead of the real database
        DatabaseManager.setTestMode(true);
        
        // Ensure clean state before starting
        File dbFile = new File("test-footwear.db");
        if (dbFile.exists()) {
            dbFile.delete();
        }

        DatabaseManager db = DatabaseManager.getInstance();
        // Since DatabaseManager is a singleton, we need to make sure we close the old connection
        db.close(false); 
        db.open();
        DataCache.getInstance().loadAll(db.getConnection());

        mainWindow = new MainWindow(stage);
        mainWindow.show();
    }

    @AfterEach
    public void tearDown() {
        DatabaseManager.getInstance().close(false);
        File dbFile = new File("test-footwear.db");
        if (dbFile.exists()) {
            dbFile.delete(); // Clean up after tests
        }
    }
}
