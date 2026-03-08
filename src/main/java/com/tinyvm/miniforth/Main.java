package com.tinyvm.miniforth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for the MiniForth interpreter.
 * Supports REPL mode, file execution, debug mode, and optimization.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        boolean debugMode = false;
        boolean optimizeMode = false;
        boolean compileMode = false;
        boolean forceRepl = false;
        boolean verbose = false;
        List<String> files = new ArrayList<>();
        List<String> breakpoints = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--debug", "-d" -> debugMode = true;
                case "--optimize", "-O" -> optimizeMode = true;
                case "--compile", "-C" -> compileMode = true;
                case "--repl", "-r" -> forceRepl = true;
                case "--verbose", "-v" -> verbose = true;
                case "--break", "-b" -> {
                    if (i + 1 < args.length) {
                        breakpoints.add(args[++i]);
                    } else {
                        System.err.println("Error: --break requires a word name");
                        System.exit(1);
                    }
                }
                case "--help", "-h" -> {
                    printUsage();
                    return;
                }
                default -> {
                    if (args[i].startsWith("-")) {
                        System.err.println("Unknown option: " + args[i]);
                        printUsage();
                        System.exit(1);
                    }
                    files.add(args[i]);
                }
            }
        }

        if (verbose) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        } else {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn");
        }

        ForthVM vm = new ForthVM();
        if (compileMode) vm.setUseBytecode(true);

        if (debugMode) {
            Debugger debugger = new Debugger(System.out);
            vm.setDebugger(debugger);
            vm.setDebugMode(true);
            for (String bp : breakpoints) debugger.addBreakpoint(bp);
            if (breakpoints.isEmpty()) debugger.startStepping();
        }

        for (String file : files) {
            try {
                log.info("Executing file: {}", file);
                vm.executeFile(file);
                if (optimizeMode) new Optimizer().optimize(vm);
                if (compileMode) vm.compile();
            } catch (MiniForthException e) {
                System.err.println("Error: " + e.getMessage());
                System.exit(1);
            }
        }

        if (files.isEmpty() || forceRepl) {
            if (optimizeMode) new Optimizer().optimize(vm);
            if (compileMode) vm.compile();
            new Repl(vm).run();
        }
    }

    private static void printUsage() {
        System.out.println("""
            MiniForth - A Forth interpreter on the JVM

            Usage: miniforth [options] [file...]

            Options:
              --repl, -r          Start REPL (also starts after file execution)
              --debug, -d         Enable debugger
              --break, -b <word>  Set breakpoint on word (implies --debug)
              --optimize, -O      Enable optimization passes
              --compile, -C       Enable JVM bytecode compilation
              --verbose, -v       Enable verbose logging
              --help, -h          Show this help

            Examples:
              miniforth                       Start interactive REPL
              miniforth program.mf            Execute a file
              miniforth -d -b FACTORIAL test.mf  Debug with breakpoint
              miniforth -O program.mf         Execute with optimization
              miniforth -C program.mf         Execute with JVM bytecode compilation
            """);
    }
}
