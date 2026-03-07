package com.tinyvm.miniforth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Optimization pass for MiniForth bytecode.
 * Supports constant folding, peephole optimizations, and simple word inlining.
 */
public class Optimizer {

    private static final Logger log = LoggerFactory.getLogger(Optimizer.class);
    private static final int MAX_INLINE_SIZE = 5; // Max instructions in a word to inline

    /**
     * Optimize all word definitions in the VM's dictionary.
     */
    public void optimize(ForthVM vm) {
        Map<String, List<Instruction>> dictionary = vm.getDictionary();
        int totalOptimizations = 0;

        for (var entry : dictionary.entrySet()) {
            String name = entry.getKey();
            List<Instruction> original = entry.getValue();
            List<Instruction> optimized = optimizeInstructions(original, dictionary);
            if (!original.equals(optimized)) {
                entry.setValue(optimized);
                int saved = original.size() - optimized.size();
                totalOptimizations += saved;
                log.debug("Optimized word '{}': {} -> {} instructions", name, original.size(), optimized.size());
            }
        }

        if (totalOptimizations > 0) {
            log.info("Optimization complete: removed {} instructions total", totalOptimizations);
        }
    }

    /**
     * Apply optimization passes to a list of instructions.
     */
    public List<Instruction> optimizeInstructions(List<Instruction> instructions,
                                                   Map<String, List<Instruction>> dictionary) {
        List<Instruction> result = new ArrayList<>(instructions);

        boolean changed = true;
        int passes = 0;
        while (changed && passes < 10) {
            changed = false;
            changed |= constantFold(result);
            changed |= peepholeOptimize(result);
            changed |= inlineSmallWords(result, dictionary);
            changed |= eliminateDeadCode(result);
            passes++;
        }

        return result;
    }

    /**
     * Constant folding: evaluate operations on constant operands at compile time.
     * E.g., PUSH(3) PUSH(4) ADD -> PUSH(7)
     */
    private boolean constantFold(List<Instruction> instructions) {
        boolean changed = false;

        for (int i = 0; i < instructions.size() - 2; i++) {
            Instruction a = instructions.get(i);
            Instruction b = instructions.get(i + 1);
            Instruction op = instructions.get(i + 2);

            if (a.opCode() == Instruction.OpCode.PUSH && b.opCode() == Instruction.OpCode.PUSH
                    && a.operand() instanceof Long la && b.operand() instanceof Long lb) {

                Long result = switch (op.opCode()) {
                    case ADD -> la + lb;
                    case SUB -> la - lb;
                    case MUL -> la * lb;
                    case DIV -> lb != 0 ? la / lb : null;
                    case MOD -> lb != 0 ? la % lb : null;
                    case EQ -> la.equals(lb) ? -1L : 0L;
                    case LT -> la < lb ? -1L : 0L;
                    case GT -> la > lb ? -1L : 0L;
                    case AND -> la & lb;
                    case OR -> la | lb;
                    case XOR -> la ^ lb;
                    default -> null;
                };

                if (result != null) {
                    instructions.set(i, new Instruction(Instruction.OpCode.PUSH, result));
                    instructions.set(i + 1, new Instruction(Instruction.OpCode.NOP));
                    instructions.set(i + 2, new Instruction(Instruction.OpCode.NOP));
                    changed = true;
                    log.debug("Constant folded: {} {} {} -> PUSH({})", a, b, op, result);
                }
            }
        }

        // Remove NOPs
        if (changed) {
            instructions.removeIf(instr -> instr.opCode() == Instruction.OpCode.NOP);
        }

        return changed;
    }

