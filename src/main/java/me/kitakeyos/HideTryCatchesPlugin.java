/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.kitakeyos;

import java.util.HashMap;
import java.util.Map;
import me.coley.recaf.control.Controller;
import me.coley.recaf.plugin.api.StartupPlugin;
import me.coley.recaf.plugin.api.WorkspacePlugin;
import me.coley.recaf.workspace.Workspace;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.plugface.core.annotations.Plugin;

/**
 *
 * @author kitakeyos - Hoàng Hữu Dũng
 */
@Plugin(name = "HideTryCatches Plugin")
public class HideTryCatchesPlugin implements WorkspacePlugin, StartupPlugin {

    private Controller controller;
    private Workspace workspace;

    public void hideTryCatches() {
        if (workspace == null) {
            return;
        }

        for (Map.Entry<String, byte[]> entry : workspace.getPrimary().getClasses().entrySet()) {
            ClassReader reader = new ClassReader(entry.getValue());
            ClassNode classNode = new ClassNode();

            reader.accept(classNode, 0);

            classNode.methods.forEach(m -> m.tryCatchBlocks.clear());

            ClassWriter classWriter = new ClassWriter(0);
            classNode.accept(classWriter);

            entry.setValue(classWriter.toByteArray());
        }
    }

    @Override
    public String getName() {
        return "HideTryCatches";
    }

    @Override
    public String getVersion() {
        return "1.0.1";
    }

    @Override
    public String getDescription() {
        return "A plugin for Recaf for hiding try catches";
    }

    @Override
    public void onClosed(Workspace workspace) {
        this.workspace = workspace;
    }

    @Override
    public void onOpened(Workspace workspace) {
        this.workspace = workspace;

        hideTryCatches();
    }

    public Controller getController() {
        return controller;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    @Override
    public void onStart(Controller controller) {
        this.controller = controller;
        hideTryCatches();
    }
}
