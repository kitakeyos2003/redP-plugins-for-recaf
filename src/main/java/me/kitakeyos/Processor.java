package me.kitakeyos;

import me.coley.cafedude.InvalidClassException;
import me.coley.cafedude.classfile.ClassFile;
import me.coley.cafedude.io.ClassFileReader;
import me.coley.cafedude.io.ClassFileWriter;
import me.coley.cafedude.transform.IllegalStrippingTransformer;
import me.coley.recaf.control.Controller;
import me.coley.recaf.mapping.Mappings;
import me.coley.recaf.util.ClassUtil;
import me.coley.recaf.util.Log;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Main handler for creating new names and applying them.
 *
 * @author Matt Coley
 */
public class Processor {

    private final Map<String, String> mappings = new ConcurrentHashMap<>();
    private final Controller controller;
    private final RedPlugin plugin;
    private final NameGenerator generator;

    /**
     * @param controller Controller with workspace to pull classes from.
     * @param plugin Plugin with config values.
     */
    public Processor(Controller controller, RedPlugin plugin) {
        this.controller = controller;
        this.plugin = plugin;
        // Configure name generator
        String packageName = plugin.keepPackageLayout ? null : RedPlugin.FLAT_PACKAGE_NAME;
        generator = new NameGenerator(controller, plugin, packageName);
    }

    /**
     * Analyze the given classes and create new names for them and their
     * members.
     *
     * @param matchedNames Set of class names to analyze.
     */
    public void analyze(Set<String> matchedNames) {
        if (plugin.dropMalformedAttributes) {
            pooled("Drop malformed attributes", service -> {
                ClassFileReader cr = new ClassFileReader();
                ClassFileWriter cw = new ClassFileWriter();
                for (Map.Entry<String, byte[]> entry : controller.getWorkspace().getPrimary().getClasses().entrySet()) {
                    if (matchedNames.stream().anyMatch(s -> s.equals(entry.getKey()))) {
                        try {
                            byte[] code = entry.getValue();
                            ClassFile cf = cr.read(code);
                            IllegalStrippingTransformer transformer = new IllegalStrippingTransformer(cf);
                            transformer.transform();
                            byte[] modified = cw.write(cf);
                            if (!Arrays.equals(code, modified)) {
                                entry.setValue(modified);
                                Log.info("Drop malformed attributes from class " + entry.getKey());
                            }
                        } catch (InvalidClassException e) {
                            Log.error("Invalid class", e);
                        }
                    }
                }
            });
        }
        // Reset mappings
        mappings.clear();
        // Analyze each class in separate phases
        // Phase 0: Prepare class nodes
        Set<ClassNode> nodes = collectNodes(matchedNames);

        // Phase 1: Create mappings for class names
        //  - following phases can use these names to enrich their naming logic
        if (plugin.renameClass) {
            pooled("Analyze: Class names", service -> {
                for (ClassNode node : nodes) {
                    service.submit(() -> analyzeClass(node));
                }
            });
        }
        // Phase 2: Create mappings for field names
        //  - methods can now use class and field names to enrich their naming logic
        if (plugin.renameField) {
            pooled("Analyze: Field names", service -> {
                for (ClassNode node : nodes) {
                    service.submit(() -> analyzeFields(node));
                }
            });
        }

        //Phase 3: Create mappings for method names
        if (plugin.renameMethod) {
            pooled("Analyze: Method names", service -> {
                for (ClassNode node : nodes) {
                    service.submit(() -> analyzeMethods(node));
                }
            });
        } else if (plugin.renameVariable) {
            pooled("Analyze: Variable names", service -> {
                for (ClassNode node : nodes) {
                    service.submit(() -> analyzeVariables(node));
                }
            });
        }
    }

    /**
     * @param matchedNames Names of classes to collect.
     *
     * @return Set of nodes from the given names.
     */
    private Set<ClassNode> collectNodes(Set<String> matchedNames) {
        Set<ClassNode> nodes = Collections.newSetFromMap(new ConcurrentHashMap<>());
        pooled("Collect-Nodes", service -> {
            for (String name : matchedNames) {
                service.submit(() -> {
                    ClassReader cr = controller.getWorkspace().getClassReader(name);
                    if (cr == null) {
                        Log.warn("AutoRenamer failed to read class from workspace: " + name);
                        return;
                    }
                    ClassNode node = ClassUtil.getNode(cr, ClassReader.SKIP_FRAMES);
                    nodes.add(node);
                });
            }
        });
        return nodes;
    }

