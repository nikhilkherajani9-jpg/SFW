package com.sfw.wholesale.ui.component;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import java.util.Optional;

/**
 * Styled confirmation dialog matching the app's dark theme.
 * Use instead of Alert.AlertType.CONFIRMATION for consistent styling.
 *
 * Usage:
 *   boolean confirmed = ConfirmDialog.show("Delete Transfer",
 *       "This will reverse the stock movement — are you sure?");
 */
public class ConfirmDialog {

    private ConfirmDialog() {}

    /**
     * Shows a modal confirmation dialog.
     * @return true if the user clicked Confirm, false if Cancel or closed.
     */
    public static boolean show(String title, String message) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setGraphic(null);

        // Apply dark styling to the dialog pane
        DialogPane pane = dialog.getDialogPane();
        pane.setStyle(
            "-fx-background-color: #ffffff; " +
            "-fx-border-color: #cbd5e1; " +
            "-fx-border-width: 1;"
        );

        // Content
        Label msgLabel = new Label(message);
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(380);
        msgLabel.setStyle("-fx-text-fill: #334155; -fx-font-size: 14px;");

        VBox content = new VBox(msgLabel);
        content.setPadding(new Insets(20, 24, 8, 24));
        pane.setContent(content);

        // Custom buttons (styled to match theme)
        ButtonType confirmBtn = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn  = new ButtonType("Cancel",  ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().setAll(confirmBtn, cancelBtn);

        // Style the buttons
        Button confirmButton = (Button) pane.lookupButton(confirmBtn);
        confirmButton.setStyle(
            "-fx-background-color: #ef4444; " +
            "-fx-text-fill: white; " +
            "-fx-font-weight: bold; " +
            "-fx-background-radius: 6; " +
            "-fx-cursor: hand;"
        );

        Button cancelButton = (Button) pane.lookupButton(cancelBtn);
        cancelButton.setStyle(
            "-fx-background-color: #e2e8f0; " +
            "-fx-text-fill: #334155; " +
            "-fx-background-radius: 6; " +
            "-fx-cursor: hand;"
        );

        dialog.setResultConverter(btn -> btn == confirmBtn);

        Optional<Boolean> result = dialog.showAndWait();
        return result.orElse(false);
    }

    /**
     * Shows a simple information/error alert in the dark theme.
     */
    public static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        styleAlert(alert);
        alert.showAndWait();
    }

    /**
     * Shows a success notification in the dark theme.
     */
    public static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        styleAlert(alert);
        alert.showAndWait();
    }

    private static void styleAlert(Alert alert) {
        DialogPane pane = alert.getDialogPane();
        pane.setStyle(
            "-fx-background-color: #ffffff; " +
            "-fx-border-color: #cbd5e1;"
        );
        Label contentLabel = (Label) pane.lookup(".content.label");
        if (contentLabel != null) {
            contentLabel.setStyle("-fx-text-fill: #334155; -fx-font-size: 14px;");
        }
    }
}
