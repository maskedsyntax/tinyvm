package com.tinyvm.miniforth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * The MiniForth virtual machine. Executes bytecode instructions using a data stack
 * and return stack. Maintains a dictionary of user-defined and built-in words.
 */
public class ForthVM {

    private static final Logger log = LoggerFactory.getLogger(ForthVM.class);
    private static final int MAX_STACK_SIZE = 65536;

    private final ArrayDeque<Object> dataStack = new ArrayDeque<>();
    private final ArrayDeque<Object> returnStack = new ArrayDeque<>();
    private final Map<String, List<Instruction>> dictionary = new LinkedHashMap<>();
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, Object> constants = new HashMap<>();

    private PrintStream output;
    private InputStream input;
    private boolean running = true;
    private boolean debugMode = false;
    private Debugger debugger;

    // Loop control
    private final ArrayDeque<LoopFrame> loopStack = new ArrayDeque<>();

    public ForthVM() {
        this(System.out, System.in);
    }

    public ForthVM(PrintStream output, InputStream input) {
        this.output = output;
        this.input = input;
    }

    /**
     * Execute a string of MiniForth code.
     */
    public void execute(String source) {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();
        Parser parser = new Parser(tokens);
        Parser.ParseResult result = parser.parse();

        // Register word definitions
        for (Parser.WordDefinition def : result.definitions()) {
            dictionary.put(def.name().toUpperCase(), def.body());
            log.debug("Defined word: {}", def.name());
        }

        // Execute immediate code
        executeInstructions(result.immediateCode());
    }

    /**
     * Execute a list of instructions.
     */
    public void executeInstructions(List<Instruction> instructions) {
        int ip = 0;
        while (ip < instructions.size() && running) {
            Instruction instr = instructions.get(ip);

            if (debugger != null && debugMode) {
                debugger.onInstruction(instr, ip, this);
                if (!running) return;
            }

            ip = executeInstruction(instr, ip, instructions);
        }
    }

