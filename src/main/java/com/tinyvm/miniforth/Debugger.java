package com.tinyvm.miniforth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.*;

/**
 * Interactive debugger for MiniForth. Supports breakpoints on words,
 * step-through execution, stack inspection, and variable viewing.
 */
public class Debugger {

    private final Set<String> breakpoints = new HashSet<>();
    private final PrintStream output;
    private final BufferedReader reader;
    private boolean stepping = false;
    private boolean enabled = true;

    public Debugger(PrintStream output) {
        this.output = output;
        this.reader = new BufferedReader(new InputStreamReader(System.in));
    }

    /**
     * Called by the VM before each instruction is executed.
     */
    public void onInstruction(Instruction instr, int ip, ForthVM vm) {
        if (!enabled) return;

        boolean shouldBreak = stepping;

        // Check if this is a CALL to a breakpointed word
        if (instr.opCode() == Instruction.OpCode.CALL) {
            String word = ((String) instr.operand()).toUpperCase();
            if (breakpoints.contains(word)) {
                shouldBreak = true;
                output.println("[Breakpoint hit: %s]".formatted(word));
            }
        }

        if (shouldBreak) {
            output.println("[IP=%d] %s".formatted(ip, instr));
            printStack(vm);
            debugPrompt(vm);
        }
    }

    /**
     * Interactive debug prompt.
     */
    private void debugPrompt(ForthVM vm) {
        while (true) {
            output.print("debug> ");
            output.flush();
            try {
                String line = reader.readLine();
                if (line == null) {
                    vm.setRunning(false);
                    return;
                }
                line = line.trim();

                switch (line.toLowerCase()) {
                    case "s", "step" -> {
                        stepping = true;
                        return;
                    }
                    case "c", "continue" -> {
                        stepping = false;
                        return;
                    }
                    case "q", "quit" -> {
                        vm.setRunning(false);
                        return;
                    }
                    case "stack", "ds" -> printStack(vm);
                    case "rs" -> printReturnStack(vm);
                    case "vars" -> printVariables(vm);
                    case "words" -> printWords(vm);
                    case "bp", "breakpoints" -> printBreakpoints();
                    case "h", "help" -> printHelp();
                    default -> {
                        if (line.startsWith("b ") || line.startsWith("break ")) {
                            String word = line.substring(line.indexOf(' ') + 1).toUpperCase();
                            addBreakpoint(word);
                        } else if (line.startsWith("d ") || line.startsWith("delete ")) {
                            String word = line.substring(line.indexOf(' ') + 1).toUpperCase();
                            removeBreakpoint(word);
                        } else if (line.startsWith("see ")) {
                            String word = line.substring(4).toUpperCase();
                            seeWord(word, vm);
                        } else {
                            output.println("Unknown command. Type 'h' for help.");
                        }
                    }
                }
            } catch (IOException e) {
                output.println("Error reading input: " + e.getMessage());
                vm.setRunning(false);
                return;
            }
        }
    }

    public void addBreakpoint(String word) {
        breakpoints.add(word.toUpperCase());
        output.println("Breakpoint set on: " + word.toUpperCase());
    }

    public void removeBreakpoint(String word) {
        if (breakpoints.remove(word.toUpperCase())) {
            output.println("Breakpoint removed: " + word.toUpperCase());
        } else {
            output.println("No breakpoint on: " + word.toUpperCase());
        }
    }

    public void startStepping() {
        stepping = true;
    }

    private void printStack(ForthVM vm) {
        var stack = vm.getDataStack();
        output.print("Stack <%d>: ".formatted(stack.size()));
        for (Object item : stack.reversed()) {
            output.print(item + " ");
        }
        output.println();
    }

    private void printReturnStack(ForthVM vm) {
        var stack = vm.getReturnStack();
        output.print("Return Stack <%d>: ".formatted(stack.size()));
        for (Object item : stack.reversed()) {
            output.print(item + " ");
        }
        output.println();
    }

    private void printVariables(ForthVM vm) {
        output.println("Variables:");
        vm.getVariables().forEach((k, v) -> output.println("  %s = %s".formatted(k, v)));
        output.println("Constants:");
        vm.getConstants().forEach((k, v) -> output.println("  %s = %s".formatted(k, v)));
    }

    private void printWords(ForthVM vm) {
        output.println("Defined words: " + String.join(" ", vm.getDictionary().keySet()));
    }

    private void printBreakpoints() {
        if (breakpoints.isEmpty()) {
            output.println("No breakpoints set.");
        } else {
            output.println("Breakpoints: " + String.join(" ", breakpoints));
        }
    }

    private void seeWord(String word, ForthVM vm) {
        var body = vm.getDictionary().get(word);
        if (body == null) {
            output.println("Undefined word: " + word);
        } else {
            output.println(": " + word);
            for (int i = 0; i < body.size(); i++) {
                output.println("  %3d: %s".formatted(i, body.get(i)));
            }
            output.println(";");
        }
    }

    private void printHelp() {
        output.println("""
            Debugger commands:
              s, step         - Execute one instruction
              c, continue     - Continue execution
              q, quit         - Stop execution
              stack, ds       - Show data stack
              rs              - Show return stack
              vars            - Show variables and constants
              words           - Show defined words
              b <word>        - Set breakpoint on word
              d <word>        - Delete breakpoint
              bp              - Show breakpoints
              see <word>      - Decompile word
              h, help         - Show this help
            """);
    }
}
