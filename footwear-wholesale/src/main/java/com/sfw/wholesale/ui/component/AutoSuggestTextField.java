package com.sfw.wholesale.ui.component;

import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class AutoSuggestTextField extends TextField {

    private final ObservableList<String> allSuggestions;
    private final Popup popup = new Popup();
    private final ListView<String> listView = new ListView<>();
    private Consumer<String> onSuggestionSelected;
    private boolean suppressPopup = false;
    private boolean autoSelectSingleOptionOnEnter = false;

    public AutoSuggestTextField(ObservableList<String> suggestions) {
        this.allSuggestions = suggestions;
        
        popup.setAutoHide(true);
        listView.setMaxHeight(220);
        listView.setStyle("-fx-background-color: #ffffff; -fx-border-color: #3b82f6; -fx-font-size: 13px;");
        popup.getContent().add(listView);

        textProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressPopup) return;
            if (newVal == null || newVal.isBlank()) {
                popup.hide();
                return;
            }
            updatePopup(newVal);
        });

        focusedProperty().addListener((obs, was, is) -> {
            if (!is) popup.hide();
        });
        
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DOWN && popup.isShowing()) {
                int idx = listView.getSelectionModel().getSelectedIndex();
                if (idx < listView.getItems().size() - 1) {
                    listView.getSelectionModel().select(idx + 1);
                    listView.scrollTo(idx + 1);
                } else if (idx == -1 && !listView.getItems().isEmpty()) {
                    listView.getSelectionModel().selectFirst();
                }
                e.consume();
            } else if (e.getCode() == KeyCode.UP && popup.isShowing()) {
                int idx = listView.getSelectionModel().getSelectedIndex();
                if (idx > 0) {
                    listView.getSelectionModel().select(idx - 1);
                    listView.scrollTo(idx - 1);
                }
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER && popup.isShowing()) {
                String selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    selectItem(selected);
                    e.consume();
                } else if (autoSelectSingleOptionOnEnter && listView.getItems().size() == 1) {
                    selectItem(listView.getItems().get(0));
                    e.consume();
                }
            }
        });
        
        listView.setFocusTraversable(false);
        listView.setCellFactory(lv -> {
            javafx.scene.control.ListCell<String> cell = new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("-fx-background-color: transparent;");
                    } else {
                        setText(item);
                        if (isSelected()) {
                            setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #1e3a8a; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-background-color: transparent; -fx-text-fill: #334155; -fx-font-weight: normal;");
                        }
                    }
                }
            };
            cell.selectedProperty().addListener((obs, was, is) -> {
                if (is) {
                    cell.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #1e3a8a; -fx-font-weight: bold;");
                } else if (!cell.isEmpty()) {
                    cell.setStyle("-fx-background-color: transparent; -fx-text-fill: #334155; -fx-font-weight: normal;");
                }
            });
            cell.setOnMousePressed(e -> {
                if (!cell.isEmpty()) {
                    selectItem(cell.getItem());
                    e.consume();
                }
            });
            return cell;
        });
        
        // Removed old listView key/mouse handlers since they are now handled by cell mouse press and TextField key filter.
    }

    public void setOnSuggestionSelected(Consumer<String> handler) {
        this.onSuggestionSelected = handler;
    }

    public void setAutoSelectSingleOptionOnEnter(boolean autoSelect) {
        this.autoSelectSingleOptionOnEnter = autoSelect;
    }

    public void setTextSilent(String text) {
        suppressPopup = true;
        try {
            setText(text);
            popup.hide();
        } finally {
            suppressPopup = false;
        }
    }

    private void updatePopup(String typed) {
        String lower = typed.toLowerCase();
        List<String> matches = allSuggestions.stream()
                .filter(s -> s.toLowerCase().contains(lower))
                .limit(20)
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            popup.hide();
            return;
        }

        listView.getItems().setAll(matches);

        if (getScene() != null && getScene().getWindow() != null) {
            Bounds bounds = localToScreen(getBoundsInLocal());
            if (bounds != null) {
                listView.setPrefWidth(bounds.getWidth());
                popup.show(this, bounds.getMinX(), bounds.getMaxY());
            }
        }
    }
    
    private void selectItem(String match) {
        setTextSilent(match);
        positionCaret(match.length());
        if (onSuggestionSelected != null) onSuggestionSelected.accept(match);
        requestFocus();
    }
}
