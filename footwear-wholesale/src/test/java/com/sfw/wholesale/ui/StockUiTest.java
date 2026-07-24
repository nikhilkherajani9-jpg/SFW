package com.sfw.wholesale.ui;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;
import org.testfx.api.FxAssert;
import org.testfx.matcher.control.LabeledMatchers;

public class StockUiTest extends BaseUiTest {

    @Test
    public void testStockFiltersAndManualAdd() {
        clickOn("#tabStock");
        sleep(200);

        // Click on Manual Add
        clickOn("#manualAddBtn");
        sleep(200);

        // The dialog is open, let's close it
        
        // Cancel dialog
        push(KeyCode.ESCAPE);
        sleep(200);



        // Verify elements
        clickOn("#stockSearchField").write("TESTING");
        sleep(200);
        
        FxAssert.verifyThat("No stock found for current filters.", LabeledMatchers.hasText("No stock found for current filters."));
    }
}
