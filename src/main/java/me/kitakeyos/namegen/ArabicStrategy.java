/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.kitakeyos.namegen;

import java.util.HashSet;
import java.util.Set;
import me.coley.recaf.control.Controller;
import me.kitakeyos.ArabicCharGenerator;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

/**
 *
 * @author kitakeyos - Hoàng Hữu Dũng
 */
public class ArabicStrategy extends AbstractNameStrategy{
    
    private Set<String> usedClassName = new HashSet<>();
    private Set<String> usedMethodName = new HashSet<>();
    private Set<String> usedFieldName = new HashSet<>();
    private Set<String> usedVariableName = new HashSet<>();

    public ArabicStrategy(Controller controller) {
        super(controller);
    }

    @Override
    public String className(ClassNode node) {
        return ArabicCharGenerator.getUniqueSpecialChar(usedClassName);
    }

    @Override
    public String fieldName(ClassNode owner, FieldNode field) {
        return ArabicCharGenerator.getUniqueSpecialChar(usedFieldName);
    }

    @Override
    public String methodName(ClassNode owner, MethodNode method) {
        return ArabicCharGenerator.getUniqueSpecialChar(usedMethodName);
    }

    @Override
    public String variable(MethodNode method, LocalVariableNode local) {
        return ArabicCharGenerator.getUniqueSpecialChar(usedVariableName);
    }


}
