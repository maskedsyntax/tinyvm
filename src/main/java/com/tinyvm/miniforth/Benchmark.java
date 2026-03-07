package com.tinyvm.miniforth;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Performance benchmarks comparing MiniForth execution against equivalent pure Java.
 * Can be run standalone or integrated with JMH.
 */
public class Benchmark {

    private static final int WARMUP_ITERATIONS = 5;
    private static final int MEASURE_ITERATIONS = 20;

    public static void main(String[] args) {
        System.out.println("=== MiniForth Performance Benchmarks ===\n");

        benchmarkFactorial();
        benchmarkFibonacci();
        benchmarkLoop();
        benchmarkStackOps();
    }

    private static void benchmarkFactorial() {
        System.out.println("--- Factorial(20) ---");

        // Java version
        long javaTime = benchmarkJava(() -> {
            long result = 1;
            for (int i = 2; i <= 20; i++) {
                result *= i;
            }
        });

        // MiniForth version
        ForthVM vm = createSilentVM();
        vm.execute(": FACTORIAL DUP 1 <= IF DROP 1 ELSE 1 SWAP 1 + 2 DO I * LOOP THEN ;");

        long forthTime = benchmarkForth(vm, "20 FACTORIAL DROP");

        printResults("Factorial(20)", javaTime, forthTime);
    }

    private static void benchmarkFibonacci() {
        System.out.println("--- Fibonacci(30) ---");

        long javaTime = benchmarkJava(() -> {
            long a = 0, b = 1;
            for (int i = 0; i < 30; i++) {
                long temp = a + b;
                a = b;
                b = temp;
            }
        });

        ForthVM vm = createSilentVM();
        vm.execute(": FIB DUP 1 <= IF ELSE 0 1 ROT 1 - 0 DO SWAP OVER + LOOP NIP THEN ;");

        long forthTime = benchmarkForth(vm, "30 FIB DROP");

        printResults("Fibonacci(30)", javaTime, forthTime);
    }

    private static void benchmarkLoop() {
        System.out.println("--- Count to 10000 ---");

        long javaTime = benchmarkJava(() -> {
            long sum = 0;
            for (int i = 0; i < 10000; i++) {
                sum += i;
            }
        });

        ForthVM vm = createSilentVM();
        vm.execute(": COUNTSUM 0 SWAP 0 DO I + LOOP ;");

        long forthTime = benchmarkForth(vm, "10000 COUNTSUM DROP");

        printResults("Loop 10000", javaTime, forthTime);
    }

    private static void benchmarkStackOps() {
        System.out.println("--- Stack Operations (1000 DUP/DROP cycles) ---");

        long javaTime = benchmarkJava(() -> {
            java.util.ArrayDeque<Long> stack = new java.util.ArrayDeque<>();
            for (int i = 0; i < 1000; i++) {
                stack.push((long) i);
                stack.push(stack.peek());
                stack.pop();
                stack.pop();
            }
        });

        ForthVM vm = createSilentVM();
        vm.execute(": STACKTEST 1000 0 DO I DUP DROP DROP LOOP ;");

        long forthTime = benchmarkForth(vm, "STACKTEST");

        printResults("Stack Ops x1000", javaTime, forthTime);
    }

    private static long benchmarkJava(Runnable task) {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            task.run();
        }

        // Measure
        long total = 0;
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            long start = System.nanoTime();
            task.run();
            total += System.nanoTime() - start;
        }
        return total / MEASURE_ITERATIONS;
    }

    private static long benchmarkForth(ForthVM vm, String code) {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            vm.reset();
            vm.execute(code);
        }

        // Measure
        long total = 0;
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            vm.reset();
            long start = System.nanoTime();
            vm.execute(code);
            total += System.nanoTime() - start;
        }
        return total / MEASURE_ITERATIONS;
    }

    private static ForthVM createSilentVM() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream silent = new PrintStream(baos);
        return new ForthVM(silent, System.in);
    }

    private static void printResults(String name, long javaNanos, long forthNanos) {
        double javaUs = javaNanos / 1000.0;
        double forthUs = forthNanos / 1000.0;
        double ratio = (double) forthNanos / javaNanos;

        System.out.println("  Java:      %10.2f µs".formatted(javaUs));
        System.out.println("  MiniForth: %10.2f µs".formatted(forthUs));
        System.out.println("  Ratio:     %10.2fx slower".formatted(ratio));
        System.out.println();
    }
}
