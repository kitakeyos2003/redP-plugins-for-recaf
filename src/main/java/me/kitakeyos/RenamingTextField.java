/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.kitakeyos;

import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import me.coley.recaf.control.gui.GuiController;
import me.coley.recaf.mapping.Mappings;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import me.coley.recaf.ui.controls.ViewportTabs;

/**
 * A popup textfield for renaming classes and members.
 *
 * @author Matt
 */
public class RenamingTextField extends PopupWindow {

    private final GuiController controller;
    private String packageName;
    private CheckBox checkBox;
    private TextField oldPackage, newPackage;
    private TextField prefix, suffix;
    private byte type;
    private Supplier<Map<String, String>> mapSupplier;
    private Consumer<Map<String, String>> onRename;

    private RenamingTextField(GuiController controller, String initialText, Consumer<RenamingTextField> renameAction, boolean isDefaultPackage) {
        this.controller = controller;
        this.type = 0;
        setHideOnEscape(true);
        setAutoHide(true);

        // Create checkbox
        checkBox = new CheckBox("Default package");
        checkBox.setSelected(isDefaultPackage);

        // Create old package textbox
        oldPackage = new TextField();
        oldPackage.setPromptText("Enter old package");
        oldPackage.setPrefWidth(400);
        oldPackage.disableProperty().bind(checkBox.selectedProperty());

        // Create new package textbox
        newPackage = new TextField();
        newPackage.setPromptText("Enter new package");
        newPackage.setPrefWidth(400);

        setOnShown(e -> {
            // Disable root so key events do not get passed to the window that owns the rename popup.
            controller.windows().getMainWindow().getRoot().setDisable(true);

            // Center on main window
            Stage main = controller.windows().getMainWindow().getStage();
            int x = (int) (main.getX() + Math.round((main.getWidth() / 2) - (getWidth() / 2)));
            int y = (int) (main.getY() + Math.round((main.getHeight() / 2) - (getHeight() / 2)));
            setX(x);
            setY(y);
        });

        setOnHiding(e -> {
            // Re-enable root after completion/cancellation
            controller.windows().getMainWindow().getRoot().setDisable(false);
        });

        // Create button
        Button renameButton = new Button("Rename");
        renameButton.setOnAction(e -> renameAction.accept(this));

        // Combine checkbox, old package textbox, new package textbox, and button in a VBox
        VBox vbox = new VBox(checkBox, oldPackage, newPackage, renameButton);
        vbox.setSpacing(10);
        vbox.setPadding(new Insets(10));

        // Close on hitting escape/close-window bind
        vbox.setOnKeyPressed(e -> {
            if (controller.config().keys().closeWindow.match(e) || e.getCode() == KeyCode.ESCAPE) {
                hide();
            }
        });

        // Setup & show
        getScene().setRoot(vbox);
        Platform.runLater(() -> {
            if (isDefaultPackage) {
                newPackage.requestFocus();
                newPackage.setText(initialText);
                newPackage.selectAll();
            } else {
                oldPackage.requestFocus();
                newPackage.setText(initialText);
            }
        });
        setMapSupplier();
    }

    private RenamingTextField(GuiController controller, Consumer<RenamingTextField> renameAction, String packageName) {
        this.controller = controller;
        this.type = 1;
        this.packageName = packageName;
        setHideOnEscape(true);
        setAutoHide(true);
        Label lbPrefix = new Label("Prefix");
        Label lbSuffix = new Label("Suffix");
        prefix = new TextField();
        suffix = new TextField();

        // Set prompt texts for text fields
        prefix.setPromptText("Prefix");
        suffix.setPromptText("Suffix");
        Button addButton = new Button("Add");

        // Create a GridPane to hold UI elements
        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(10));
        gridPane.setHgap(10);
        gridPane.setVgap(10);

        // Add UI elements to the GridPane
        gridPane.add(lbPrefix, 0, 0);
        gridPane.add(prefix, 1, 0);
        gridPane.add(lbSuffix, 0, 1);
        gridPane.add(suffix, 1, 1);
        gridPane.add(addButton, 1, 2);
        gridPane.setHalignment(addButton, HPos.RIGHT);

