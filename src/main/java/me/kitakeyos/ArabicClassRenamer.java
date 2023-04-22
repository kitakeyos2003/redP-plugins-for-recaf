/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.kitakeyos;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javafx.scene.control.Tab;
import me.coley.recaf.control.gui.GuiController;
import me.coley.recaf.mapping.Mappings;
import me.coley.recaf.ui.controls.ViewportTabs;

/**
 *
 * @author kitakeyos - Hoàng Hữu Dũng
 */
public class ArabicClassRenamer {

    private GuiController controller;
    private String packageName;
    private Supplier<Map<String, String>> mapSupplier;
    private Consumer<Map<String, String>> onRename;

    public ArabicClassRenamer(GuiController controller, String packageName) {
        this.controller = controller;
        this.packageName = packageName;
        setMapSupplier();
        setOnRename((renamed) -> renamed.forEach((oldName, newName) -> {
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
    }

    public void setMapSupplier() {
        this.mapSupplier = new Supplier<Map<String, String>>() {
            @Override
            public Map<String, String> get() {
                if (packageName == null) {
                    List<String> list = controller.getWorkspace().getPrimaryClassNames().stream().collect(Collectors.toList());
                    return ArabicCharGenerator.generateRandomSpecialCharMap(list);
                } else {
                    String prefix = packageName + "/";
                    List<String> list = controller.getWorkspace().getPrimaryClassNames().stream()
                            .filter(n -> n.startsWith(prefix) && !n.substring(packageName.length() + 1).contains("/"))
                            .collect(Collectors.toList());
                    return ArabicCharGenerator.generateRandomSpecialCharMap(list);
                }
            }
        };
    }

    public void setOnRename(Consumer<Map<String, String>> onRename) {
        this.onRename = onRename;
    }

    public void execute() {
        // Apply mappings
        Map<String, String> map = mapSupplier.get();
        Mappings mappings = new Mappings(controller.getWorkspace());
        mappings.setMappings(map);
        mappings.accept(controller.getWorkspace().getPrimary());
        // Refresh affected tabs
        ViewportTabs tabs = controller.windows().getMainWindow().getTabs();
        for (String updated : controller.getWorkspace().getDefinitionUpdatedClasses()) {
            if (tabs.isOpen(updated)) {
                tabs.getClassViewport(updated).updateView();
            }
        }
        onRename.accept(map);
    }
}