    /**
     * Execute a single instruction. Returns the next instruction pointer.
     */
    private int executeInstruction(Instruction instr, int ip, List<Instruction> instructions) {
        switch (instr.opCode()) {
            case PUSH -> {
                push(instr.operand());
                return ip + 1;
            }

            // Stack operations
            case DUP -> {
                push(peek());
                return ip + 1;
            }
            case DROP -> {
                pop();
                return ip + 1;
            }
            case SWAP -> {
                Object a = pop();
                Object b = pop();
                push(a);
                push(b);
                return ip + 1;
            }
            case OVER -> {
                Object a = pop();
                Object b = peek();
                push(a);
                push(b);
                return ip + 1;
            }
            case ROT -> {
                Object c = pop();
                Object b = pop();
                Object a = pop();
                push(b);
                push(c);
                push(a);
                return ip + 1;
            }
            case NIP -> {
                Object a = pop();
                pop();
                push(a);
                return ip + 1;
            }
            case TUCK -> {
                Object a = pop();
                Object b = pop();
                push(a);
                push(b);
                push(a);
                return ip + 1;
            }
            case PICK -> {
                int n = popInt();
                Object[] items = new Object[n + 1];
                for (int i = 0; i <= n; i++) items[i] = pop();
                Object picked = items[n];
                for (int i = n; i >= 0; i--) push(items[i]);
                push(picked);
                return ip + 1;
            }
            case DEPTH -> {
                push((long) dataStack.size());
                return ip + 1;
            }
            case TWO_DUP -> {
                Object a = pop();
                Object b = peek();
                push(a);
                push(b);
                push(a);
                return ip + 1;
            }
            case TWO_DROP -> {
                pop();
                pop();
                return ip + 1;
            }
            case TWO_SWAP -> {
                Object d = pop();
                Object c = pop();
                Object b = pop();
                Object a = pop();
                push(c);
                push(d);
                push(a);
                push(b);
                return ip + 1;
            }
            case TWO_OVER -> {
                Object d = pop();
                Object c = pop();
                Object b = pop();
                Object a = pop();
                push(a);
                push(b);
                push(c);
                push(d);
                push(a);
                push(b);
                return ip + 1;
            }

            // Arithmetic
            case ADD -> {
                Object b = pop();
                Object a = pop();
                push(numericOp(a, b, '+'));
                return ip + 1;
            }
            case SUB -> {
                Object b = pop();
                Object a = pop();
                push(numericOp(a, b, '-'));
                return ip + 1;
            }
            case MUL -> {
                Object b = pop();
                Object a = pop();
                push(numericOp(a, b, '*'));
                return ip + 1;
            }
            case DIV -> {
                Object b = pop();
                Object a = pop();
                if (isZero(b)) throw new MiniForthException("Division by zero");
                push(numericOp(a, b, '/'));
                return ip + 1;
            }
            case MOD -> {
                long b = popLong();
                long a = popLong();
                if (b == 0) throw new MiniForthException("Modulo by zero");
                push(a % b);
                return ip + 1;
            }
            case NEGATE -> {
                Object a = pop();
                if (a instanceof Double d) push(-d);
                else push(-toLong(a));
                return ip + 1;
            }
            case ABS -> {
                Object a = pop();
                if (a instanceof Double d) push(Math.abs(d));
                else push(Math.abs(toLong(a)));
                return ip + 1;
            }
            case MIN -> {
                long b = popLong();
                long a = popLong();
                push(Math.min(a, b));
                return ip + 1;
            }
            case MAX -> {
                long b = popLong();
                long a = popLong();
                push(Math.max(a, b));
                return ip + 1;
            }

            // Comparison
            case EQ -> {
                Object b = pop();
                Object a = pop();
                push(a.equals(b) ? -1L : 0L);
                return ip + 1;
            }
            case NEQ -> {
                Object b = pop();
                Object a = pop();
                push(!a.equals(b) ? -1L : 0L);
                return ip + 1;
            }
            case LT -> {
                long b = popLong();
                long a = popLong();
                push(a < b ? -1L : 0L);
                return ip + 1;
            }
            case GT -> {
                long b = popLong();
                long a = popLong();
                push(a > b ? -1L : 0L);
                return ip + 1;
            }
            case LE -> {
                long b = popLong();
                long a = popLong();
                push(a <= b ? -1L : 0L);
                return ip + 1;
            }
            case GE -> {
                long b = popLong();
                long a = popLong();
                push(a >= b ? -1L : 0L);
                return ip + 1;
            }
            case ZERO_EQ -> {
                push(popLong() == 0 ? -1L : 0L);
                return ip + 1;
            }
            case ZERO_LT -> {
                push(popLong() < 0 ? -1L : 0L);
                return ip + 1;
            }
            case ZERO_GT -> {
                push(popLong() > 0 ? -1L : 0L);
                return ip + 1;
            }

            // Logic
            case AND -> {
                long b = popLong();
                long a = popLong();
                push(a & b);
                return ip + 1;
            }
            case OR -> {
                long b = popLong();
                long a = popLong();
                push(a | b);
                return ip + 1;
            }
            case XOR -> {
                long b = popLong();
                long a = popLong();
                push(a ^ b);
                return ip + 1;
            }
            case INVERT -> {
                push(~popLong());
                return ip + 1;
            }

            // I/O
            case DOT -> {
                Object val = pop();
                output.print(formatValue(val) + " ");
                return ip + 1;
            }
            case EMIT -> {
                long code = popLong();
                output.print((char) code);
                return ip + 1;
            }
            case CR -> {
                output.println();
                return ip + 1;
            }
            case PRINT_STRING -> {
                output.print(instr.operand());
                return ip + 1;
            }
            case KEY -> {
                try {
                    int ch = input.read();
                    push((long) ch);
                } catch (IOException e) {
                    throw new MiniForthException("Error reading input", e);
                }
                return ip + 1;
            }
            case DOT_S -> {
                output.print("<%d> ".formatted(dataStack.size()));
                for (Object item : dataStack.reversed()) {
                    output.print(formatValue(item) + " ");
                }
                return ip + 1;
            }

            // Control flow
            case CALL -> {
                String wordName = ((String) instr.operand()).toUpperCase();

                // Check constants first
                if (constants.containsKey(wordName)) {
                    push(constants.get(wordName));
                    return ip + 1;
                }

                // Check variables (push address/name)
                if (variables.containsKey(wordName)) {
                    push(wordName); // push variable "address" (name)
                    return ip + 1;
                }

                List<Instruction> wordBody = dictionary.get(wordName);
                if (wordBody == null) {
                    throw new MiniForthException("Undefined word: " + instr.operand());
                }
                executeInstructions(wordBody);
                return ip + 1;
            }
            case RETURN -> {
                return instructions.size(); // exit current word
            }
            case BRANCH -> {
                return (int) instr.operand();
            }
            case BRANCH_FALSE -> {
                long condition = popLong();
                if (condition == 0) {
                    return (int) instr.operand();
                }
                return ip + 1;
            }

            // DO/LOOP
            case DO -> {
                long start = popLong();
                long limit = popLong();
                if (start == limit) {
                    // Skip loop body when start equals limit (standard Forth behavior)
                    return (int) instr.operand();
                }
                loopStack.push(new LoopFrame(start, limit, (int) instr.operand()));
                return ip + 1;
            }
            case LOOP -> {
                LoopFrame frame = loopStack.peek();
                if (frame == null) throw new MiniForthException("LOOP without DO");
                frame.index++;
                if (frame.index >= frame.limit) {
                    loopStack.pop();
                    return ip + 1;
                }
                return (int) instr.operand();
            }
            case PLUS_LOOP -> {
                long increment = popLong();
                LoopFrame frame = loopStack.peek();
                if (frame == null) throw new MiniForthException("+LOOP without DO");
                long oldIndex = frame.index;
                frame.index += increment;
                // Check crossing boundary
                boolean done;
                if (increment > 0) {
                    done = frame.index >= frame.limit;
                } else {
                    done = frame.index < frame.limit;
                }
                if (done) {
                    loopStack.pop();
                    return ip + 1;
                }
                return (int) instr.operand();
            }
            case LOOP_I -> {
                LoopFrame frame = loopStack.peek();
                if (frame == null) throw new MiniForthException("I used outside of DO loop");
                push(frame.index);
                return ip + 1;
            }
            case LOOP_J -> {
                if (loopStack.size() < 2) throw new MiniForthException("J used without nested DO loop");
                Iterator<LoopFrame> it = loopStack.iterator();
                it.next(); // skip inner
                push(it.next().index);
                return ip + 1;
            }
            case LEAVE -> {
                LoopFrame frame = loopStack.pop();
                if (frame == null) throw new MiniForthException("LEAVE used outside of DO loop");
                return frame.exitAddress;
            }

            // Variables
            case VARIABLE -> {
                String name = ((String) instr.operand()).toUpperCase();
                variables.put(name, 0L);
                log.debug("Declared variable: {}", name);
                return ip + 1;
            }
            case CONSTANT -> {
                String name = ((String) instr.operand()).toUpperCase();
                Object value = pop();
                constants.put(name, value);
                log.debug("Declared constant: {} = {}", name, value);
                return ip + 1;
            }
            case FETCH -> {
                String varName = (String) pop();
                if (!variables.containsKey(varName)) {
                    throw new MiniForthException("Unknown variable: " + varName);
                }
                push(variables.get(varName));
                return ip + 1;
            }
            case STORE -> {
                String varName = (String) pop();
                Object value = pop();
                if (!variables.containsKey(varName)) {
                    throw new MiniForthException("Unknown variable: " + varName);
                }
                variables.put(varName, value);
                return ip + 1;
            }

            // Misc
            case WORDS -> {
                output.println(String.join(" ", dictionary.keySet()));
                return ip + 1;
            }
            case SEE -> {
                String wordName = ((String) pop()).toUpperCase();
                List<Instruction> body = dictionary.get(wordName);
                if (body == null) {
                    output.println("Undefined word: " + wordName);
                } else {
                    output.println(": " + wordName);
                    for (int i = 0; i < body.size(); i++) {
                        output.println("  %3d: %s".formatted(i, body.get(i)));
                    }
                    output.println(";");
                }
                return ip + 1;
            }
            case BYE -> {
                running = false;
                return instructions.size();
            }
            case INCLUDE -> {
                String fileName = (String) pop();
                executeFile(fileName);
                return ip + 1;
            }
            case NOP -> {
                return ip + 1;
            }
            case HALT -> {
                running = false;
                return instructions.size();
            }

            default -> throw new MiniForthException("Unknown opcode: " + instr.opCode());
        }
    }

