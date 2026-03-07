package com.tinyvm.miniforth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

/**
 * Interactive Read-Eval-Print Loop for MiniForth.
 */
public class Repl {

    private final ForthVM vm;
    private final PrintStream output;
    private final BufferedReader reader;

    public Repl(ForthVM vm) {
        this.vm = vm;
        this.output = vm.getOutput();
        this.reader = new BufferedReader(new InputStreamReader(System.in));
    }

    /**
     * Start the REPL session.
     */
    public void run() {
        output.println("MiniForth v1.0 - A Forth interpreter on the JVM");
        output.println("Type 'BYE' to exit, 'WORDS' to list defined words.");
        output.println();

        StringBuilder multiLine = new StringBuilder();
        boolean inDefinition = false;

        while (vm.isRunning()) {
            if (inDefinition) {
                output.print("...> ");
            } else {
                output.print("forth> ");
            }
            output.flush();

            String line;
            try {
                line = reader.readLine();
            } catch (IOException e) {
                output.println("Error reading input: " + e.getMessage());
                break;
            }

            if (line == null) {
                break; // EOF
            }

            line = line.trim();
            if (line.isEmpty()) continue;

            // Handle multi-line word definitions
            if (line.contains(":") && !line.contains(";")) {
                inDefinition = true;
                multiLine.append(line).append("\n");
                continue;
            }

            if (inDefinition) {
                multiLine.append(line).append("\n");
                if (line.contains(";")) {
                    inDefinition = false;
                    line = multiLine.toString();
                    multiLine.setLength(0);
                } else {
                    continue;
                }
            }

            try {
                vm.execute(line);
                // Print "ok" like traditional Forth
                if (vm.isRunning()) {
                    output.println(" ok");
                }
            } catch (MiniForthException e) {
                output.println("Error: " + e.getMessage());
                vm.reset();
            } catch (Exception e) {
                output.println("Internal error: " + e.getMessage());
                vm.reset();
            }
        }

        output.println("Bye!");
    }
}
