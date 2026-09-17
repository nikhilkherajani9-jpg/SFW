package com.sfw.wholesale.ui;

import com.sfw.wholesale.license.HardwareUtils;
import com.sfw.wholesale.license.LicenseClient;
import com.sfw.wholesale.license.LicenseManager;
import com.sfw.wholesale.ui.component.ConfirmDialog;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Screen shown when the app needs a valid product key to continue.
 */
public class ActivationScreen extends Stage {

    private final Runnable onActivationSuccess;

    public ActivationScreen(Runnable onActivationSuccess) {
        this.onActivationSuccess = onActivationSuccess;
        initStyle(StageStyle.UNDECORATED);

        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setStyle(
                "-fx-background-color: #f8f9fa; " +
                "-fx-border-color: #3b82f6; " +
                "-fx-border-width: 2; " +
                "-fx-border-radius: 12; " +
                "-fx-background-radius: 12;");
        root.setPrefSize(500, 450);

        // Logo
        ImageView logoView = new ImageView();
        logoView.setFitWidth(100);
        logoView.setFitHeight(100);
        logoView.setPreserveRatio(true);
        try {
            Image logo = new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/icons/sfw-logo.png")));
            logoView.setImage(logo);
            getIcons().add(logo);
        } catch (Exception ignored) {}

        Label titleLbl = new Label("Product Activation Required");
        titleLbl.setStyle("-fx-text-fill: #1e293b; -fx-font-size: 24px; -fx-font-weight: bold;");

        Label instructionsLbl = new Label("Please enter your product key to activate SFW Wholesale.");
        instructionsLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 14px;");

        // Hardware ID Display
        String hwId = HardwareUtils.getHardwareId();
        Label hwIdLbl = new Label("Hardware ID: " + (hwId != null ? hwId : "Unknown"));
        hwIdLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-family: monospace;");

        // Key Input
        TextField keyField = new TextField();
        keyField.setPromptText("SFW-XXXX-XXXX-XXXX");
        keyField.setStyle("-fx-font-size: 16px; -fx-padding: 10px; -fx-background-radius: 6; -fx-border-radius: 6; -fx-border-color: #cbd5e1;");
        keyField.setMaxWidth(350);

        // Controls
        Button activateBtn = new Button("Activate Online");
        activateBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; -fx-padding: 10 24; -fx-background-radius: 6; -fx-cursor: hand;");
        
        Button exitBtn = new Button("Exit");
        exitBtn.setStyle("-fx-background-color: #e2e8f0; -fx-text-fill: #334155; -fx-font-size: 15px; -fx-padding: 10 24; -fx-background-radius: 6; -fx-cursor: hand;");
        exitBtn.setOnAction(e -> Platform.exit());

        HBox btnBox = new HBox(15, exitBtn, activateBtn);
        btnBox.setAlignment(Pos.CENTER);
        btnBox.setPadding(new Insets(10, 0, 0, 0));

        // Loading state
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setMaxSize(30, 30);
        
        Label statusLbl = new Label();
        statusLbl.setStyle("-fx-font-size: 13px;");
        statusLbl.setVisible(false);

        HBox statusBox = new HBox(10, spinner, statusLbl);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setMinHeight(40);

        activateBtn.setOnAction(e -> {
            String key = keyField.getText().trim();
            if (key.isEmpty()) {
                statusLbl.setText("Please enter a product key.");
                statusLbl.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 13px;");
                statusLbl.setVisible(true);
                return;
            }

            keyField.setDisable(true);
            activateBtn.setDisable(true);
            spinner.setVisible(true);
            statusLbl.setText("Connecting to license server...");
            statusLbl.setStyle("-fx-text-fill: #3b82f6; -fx-font-size: 13px;");
            statusLbl.setVisible(true);

            // Run network call on background thread
            CompletableFuture.supplyAsync(() -> LicenseClient.verifyLicense(key))
                .thenAcceptAsync(result -> {
                    spinner.setVisible(false);
                    keyField.setDisable(false);
                    activateBtn.setDisable(false);

                    if (result.status == LicenseClient.LicenseStatus.VALID) {
                        LicenseManager.saveLocalLicense(key);
                        statusLbl.setText("Activation Successful!");
                        statusLbl.setStyle("-fx-text-fill: #10b981; -fx-font-size: 14px; -fx-font-weight: bold;");
                        
                        // Brief pause to show success, then proceed
                        new Thread(() -> {
                            try { Thread.sleep(1000); } catch (Exception ignored) {}
                            Platform.runLater(() -> {
                                this.close();
                                onActivationSuccess.run();
                            });
                        }).start();
                    } else {
                        statusLbl.setText(result.message);
                        statusLbl.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 13px;");
                    }
                }, Platform::runLater);
        });

        root.getChildren().addAll(logoView, titleLbl, instructionsLbl, hwIdLbl, keyField, btnBox, statusBox);

        Scene scene = new Scene(root, 500, 450);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        setScene(scene);
        centerOnScreen();
    }
}
