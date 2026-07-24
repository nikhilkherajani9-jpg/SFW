package com.sfw.wholesale.ui.component;

import com.sfw.wholesale.service.LegacyImportService;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

public class LocationMappingDialog extends Stage {

    private boolean proceed = false;

    public LocationMappingDialog(List<LegacyImportService.ParsedLegacyData> invalidData) {
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Fix Unknown Locations");
        
        Label instruction = new Label("The following files have unknown locations in their filenames.\n" +
                                      "Please double-click the 'New Location' cells to type a valid location (e.g., G1, RK2).\n" +
                                      "Click 'Continue Import' when done.");
        instruction.setStyle("-fx-text-fill: #b91c1c; -fx-font-weight: bold;");
        
        TableView<LegacyImportService.ParsedLegacyData> table = new TableView<>();
        table.setEditable(true);
        table.getItems().addAll(invalidData);
        
        TableColumn<LegacyImportService.ParsedLegacyData, String> fileCol = new TableColumn<>("File Name");
        fileCol.setCellValueFactory(new PropertyValueFactory<>("originalFileName"));
        fileCol.setPrefWidth(250);
        
        TableColumn<LegacyImportService.ParsedLegacyData, String> oldLocCol = new TableColumn<>("Extracted");
        oldLocCol.setCellValueFactory(new PropertyValueFactory<>("extractedLocation"));
        oldLocCol.setPrefWidth(100);
        
        TableColumn<LegacyImportService.ParsedLegacyData, String> newLocCol = new TableColumn<>("New Location");
        newLocCol.setCellValueFactory(new PropertyValueFactory<>("mappedLocation"));
        newLocCol.setCellFactory(TextFieldTableCell.forTableColumn());
        newLocCol.setOnEditCommit(event -> {
            LegacyImportService.ParsedLegacyData row = event.getRowValue();
            row.mappedLocation = event.getNewValue().trim().toUpperCase();
        });
        newLocCol.setPrefWidth(100);
        newLocCol.setStyle("-fx-background-color: #fef08a;"); // Highlight editable column
        
        table.getColumns().add(fileCol);
        table.getColumns().add(oldLocCol);
        table.getColumns().add(newLocCol);
        
        Button continueBtn = new Button("Continue Import");
        continueBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold;");
        continueBtn.setOnAction(e -> {
            // Validate all mapped locations before proceeding
            boolean allValid = true;
            for (LegacyImportService.ParsedLegacyData d : table.getItems()) {
                if (!LegacyImportService.VALID_LOCATIONS.contains(d.mappedLocation)) {
                    allValid = false;
                    break;
                }
            }
            if (!allValid) {
                ConfirmDialog.showError("Invalid Location", "One or more locations are still invalid.\nValid locations are: " + String.join(", ", LegacyImportService.VALID_LOCATIONS));
                return;
            }
            proceed = true;
            close();
        });
        
        VBox layout = new VBox(15, instruction, table, continueBtn);
        layout.setPadding(new Insets(20));
        
        Scene scene = new Scene(layout, 500, 400);
        setScene(scene);
    }
    
    public boolean showAndAwaitProceed() {
        showAndWait();
        return proceed;
    }
}