    /**
     * Generate mapping for class.
     *
     * @param node Class to rename.
     */
    private void analyzeClass(ClassNode node) {
        try {
            // Skip special cases: 'module-info'/'package-info'
            if (node.name.matches("(?:[\\w\\/]+\\/)?(?:module|package)-info")) {
                return;
            }
            // Class name
            String oldClassName = node.name;
            String newClassName = generator.createClassName(node);
            if (newClassName != null) {
                mappings.put(oldClassName, newClassName);
            }
        } catch (Throwable t) {
            Log.error(t, "Error occurred in Processor#analyzeClass");
        }
    }

    /**
     * Generate mappings for field names.
     *
     * @param node Class with fields to rename.
     */
    private void analyzeFields(ClassNode node) {
        try {
            // Class name
            String oldClassName = node.name;
            // Field names
            for (FieldNode field : node.fields) {
                String oldFieldName = field.name;
                String newFieldName = generator.createFieldName(node, field);
                if (newFieldName != null) {
                    mappings.put(oldClassName + "." + oldFieldName + " " + field.desc, newFieldName);
                }
            }
        } catch (Throwable t) {
            Log.error(t, "Error occurred in Processor#analyzeFields");
        }
    }

    /**
     * Generate mappings for method names.
     *
     * @param node Class with methods to rename.
     */
    private void analyzeMethods(ClassNode node) {
        try {
            // Class name
            String owner = node.name;
            // Method names
            for (MethodNode method : node.methods) {
                // Skip constructor/static-block
                if (method.name.charAt(0) == '<') {
                    continue;
                }
                String name = method.name;
                String desc = method.desc;
                String newName = generator.createMethodName(node, method);
                if (newName != null) {
                    controller.getWorkspace().getHierarchyGraph().getHierarchyNames(owner).forEach(hierarchyMember -> mappings.put(hierarchyMember + "." + name + desc, newName));
                }
                // Method variable names
                if (!plugin.pruneDebugInfo && method.localVariables != null && plugin.renameVariable) {
                    for (LocalVariableNode local : method.localVariables) {
                        String newLocalName = generator.createVariableName(method, local);
                        // Locals do not get globally mapped, so we handle renaming them locally here
                        if (newLocalName != null) {
                            local.name = newLocalName;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.error(t, "Error occurred in Processor#analyzeMethods");
        }
    }

    /**
     * Generate mappings for variable names.
     *
     * @param node Class with variables to rename.
     */
    private void analyzeVariables(ClassNode node) {
        try {
            for (MethodNode method : node.methods) {
                // Method variable names
                if (method.localVariables != null && plugin.renameVariable) {
                    for (LocalVariableNode local : method.localVariables) {
                        String newLocalName = generator.createVariableName(method, local);
                        // Locals do not get globally mapped, so we handle renaming them locally here
                        if (newLocalName != null) {
                            local.name = newLocalName;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.error(t, "Error occurred in Processor#analyzeMethods");
        }
    }

    /**
     * Applies the mappings created from
     * {@link #analyze(Set) the analysis phase} to the primary resource of the
     * workspace
     */
    public void apply() {
        SortedMap<String, String> sortedMappings = new TreeMap<>(mappings);
        Mappings mapper = new Mappings(controller.getWorkspace());
        mapper.setCheckFieldHierarchy(true);
        mapper.setCheckMethodHierarchy(true);
        if (plugin.pruneDebugInfo) {
            mapper.setClearDebugInfo(true);
        }
        mapper.setMappings(sortedMappings);
        mapper.accept(controller.getWorkspace().getPrimary());
        Log.info("Done auto-mapping! Applied {} mappings", sortedMappings.size());
    }

    /**
     * Run a task that utilizes {@link ExecutorService} for parallel execution.
     * Pooled
     *
     * @param phaseName Task name.
     * @param task Task to run.
     */
    private void pooled(String phaseName, Consumer<ExecutorService> task) {
        try {
            long start = System.currentTimeMillis();
            Log.info("AutoRename Processing: Task '{}' starting", phaseName);
            ExecutorService service;
            if (generator.allowMultiThread()) {
                service = Executors.newFixedThreadPool(getThreadCount());
            } else {
                service = Executors.newSingleThreadExecutor();
            }
            task.accept(service);
            service.shutdown();
            service.awaitTermination(plugin.phaseTimeout, TimeUnit.SECONDS);
            Log.info("AutoRename Processing: Task '{}' completed in {}ms", phaseName, (System.currentTimeMillis() - start));
        } catch (Throwable t) {
            Log.error(t, "Failed processor phase '{}', reason: {}", phaseName, t.getMessage());
        }
    }

    private static int getThreadCount() {
        return Runtime.getRuntime().availableProcessors();
    }
}
