package com.sfw.wholesale.ui.tab;

import javafx.scene.input.KeyEvent;

/**
 * Interface for tabs that handle global keyboard shortcuts.
 * The MainWindow routes Scene-level key events to the currently active tab,
 * ensuring that shortcuts (like Ctrl+S) only trigger on the visible tab.
 */
public interface TabShortcutHandler {
    void handleShortcut(KeyEvent event);
}