        setOnShown(e -> {
            // Disable root so key events do not get passed to the window that owns the rename popup.
            controller.windows().getMainWindow().getRoot().setDisable(true);

            // Center on main window
            Stage main = controller.windows().getMainWindow().getStage();
            int x = (int) (main.getX() + Math.round((main.getWidth() / 2) - (getWidth() / 2)));
            int y = (int) (main.getY() + Math.round((main.getHeight() / 2) - (getHeight() / 2)));
            setX(x);
            setY(y);
        });

        setOnHiding(e -> {
            // Re-enable root after completion/cancellation
            controller.windows().getMainWindow().getRoot().setDisable(false);
        });

        // Create button
        addButton.setOnAction(e -> renameAction.accept(this));

        // Close on hitting escape/close-window bind
        gridPane.setOnKeyPressed(e -> {
            if (controller.config().keys().closeWindow.match(e) || e.getCode() == KeyCode.ESCAPE) {
                hide();
            }
        });

        // Setup & show
        getScene().setRoot(gridPane);
        Platform.runLater(() -> {
            prefix.requestFocus();
            prefix.selectAll();

        });
        setMapSupplier();
    }

    public void setMapSupplier() {
        if (type == 0) {
            this.mapSupplier = new Supplier<Map<String, String>>() {
                @Override
                public Map<String, String> get() {
                    if (checkBox.isSelected()) {
                        String renamed = newPackage.getText();
                        Map<String, String> map = new HashMap<>();
                        // Map all classes in the package
                        controller.getWorkspace().getPrimaryClassNames().stream()
                                .filter(n -> !n.contains("/")).forEach(n -> map.put(n, renamed + "/" + n));
                        return map;
                    } else {
                        String renamed = newPackage.getText();
                        String oldName = oldPackage.getText();
                        Map<String, String> map = new HashMap<>();
                        // Map all classes in the package
                        String prefix = oldName + "/";
                        controller.getWorkspace().getPrimaryClassNames().stream()
                                .filter(n -> n.startsWith(prefix))
                                .forEach(n -> map.put(n, renamed + "/" + n.substring(oldName.length() + 1)));
                        return map;
                    }
                }
            };
        } else if (type == 1) {
            this.mapSupplier = new Supplier<Map<String, String>>() {
                @Override
                public Map<String, String> get() {

                    String pf = prefix.getText();
                    String sf = suffix.getText();
                    if (packageName == null) {
                        Map<String, String> map = new HashMap<>();
                        // Map all classes in the package
                        controller.getWorkspace().getPrimaryClassNames().stream().forEach(n -> {
                            String pkgName = n;
                            String[] nP = pkgName.split("/");
                            String viewName = nP[nP.length - 1];

                            String newName = pf + viewName + sf;
                            nP[nP.length - 1] = newName;

                            String result = String.join("/", nP);
                            map.put(n, result);
                        });
                        return map;
                    } else {
                        Map<String, String> map = new HashMap<>();
                        // Map all classes in the package
                        String prefix = packageName + "/";
                        controller.getWorkspace().getPrimaryClassNames().stream()
                                .filter(n -> n.startsWith(prefix))
                                .forEach(n -> {
                                    String name = n.substring(packageName.length() + 1);
                                    if (!name.contains("/")) {
                                        String newName = pf + name + sf;
                                        map.put(n, packageName + "/" + newName);
                                    }
                                });
                        return map;
                    }
                }
            };
        }
    }

    /**
     * @param onRename Action to run on the mappings.
     */
    public void setOnRename(Consumer<Map<String, String>> onRename) {
        this.onRename = onRename;
    }

    /**
     * Create a renaming field for packages.
     *
     * @param controller Controller to act on.
     * @param name Package name.
     *
     * @return Renaming field popup.
     */
    public static RenamingTextField renamePackage(GuiController controller, String name) {
        RenamingTextField popup = new RenamingTextField(controller, "me/kitakeyos", RenamingTextField::defaultAction, false);
        popup.oldPackage.setText(name);
        // Close class tabs with old names & open the new ones
        popup.setOnRename((renamed) -> renamed.forEach((oldName, newName) -> {
            // Get old tab index
            Tab tab = controller.windows().getMainWindow().getTabs().getTab(oldName);
            if (tab == null) {
                return;
            }
            int oldIndex = controller.windows().getMainWindow().getTabs().getTabs().indexOf(tab);
            if (oldIndex == -1) {
                return;
            }
            // Close old tab
            controller.windows().getMainWindow().getTabs().closeTab(oldName);
            // Open new tab and move to old index
            controller.windows().getMainWindow().openClass(controller.getWorkspace().getPrimary(), newName);
            tab = controller.windows().getMainWindow().getTabs().getTab(newName);
            controller.windows().getMainWindow().getTabs().getTabs().remove(tab);
            controller.windows().getMainWindow().getTabs().getTabs().add(oldIndex, tab);
            controller.windows().getMainWindow().getTabs().select(tab);
        }));
        return popup;
    }

    /**
     * Create a renaming field for default packages.
     *
     * @param controller Controller to act on.
     * @param name Package name.
     *
     * @return Renaming field popup.
     */
    public static RenamingTextField renameDefaultPackage(GuiController controller) {
        RenamingTextField popup = new RenamingTextField(controller, "me/kitakeyos", RenamingTextField::defaultAction, true);
        // Close class tabs with old names & open the new ones
        popup.setOnRename((renamed) -> renamed.forEach((oldName, newName) -> {
            // Get old tab index
            Tab tab = controller.windows().getMainWindow().getTabs().getTab(oldName);
            if (tab == null) {
                return;
            }
            int oldIndex = controller.windows().getMainWindow().getTabs().getTabs().indexOf(tab);
            if (oldIndex == -1) {
                return;
            }
            // Close old tab
            controller.windows().getMainWindow().getTabs().closeTab(oldName);
            // Open new tab and move to old index
            controller.windows().getMainWindow().openClass(controller.getWorkspace().getPrimary(), newName);
            tab = controller.windows().getMainWindow().getTabs().getTab(newName);
            controller.windows().getMainWindow().getTabs().getTabs().remove(tab);
            controller.windows().getMainWindow().getTabs().getTabs().add(oldIndex, tab);
            controller.windows().getMainWindow().getTabs().select(tab);
        }));
        return popup;
    }

    public static RenamingTextField addPrefixOrSuffix(GuiController controller, String packageName) {
        RenamingTextField popup = new RenamingTextField(controller, RenamingTextField::defaultAction, packageName);
        // Close class tabs with old names & open the new ones
        popup.setOnRename((renamed) -> renamed.forEach((oldName, newName) -> {
            // Get old tab index
            Tab tab = controller.windows().getMainWindow().getTabs().getTab(oldName);
            if (tab == null) {
                return;
            }
            int oldIndex = controller.windows().getMainWindow().getTabs().getTabs().indexOf(tab);
            if (oldIndex == -1) {
                return;
            }
            // Close old tab
            controller.windows().getMainWindow().getTabs().closeTab(oldName);
            // Open new tab and move to old index
            controller.windows().getMainWindow().openClass(controller.getWorkspace().getPrimary(), newName);
            tab = controller.windows().getMainWindow().getTabs().getTab(newName);
            controller.windows().getMainWindow().getTabs().getTabs().remove(tab);
            controller.windows().getMainWindow().getTabs().getTabs().add(oldIndex, tab);
            controller.windows().getMainWindow().getTabs().select(tab);
        }));
        return popup;
    }

    private static void defaultAction(RenamingTextField field) {
        // Apply mappings
        Map<String, String> map = field.mapSupplier.get();
        Mappings mappings = new Mappings(field.controller.getWorkspace());
        mappings.setMappings(map);
        mappings.accept(field.controller.getWorkspace().getPrimary());
        // Refresh affected tabs
        ViewportTabs tabs = field.controller.windows().getMainWindow().getTabs();
        for (String updated : field.controller.getWorkspace().getDefinitionUpdatedClasses()) {
            if (tabs.isOpen(updated)) {
                tabs.getClassViewport(updated).updateView();
            }
        }
        // Close popup
        field.hide();
        field.onRename.accept(map);
    }

}
