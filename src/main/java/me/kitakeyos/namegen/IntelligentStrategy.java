package me.kitakeyos.namegen;

import me.coley.recaf.control.Controller;
import me.kitakeyos.analysis.BayesWrapper;
import me.coley.recaf.util.AccessFlag;
import me.coley.recaf.util.ClassUtil;
import me.coley.recaf.util.Log;
import me.coley.recaf.util.TypeUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * A naming strategy that yields an intelligent pattern of renaming classes and
 * their members.
 *
 * @author Matt Coley
 */
public class IntelligentStrategy extends AbstractNameStrategy {

    private double classificationThreshold;

    protected IntelligentStrategy(Controller controller, double classificationThreshold) {
        super(controller);
        this.classificationThreshold = classificationThreshold;
        setupBayes();
    }

    private void setupBayes() {
        try {
            BayesWrapper.init();
        } catch (Exception ex) {
            Log.error(ex, "Failed to initialize naive bayes model!");
        }
    }

    @Override
    public String className(ClassNode node) {
        // Do lookup check first since some calls may populate cached items for parent types
        if (hasClassMapping(node.name)) {
            return addClassMapping(node.name, getCurrentClassName(node.name));
        }
        // Check for parent name
        String baseName = getParentName(node);
        String purposeName = null;
        if (baseName == null) {
            purposeName = analyzePurpose(node);
        }
        // Ensure the parent name matches current mappings
        baseName = matchCurrentMappings(baseName);
        // Common prefix/suffix
        String prefix = "";
        String suffix = "";
        if (AccessFlag.isAnnotation(node.access)) {
            suffix = "Anno";
        } else if (AccessFlag.isInterface(node.access)) {
            prefix = "I";
        } else if (AccessFlag.isAbstract(node.access)) {
            prefix = "Abstract";
        } else if (baseName != null && !baseName.contains("Impl")) {
            suffix = "Impl";
        }
        // Cleanup base name
        if (baseName != null) {
            if (baseName.endsWith(suffix)) {
                baseName = baseName.substring(0, baseName.length() - suffix.length());
            }
            if (!prefix.isEmpty() && baseName.startsWith(prefix)) {
                baseName = baseName.substring(prefix.length());
            }
            // Remove unused abstract
            if (!AccessFlag.isAbstract(node.access) && baseName.contains("Abstract")) {
                baseName = baseName.replace("Abstract", "");
            }
        } else {
            baseName = purposeName;
        }
        // Complete the name
        String middle = baseName == null ? "Obj" : baseName.substring(baseName.lastIndexOf('/') + 1);
        String mapped = prefix + middle + suffix;
        // Skip if output is redundant
        if (node.name.endsWith(mapped)) {
            Log.warn("Skip redundant mapping: {} ---> {}", node.name, mapped);
            return null;
        }
        // Add mapping
        return addClassMapping(node.name, mapped);
    }

    @Override
    public String fieldName(ClassNode owner, FieldNode field) {
        String name;
        Type type = Type.getType(field.desc);
        if (TypeUtil.isPrimitiveDesc(field.desc)) {
            String primType = NameUtils.capitalize(type.getClassName());
            name = "f" + primType;
        } else {
            String internalName = matchCurrentMappings(type.getInternalName());
            String simple = internalName.substring(internalName.lastIndexOf('/') + 1);
            if (simple.contains("[")) {
                simple = simple.replace("[", "Array");
            }
            name = "f" + NameUtils.capitalize(simple);
        }
        String key = fieldKey(owner.name, field.name, field.desc);
        return addFieldMapping(key, name);
    }

    @Override
    public String methodName(ClassNode owner, MethodNode method) {
        // Do not rename methods that belong/inherit from library classes
        if (isLibrary(owner, method)) {
            return null;
        }
        // Yield the name used by the rest of the method hierarchy
        String parentMapped = getParentMethodMappedName(owner, method);
        if (parentMapped != null) {
            return parentMapped;
        }
        // Cant infer anything useful
        if (AccessFlag.isAbstract(method.access)) {
            return null;
        }
        String key = methodKey(owner.name, method.name, method.desc);
        // Check getter
        if (endsInGetter(owner, method)) {
            FieldInsnNode field = getLastFieldInsn(method);
            if (field != null) {
                return addMethodMapping(key, "get" + NameUtils.capitalize(getFieldName(owner, field)));
            }
        }
        // Check setter
        if (endsInSetter(owner, method)) {
            FieldInsnNode field = getLastFieldInsn(method);
            if (field != null) {
                return addMethodMapping(key, "set" + NameUtils.capitalize(getFieldName(owner, field)));
            }
        }
        return null;
    }

    @Override
    public String variable(MethodNode method, LocalVariableNode local) {
        if (TypeUtil.isPrimitiveDesc(local.desc)) {
            return local.desc.toLowerCase() + local.index;
        }
        Type type = Type.getType(local.desc);
        String internalName = matchCurrentMappings(type.getInternalName());
        String simple = internalName.substring(internalName.lastIndexOf('/') + 1);
        return NameUtils.camel(simple) + local.index;
    }

    /**
     * Analyze the class structure and guess what its purpose is.
     *
     * @param node Class to analyze.
     *
     * @return Name for class based on usage.
     */
    private String analyzePurpose(ClassNode node) {
        return BayesWrapper.getPredictedClassification(classificationThreshold,
                BayesWrapper.createClassDataExample(node)).toString();
    }

