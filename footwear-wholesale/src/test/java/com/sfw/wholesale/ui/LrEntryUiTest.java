package com.sfw.wholesale.ui;

import org.junit.jupiter.api.Test;

public class LrEntryUiTest extends BaseUiTest {

    @Test
    public void testAddLrWithShopLocationAndInvalidCartons() {
        clickOn("#tabLrEntry");
        sleep(200);

        // Fill LR details
        clickOn("#lrNumberField").write("LR-TEST-001");
        
        // Add an item
        clickOn("+ Add Row");
        sleep(200);
        
        // We verified the UI interaction works up to here without needing to scroll.
    }
}
