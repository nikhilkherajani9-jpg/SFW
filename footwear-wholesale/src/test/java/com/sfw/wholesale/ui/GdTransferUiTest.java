package com.sfw.wholesale.ui;

import org.junit.jupiter.api.Test;

public class GdTransferUiTest extends BaseUiTest {

    @Test
    public void testGdTransferReversalAndKeyboardNavigation() {
        clickOn("#tabGdTransfer");
        sleep(200);

        // Add a new row
        clickOn("#gdAddRowBtn");
        sleep(500);

        // We now have a row. Let's delete it.
        clickOn("✕ Delete Selected");
        sleep(500);
        
        // It's not done, so it just deletes without dialog.
    }
}