    /**
     * Peephole optimizations for common patterns.
     */
    private boolean peepholeOptimize(List<Instruction> instructions) {
        boolean changed = false;

        for (int i = 0; i < instructions.size() - 1; i++) {
            Instruction curr = instructions.get(i);
            Instruction next = instructions.get(i + 1);

            // DUP + ADD -> replace with PUSH(2) MUL (double top of stack)
            if (curr.opCode() == Instruction.OpCode.DUP && next.opCode() == Instruction.OpCode.ADD) {
                instructions.set(i, new Instruction(Instruction.OpCode.PUSH, 2L));
                instructions.set(i + 1, new Instruction(Instruction.OpCode.MUL));
                changed = true;
                log.debug("Peephole: DUP ADD -> PUSH(2) MUL");
            }

            // PUSH(x) DROP -> remove both
            if (curr.opCode() == Instruction.OpCode.PUSH && next.opCode() == Instruction.OpCode.DROP) {
                instructions.set(i, new Instruction(Instruction.OpCode.NOP));
                instructions.set(i + 1, new Instruction(Instruction.OpCode.NOP));
                changed = true;
                log.debug("Peephole: PUSH DROP -> NOP NOP");
            }

            // DUP DROP -> NOP
            if (curr.opCode() == Instruction.OpCode.DUP && next.opCode() == Instruction.OpCode.DROP) {
                instructions.set(i, new Instruction(Instruction.OpCode.NOP));
                instructions.set(i + 1, new Instruction(Instruction.OpCode.NOP));
                changed = true;
                log.debug("Peephole: DUP DROP -> NOP NOP");
            }

            // SWAP SWAP -> NOP NOP
            if (curr.opCode() == Instruction.OpCode.SWAP && next.opCode() == Instruction.OpCode.SWAP) {
                instructions.set(i, new Instruction(Instruction.OpCode.NOP));
                instructions.set(i + 1, new Instruction(Instruction.OpCode.NOP));
                changed = true;
                log.debug("Peephole: SWAP SWAP -> NOP NOP");
            }
        }

        if (changed) {
            instructions.removeIf(instr -> instr.opCode() == Instruction.OpCode.NOP);
        }

        return changed;
    }

    /**
     * Inline small word definitions (fewer than MAX_INLINE_SIZE instructions).
     */
    private boolean inlineSmallWords(List<Instruction> instructions,
                                      Map<String, List<Instruction>> dictionary) {
        boolean changed = false;

        for (int i = 0; i < instructions.size(); i++) {
            Instruction instr = instructions.get(i);
            if (instr.opCode() == Instruction.OpCode.CALL) {
                String wordName = ((String) instr.operand()).toUpperCase();
                List<Instruction> wordBody = dictionary.get(wordName);

                if (wordBody != null && wordBody.size() <= MAX_INLINE_SIZE && canInline(wordBody)) {
                    // Replace CALL with inlined body (minus the RETURN)
                    instructions.remove(i);
                    List<Instruction> inlined = wordBody.stream()
                            .filter(w -> w.opCode() != Instruction.OpCode.RETURN)
                            .toList();
                    instructions.addAll(i, inlined);
                    changed = true;
                    log.debug("Inlined word: {}", wordName);
                }
            }
        }

        return changed;
    }

    /**
     * Check if a word can be safely inlined (no control flow that requires address patching).
     */
    private boolean canInline(List<Instruction> body) {
        for (Instruction instr : body) {
            switch (instr.opCode()) {
                case BRANCH, BRANCH_FALSE, DO, LOOP, PLUS_LOOP, CALL -> {
                    return false;
                }
                default -> {}
            }
        }
        return true;
    }

    /**
     * Eliminate dead code after unconditional branches or returns.
     */
    private boolean eliminateDeadCode(List<Instruction> instructions) {
        boolean changed = false;

        for (int i = 0; i < instructions.size() - 1; i++) {
            Instruction curr = instructions.get(i);
            if (curr.opCode() == Instruction.OpCode.RETURN || curr.opCode() == Instruction.OpCode.HALT) {
                Instruction next = instructions.get(i + 1);
                // Don't remove if it could be a branch target
                if (next.opCode() != Instruction.OpCode.NOP && !isBranchTarget(i + 1, instructions)) {
                    instructions.set(i + 1, new Instruction(Instruction.OpCode.NOP));
                    changed = true;
                    log.debug("Dead code after {} at index {}", curr.opCode(), i);
                }
            }
        }

        if (changed) {
            instructions.removeIf(instr -> instr.opCode() == Instruction.OpCode.NOP);
        }

        return changed;
    }

    private boolean isBranchTarget(int index, List<Instruction> instructions) {
        for (Instruction instr : instructions) {
            if ((instr.opCode() == Instruction.OpCode.BRANCH || instr.opCode() == Instruction.OpCode.BRANCH_FALSE
                    || instr.opCode() == Instruction.OpCode.LOOP || instr.opCode() == Instruction.OpCode.PLUS_LOOP)
                    && instr.operand() instanceof Integer target && target == index) {
                return true;
            }
        }
        return false;
    }
}
