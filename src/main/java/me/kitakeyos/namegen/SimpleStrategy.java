package me.kitakeyos.namegen;

import me.coley.recaf.control.Controller;
import me.kitakeyos.Processor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * A naming strategy that yields a basic pattern of incrementing numbers. IE,
 * Class1, Class2, etc.
 *
 * @author Matt Coley
 */
public class SimpleStrategy extends AbstractNameStrategy {

    private int classIndex = 1;
    private int fieldIndex = 1;
    private int methodIndex = 1;
    private final Processor processor;

    public SimpleStrategy(Controller controller, Processor processor) {
        super(controller);
        this.processor = processor;
    }

    @Override
    public boolean allowMultiThread() {
        return false;
    }

    @Override
    public String className(ClassNode node) {
        return "C" + (classIndex++);
    }

    @Override
    public String fieldName(ClassNode owner, FieldNode field) {
        return "f" + (fieldIndex++);
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
        return "m" + (methodIndex++);
    }

    @Override
    public String variable(MethodNode method, LocalVariableNode local) {
        return "lc" + local.index;
    }
}