    /**
     * Execute a MiniForth source file.
     */
    public void executeFile(String path) {
        try {
            String source = Files.readString(Path.of(path));
            execute(source);
        } catch (IOException e) {
            throw new MiniForthException("Cannot read file: " + path, e);
        }
    }

    // Stack operations
    public void push(Object value) {
        if (dataStack.size() >= MAX_STACK_SIZE) {
            throw new MiniForthException("Stack overflow (max size: " + MAX_STACK_SIZE + ")");
        }
        dataStack.push(value);
    }

    public Object pop() {
        if (dataStack.isEmpty()) {
            throw new MiniForthException("Stack underflow");
        }
        return dataStack.pop();
    }

    public Object peek() {
        if (dataStack.isEmpty()) {
            throw new MiniForthException("Stack underflow");
        }
        return dataStack.peek();
    }

    public long popLong() {
        return toLong(pop());
    }

    public int popInt() {
        return (int) popLong();
    }

    public long toLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i;
        if (value instanceof Double d) return d.longValue();
        if (value instanceof Boolean b) return b ? -1 : 0;
        throw new MiniForthException("Expected numeric value, got: " + value.getClass().getSimpleName());
    }

    private boolean isZero(Object value) {
        if (value instanceof Long l) return l == 0;
        if (value instanceof Double d) return d == 0.0;
        return false;
    }

    private Object numericOp(Object a, Object b, char op) {
        if (a instanceof Double || b instanceof Double) {
            double da = (a instanceof Double d) ? d : toLong(a);
            double db = (b instanceof Double d) ? d : toLong(b);
            return switch (op) {
                case '+' -> da + db;
                case '-' -> da - db;
                case '*' -> da * db;
                case '/' -> da / db;
                default -> throw new MiniForthException("Unknown operator: " + op);
            };
        }
        long la = toLong(a);
        long lb = toLong(b);
        return switch (op) {
            case '+' -> la + lb;
            case '-' -> la - lb;
            case '*' -> la * lb;
            case '/' -> la / lb;
            default -> throw new MiniForthException("Unknown operator: " + op);
        };
    }

    private String formatValue(Object value) {
        if (value instanceof Long l) return Long.toString(l);
        if (value instanceof Double d) {
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return Long.toString(d.longValue()) + ".0";
            }
            return Double.toString(d);
        }
        return String.valueOf(value);
    }

    // Accessors
    public ArrayDeque<Object> getDataStack() { return dataStack; }
    public ArrayDeque<Object> getReturnStack() { return returnStack; }
    public Map<String, List<Instruction>> getDictionary() { return dictionary; }
    public Map<String, Object> getVariables() { return variables; }
    public Map<String, Object> getConstants() { return constants; }
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }
    public PrintStream getOutput() { return output; }
    public void setOutput(PrintStream output) { this.output = output; }

    public void setDebugMode(boolean debugMode) { this.debugMode = debugMode; }
    public boolean isDebugMode() { return debugMode; }
    public void setDebugger(Debugger debugger) { this.debugger = debugger; }
    public Debugger getDebugger() { return debugger; }

    /**
     * Reset the VM state (stacks, but keep dictionary).
     */
    public void reset() {
        dataStack.clear();
        returnStack.clear();
        loopStack.clear();
        running = true;
    }

    /**
     * Fully reset including dictionary.
     */
    public void resetAll() {
        reset();
        dictionary.clear();
        variables.clear();
        constants.clear();
    }

    private static class LoopFrame {
        long index;
        long limit;
        int exitAddress;

        LoopFrame(long index, long limit, int exitAddress) {
            this.index = index;
            this.limit = limit;
            this.exitAddress = exitAddress;
        }
    }
}
