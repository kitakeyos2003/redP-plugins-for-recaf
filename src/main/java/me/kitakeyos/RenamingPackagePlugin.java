package me.kitakeyos;

import javafx.scene.control.ContextMenu;
import javafx.stage.Stage;
import me.coley.recaf.control.Controller;
import me.coley.recaf.control.gui.GuiController;
import me.coley.recaf.plugin.api.*;
import me.coley.recaf.ui.ContextBuilder;
import me.coley.recaf.ui.controls.ActionMenuItem;
import me.coley.recaf.workspace.JavaResource;
import org.plugface.core.annotations.Plugin;

@Plugin(name = "Renamer")
public class RenamingPackagePlugin implements StartupPlugin, ContextMenuInjectorPlugin {

    Controller controller;
    @Override
    public String getVersion() {
        return "1.0.1";
    }

    @Override
    public String getDescription() {
        return "Rename package";
    }

    @Override
    public void forPackage(ContextBuilder builder, ContextMenu menu, String name) {
        menu.getItems().add(new ActionMenuItem("Rename package", () -> {
            GuiController guiController = builder.getController();
            RenamingTextField renamingTextField = RenamingTextField.renamePackage(guiController, name.replaceAll("\\.", "/"));
            Stage stage = guiController.windows().getMainWindow().getStage();
            renamingTextField.show(stage);
        }));
        menu.getItems().add(new ActionMenuItem("Add Prefix/Suffix to Classes in Package", () -> {
            GuiController guiController = builder.getController();
            RenamingTextField renamingTextField = RenamingTextField.addPrefixOrSuffix(guiController, name.replaceAll("\\.", "/"));
            Stage stage = guiController.windows().getMainWindow().getStage();
            renamingTextField.show(stage);
        }));
    }
    
    

    @Override
    public void forResourceRoot(ContextBuilder builder, ContextMenu menu, JavaResource resource) {
        menu.getItems().add(new ActionMenuItem("Rename default package", () -> {
            GuiController guiController = builder.getController();
            RenamingTextField renamingTextField = RenamingTextField.renameDefaultPackage(guiController);
            Stage stage = guiController.windows().getMainWindow().getStage();
            renamingTextField.show(stage);
        }));
        menu.getItems().add(new ActionMenuItem("Add prefix/suffix for all classes ", () -> {
            GuiController guiController = builder.getController();
            RenamingTextField renamingTextField = RenamingTextField.addPrefixOrSuffix(guiController, null);
            Stage stage = guiController.windows().getMainWindow().getStage();
            renamingTextField.show(stage);
        }));
    }
    

    @Override
    public void onStart(Controller controller) {
        this.controller = controller;
    }
    
    

}
