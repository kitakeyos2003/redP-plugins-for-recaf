package me.kitakeyos;

import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import me.coley.recaf.config.Conf;
import me.coley.recaf.config.FieldWrapper;
import me.coley.recaf.control.Controller;
import me.coley.recaf.control.gui.GuiController;
import me.coley.recaf.plugin.api.ConfigurablePlugin;
import me.coley.recaf.plugin.api.ContextMenuInjectorPlugin;
import me.coley.recaf.plugin.api.StartupPlugin;
import me.kitakeyos.namefilter.NamingScope;
import me.kitakeyos.namegen.NamingPattern;
import me.coley.recaf.ui.ContextBuilder;
import me.coley.recaf.ui.controls.ActionMenuItem;
import me.coley.recaf.ui.controls.NumberSlider;
import me.coley.recaf.workspace.JavaResource;
import org.plugface.core.annotations.Plugin;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.stage.Stage;
import me.coley.recaf.util.Log;

/**
 * A plugin that adds context menus to decompile a class, a package, or the
 * entire program all at once. The results are bundled as a ZIP file and placed
 * at a requested location.
 *
 * @author Matt Coley
 */
@Plugin(name = "REDP")
public class RedP implements StartupPlugin, ContextMenuInjectorPlugin, ConfigurablePlugin {
    public static final String FLAT_PACKAGE_NAME = "renamed/";
    // Config keys
    private static final String KEEP_P_STRUCT = "Keep package layout";
    private static final String NAME_PATTERN = "Naming pattern";
    private static final String NAME_SCOPE = "Naming scope";
    private static final String SHORT_CUTOFF = "Short name cutoff";
    private static final String PRUNE_DEBUG = "Remove debug info";
    private static final String INTELLI_THRESH = "Intelligent guess (%) threshold";
    
    private static final String RENAME_CLASS = "Rename class";
    private static final String RENAME_METHOD = "Rename method";
    private static final String RENAME_FIELD = "Rename field";
    private static final String RENAME_VARIABLE = "Rename variable";
    private static final String DROP_MALFORMED_ATTRIBUTES = "Drop malformed attributes from classes added by obfuscators";
    
    private Controller controller;

    @Conf(value = NAME_PATTERN, noTranslate = true)
    public NamingPattern namingPattern = NamingPattern.SIMPLE;

    @Conf(value = NAME_SCOPE, noTranslate = true)
    public NamingScope namingScope = NamingScope.ALL;

    @Conf(value = SHORT_CUTOFF, noTranslate = true)
    public long cutoffNameLen = 4;

    @Conf(value = INTELLI_THRESH, noTranslate = true)
    public long intelligentGuessThreshold = 30;

    @Conf(value = KEEP_P_STRUCT, noTranslate = true)
    public boolean keepPackageLayout = true;

    @Conf(value = PRUNE_DEBUG, noTranslate = true)
    public boolean pruneDebugInfo;
    
    @Conf(value = RENAME_CLASS, noTranslate = true)
    public boolean renameClass = true;
    @Conf(value = RENAME_METHOD, noTranslate = true)
    public boolean renameMethod = false;
    @Conf(value = RENAME_FIELD, noTranslate = true)
    public boolean renameField = true;
    @Conf(value = RENAME_VARIABLE, noTranslate = true)
    public boolean renameVariable = true;
    @Conf(value = DROP_MALFORMED_ATTRIBUTES, noTranslate = true)
    public boolean dropMalformedAttributes = true;

    // TODO: Should this be a modifiable conf value, or just a reasonable const?
    public int phaseTimeout = 10;

    @Override
    public String getVersion() {
        return "1.0.2";
    }

    @Override
    public String getDescription() {
        return "Allows classes and packages to be renamed automatically";
    }

    @Override
    public void onStart(Controller controller) {
        this.controller = controller;
    }

    @Override
    public void addFieldEditors(Map<String, Function<FieldWrapper, Node>> editors) {
        editors.put(SHORT_CUTOFF, field -> new NumberSlider<Integer>((GuiController) controller, field, 1, 30, 1));
        editors.put(INTELLI_THRESH, field -> new NumberSlider<Integer>((GuiController) controller, field, 10, 100, 5));
    }

    @Override
    public void forPackage(ContextBuilder builder, ContextMenu menu, String name) {
        menu.getItems().add(new ActionMenuItem("Auto rename classes",
                () -> rename(name.replaceAll("\\.", "/") + "/.*", builder.getResource())));
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
    public void forClass(ContextBuilder builder, ContextMenu menu, String name) {
        menu.getItems().add(new ActionMenuItem("Auto rename class",
                () -> rename(Collections.singleton(name), builder.getResource())));
    }

    @Override
    public void forResourceRoot(ContextBuilder builder, ContextMenu menu, JavaResource resource) {
        menu.getItems().add(new ActionMenuItem("Auto rename all",
                () -> rename(".*", resource)));
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

    private void rename(String namePattern, JavaResource resource) {
        Log.debug("namePattern: " + namePattern);
        Set<String> matchedNames = resource.getClasses().keySet().stream()
                .filter(name -> name.matches(namePattern))
                .collect(Collectors.toSet());
        rename(matchedNames, resource);
    }

    private void rename(Set<String> matchedNames, JavaResource resource) {
        Processor processor = new Processor(controller, this);
        processor.analyze(matchedNames);
        processor.apply();
    }

    @Override
    public String getConfigTabTitle() {
        return "REDP";
    }
}
