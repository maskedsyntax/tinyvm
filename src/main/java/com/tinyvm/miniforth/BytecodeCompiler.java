package com.tinyvm.miniforth;

import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.objectweb.asm.Opcodes.*;

/**
 * Compiles MiniForth bytecode into JVM bytecode using the ASM library.
 * This provides a significant performance boost over the interpreted VM loop.
 */
public class BytecodeCompiler extends ClassLoader {

    private static final Logger log = LoggerFactory.getLogger(BytecodeCompiler.class);
    private static final String CLASS_NAME = "com/tinyvm/miniforth/GeneratedProgram";
    private static final String VM_TYPE = "Lcom/tinyvm/miniforth/ForthVM;";
    private static final String VM_INTERNAL_NAME = "com/tinyvm/miniforth/ForthVM";

    /**
     * Compiles a word's MiniForth instructions into a native JVM method and returns a Consumer.
     */
    public Consumer<ForthVM> compileWord(String wordName, List<Instruction> instructions, Map<String, List<Instruction>> dictionary) {
        String safeName = "word_" + wordName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.nanoTime();
        
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V21, ACC_PUBLIC, CLASS_NAME, null, "java/lang/Object", null);

        // Constructor
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        // Static method for the word
        mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, safeName, "(" + VM_TYPE + ")V", null, null);
        mv.visitCode();

        compileInstructions(mv, instructions, dictionary);

        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0); // Computed by ClassWriter.COMPUTE_FRAMES
        mv.visitEnd();
        cw.visitEnd();

        byte[] b = cw.toByteArray();
        Class<?> generatedClass = defineClass(CLASS_NAME.replace('/', '.'), b, 0, b.length);
        
        try {
            Method method = generatedClass.getMethod(safeName, ForthVM.class);
            return (vm) -> {
                try {
                    method.invoke(null, vm);
                } catch (Exception e) {
                    throw new MiniForthException("Error executing compiled word: " + wordName, e);
                }
            };
        } catch (NoSuchMethodException e) {
            throw new MiniForthException("Failed to find generated method for: " + wordName, e);
        }
    }

    private void compileInstructions(MethodVisitor mv, List<Instruction> instructions, Map<String, List<Instruction>> dictionary) {
        // Mapping jump addresses to ASM labels
        Map<Integer, Label> labels = new HashMap<>();
        for (int i = 0; i < instructions.size(); i++) {
            Instruction instr = instructions.get(i);
            if (instr.operand() instanceof Integer target) {
                labels.putIfAbsent(target, new Label());
            }
        }

        for (int i = 0; i < instructions.size(); i++) {
            if (labels.containsKey(i)) {
                mv.visitLabel(labels.get(i));
            }

            Instruction instr = instructions.get(i);
            compileInstruction(mv, instr, labels, dictionary);
        }
    }

    private void compileInstruction(MethodVisitor mv, Instruction instr, Map<Integer, Label> labels, Map<String, List<Instruction>> dictionary) {
        switch (instr.opCode()) {
            case PUSH -> {
                mv.visitVarInsn(ALOAD, 0); // VM
                if (instr.operand() instanceof Long l) {
                    mv.visitLdcInsn(l);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                } else if (instr.operand() instanceof Double d) {
                    mv.visitLdcInsn(d);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                } else if (instr.operand() instanceof String s) {
                    mv.visitLdcInsn(s);
                }
                mv.visitMethodInsn(INVOKEVIRTUAL, VM_INTERNAL_NAME, "push", "(Ljava/lang/Object;)V", false);
            }
            case ADD -> compileBinaryOp(mv, '+');
            case SUB -> compileBinaryOp(mv, '-');
            case MUL -> compileBinaryOp(mv, '*');
            case DIV -> compileBinaryOp(mv, '/');
            
            case DUP -> {
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, VM_INTERNAL_NAME, "peek", "()Ljava/lang/Object;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, VM_INTERNAL_NAME, "push", "(Ljava/lang/Object;)V", false);
            }
            case DROP -> {
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, VM_INTERNAL_NAME, "pop", "()Ljava/lang/Object;", false);
                mv.visitInsn(POP);
            }
            case PRINT_STRING -> {
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, VM_INTERNAL_NAME, "getOutput", "()Ljava/io/PrintStream;", false);
                mv.visitLdcInsn(instr.operand());
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
            }
            case PUSH_STRING -> {
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn(instr.operand());
                mv.visitMethodInsn(INVOKEVIRTUAL, VM_INTERNAL_NAME, "push", "(Ljava/lang/Object;)V", false);
            }
            case DOT -> {
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, VM_INTERNAL_NAME, "pop", "()Ljava/lang/Object;", false);
                mv.visitVarInsn(ASTORE, 1); // Store value to local 1
                
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, VM_INTERNAL_NAME, "getOutput", "()Ljava/io/PrintStream;", false);
                
                mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                
                mv.visitVarInsn(ALOAD, 0);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitMethodInsn(INVOKEVIRTUAL, VM_INTERNAL_NAME, "formatValue", "(Ljava/lang/Object;)Ljava/lang/String;", false);
                
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                mv.visitLdcInsn(" ");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
                
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "print", "(Ljava/lang/String;)V", false);
            }
            case BRANCH -> {
                mv.visitJumpInsn(GOTO, labels.get((Integer) instr.operand()));
            }
            case BRANCH_FALSE -> {
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, VM_INTERNAL_NAME, "popLong", "()J", false);
                mv.visitInsn(LCONST_0);
                mv.visitInsn(LCMP);
                mv.visitJumpInsn(IFEQ, labels.get((Integer) instr.operand()));
            }
            case CALL -> {
                // For simplicity, we call back into the VM for now
                // High performance would involve cross-linking generated methods
                mv.visitVarInsn(ALOAD, 0);
                mv.visitLdcInsn(instr.operand());
                mv.visitMethodInsn(INVOKESTATIC, "java/util/List", "of", "(Ljava/lang/Object;)Ljava/util/List;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, VM_INTERNAL_NAME, "executeInstructions", "(Ljava/util/List;)V", false);
            }
            case RETURN -> mv.visitInsn(RETURN);
            
            // Default to using the VM's executeInstruction logic for complex opcodes
            default -> {
                mv.visitVarInsn(ALOAD, 0);
                mv.visitTypeInsn(NEW, "com/tinyvm/miniforth/Instruction");
                mv.visitInsn(DUP);
                mv.visitFieldInsn(GETSTATIC, "com/tinyvm/miniforth/Instruction$OpCode", instr.opCode().name(), "Lcom/tinyvm/miniforth/Instruction$OpCode;");
                if (instr.operand() != null) {
                    mv.visitLdcInsn(instr.operand());
                } else {
                    mv.visitInsn(ACONST_NULL);
                }
                mv.visitLdcInsn(instr.line());
                mv.visitLdcInsn(instr.column());
                mv.visitMethodInsn(INVOKESPECIAL, "com/tinyvm/miniforth/Instruction", "<init>", "(Lcom/tinyvm/miniforth/Instruction$OpCode;Ljava/lang/Object;II)V", false);
                mv.visitInsn(ICONST_0); // dummy IP
                mv.visitInsn(ACONST_NULL); // dummy instructions list
                mv.visitMethodInsn(INVOKEVIRTUAL, VM_INTERNAL_NAME, "executeInstruction", "(Lcom/tinyvm/miniforth/Instruction;ILjava/util/List;)I", false);
                mv.visitInsn(POP);
            }
        }
    }

    private void compileBinaryOp(MethodVisitor mv, char op) {
        mv.visitVarInsn(ALOAD, 0); // VM for push
        mv.visitVarInsn(ALOAD, 0); // VM for pop
        mv.visitMethodInsn(INVOKEVIRTUAL, VM_INTERNAL_NAME, "pop", "()Ljava/lang/Object;", false);
        mv.visitVarInsn(ASTORE, 2); // b
        mv.visitVarInsn(ALOAD, 0); // VM for pop
        mv.visitMethodInsn(INVOKEVIRTUAL, VM_INTERNAL_NAME, "pop", "()Ljava/lang/Object;", false);
        mv.visitVarInsn(ASTORE, 1); // a
        
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitIntInsn(BIPUSH, op);
        mv.visitMethodInsn(INVOKEVIRTUAL, VM_INTERNAL_NAME, "numericOp", "(Ljava/lang/Object;Ljava/lang/Object;C)Ljava/lang/Object;", false);
        mv.visitMethodInsn(INVOKEVIRTUAL, VM_INTERNAL_NAME, "push", "(Ljava/lang/Object;)V", false);
    }
}
