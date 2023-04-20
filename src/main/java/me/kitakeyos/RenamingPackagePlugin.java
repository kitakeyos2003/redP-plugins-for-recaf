package me.kitakeyos;

import java.util.Map;
import javafx.scene.control.ContextMenu;
import javafx.stage.Stage;
import me.coley.recaf.config.ConfKeybinding;
import me.coley.recaf.control.gui.GuiController;
import me.coley.recaf.plugin.api.*;
import me.coley.recaf.ui.ContextBuilder;
import me.coley.recaf.ui.controls.ActionMenuItem;
import org.plugface.core.annotations.Plugin;

@Plugin(name = "Renaming package")
public class RenamingPackagePlugin implements BasePlugin, ContextMenuInjectorPlugin, KeybindProviderPlugin {

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Renaming default package";
    }

    @Override
    public void forClass(ContextBuilder builder, ContextMenu menu, String name) {
        menu.getItems().add(new ActionMenuItem("Renaming default package", () -> {
            GuiController guiController = builder.getController();
            RenamingTextField renamingTextField = RenamingTextField.forDefaultPackage(guiController);
            Stage stage = guiController.windows().getMainWindow().getStage();
            renamingTextField.show(stage);
        }));
    }

    @Override
    public Map<ConfKeybinding.Binding, Runnable> createGlobalBindings() {
        return KeybindProviderPlugin.super.createGlobalBindings(); //To change body of generated methods, choose Tools | Templates.
    }
    
    

}