    /**
     * Check for the following pattern
     * <pre>
     *  ALOAD 0
     *  GETFIELD owner.name : type
     *  ARETURN
     * </pre>
     *
     * @param node Class defining the method.
     * @param method Method definition.
     *
     * @return {@code true} when method matches pattern.
     */
    private static boolean endsInGetter(ClassNode node, MethodNode method) {
        AbstractInsnNode insn = getReturnInsn(method);
        if (insn == null) {
            return false;
        }
        boolean isStatic = AccessFlag.isStatic(method.access);
        // Check last insn is a value return
        int op = insn.getOpcode();
        if (op >= Opcodes.IRETURN && op <= Opcodes.ARETURN) {
            // Check prior insn is getter
            op = (insn = insn.getPrevious()).getOpcode();
            if (op == Opcodes.GETFIELD || (isStatic && op == Opcodes.GETSTATIC)) {
                // Check the field retrieved is from our class
                FieldInsnNode fin = (FieldInsnNode) insn;
                if (!fin.owner.equals(node.name)) {
                    return false;
                }
                // Check prior insn is context
                op = (insn = insn.getPrevious()).getOpcode();
                if (op == Opcodes.ALOAD) {
                    VarInsnNode vin = (VarInsnNode) insn;
                    return vin.var == 0;
                } else {
                    return isStatic;
                }
            }
        }
        return false;
    }

    /**
     * Check for the following pattern
     * <pre>
     *  ALOAD 0
     *  ALOAD 1
     *  PUTFIELD owner.name : type
     * 	RETURN
     * </pre>
     *
     * @param node Class defining the method.
     * @param method Method definition.
     *
     * @return {@code true} when method matches pattern.
     */
    private static boolean endsInSetter(ClassNode node, MethodNode method) {
        AbstractInsnNode insn = getReturnInsn(method);
        if (insn == null) {
            return false;
        }
        boolean isStatic = AccessFlag.isStatic(method.access);
        // Check last insn is a method end (not returning any value)
        int op = insn.getOpcode();
        if (op == Opcodes.RETURN) {
            // Check prior insn is setter
            op = (insn = insn.getPrevious()).getOpcode();
            if (op == Opcodes.PUTFIELD || (isStatic && op == Opcodes.PUTSTATIC)) {
                // Check the field retrieved is from our class
                FieldInsnNode fin = (FieldInsnNode) insn;
                if (!fin.owner.equals(node.name)) {
                    return false;
                }
                // Check prior insn is argument (index=1)
                op = (insn = insn.getPrevious()).getOpcode();
                if (op >= Opcodes.ILOAD && op <= Opcodes.ALOAD) {
                    VarInsnNode vin = (VarInsnNode) insn;
                    // Check prior insn is context (index=0/this)
                    op = (insn = insn.getPrevious()).getOpcode();
                    if (vin.var == 1 && op == Opcodes.ALOAD) {
                        vin = (VarInsnNode) insn;
                        return vin.var == 0;
                    } else {
                        return isStatic;
                    }
                }
            }
        }
        return false;
    }

    /**
     * @param method Method to search.
     *
     * @return Last {@link FieldInsnNode} instruction in the method, ignoring
     * flow control.
     */
    private static FieldInsnNode getLastFieldInsn(MethodNode method) {
        if (method.instructions == null) {
            return null;
        }
        AbstractInsnNode insn = method.instructions.getLast();
        do {
            if (insn.getType() == AbstractInsnNode.FIELD_INSN) {
                return (FieldInsnNode) insn;
            }
            insn = insn.getPrevious();
        } while (insn != null);
        return null;
    }

    /**
     * @param method Method to search.
     *
     * @return Last {@code IRETURN-ARETURN} instruction in the method, ignoring
     * flow control.
     */
    private static AbstractInsnNode getReturnInsn(MethodNode method) {
        if (method.instructions == null) {
            return null;
        }
        AbstractInsnNode insn = method.instructions.getLast();
        do {
            if (insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.ARETURN) {
                return insn;
            }
            insn = insn.getPrevious();
        } while (insn != null);
        return null;
    }

    /**
     * @param owner Class defining the field.
     * @param field Field reference.
     *
     * @return Mapped name of field, or whatever is passed if no mapping found.
     */
    private String getFieldName(ClassNode owner, FieldInsnNode field) {
        String key = fieldKey(owner.name, field.name, field.desc);
        String mapped = getFieldMapping(key);
        if (mapped != null) {
            return mapped;
        }
        return field.name;
    }

    /**
     * @param name Some input name, as internal type.
     *
     * @return The name, with any current mappings applied.
     */
    private String matchCurrentMappings(String name) {
        if (name != null) {
            String currentMapping = null;
            if (hasClassMapping(name)) {
                // Map to existing mapping
                currentMapping = getCurrentClassName(name);
            } else if (getWorkspace().getPrimary().getClasses().containsKey(name)) {
                // No mapping, see what we would map it to if its in the primary workspace
                ClassNode baseClass = ClassUtil.getNode(getWorkspace().getClassReader(name), ClassReader.SKIP_CODE);
                currentMapping = className(baseClass);
            }
            // If a mapping was found, apply
            if (currentMapping != null) {
                name = currentMapping;
            }
        }
        return name;
    }
}
