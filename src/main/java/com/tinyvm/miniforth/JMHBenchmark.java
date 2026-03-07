package com.tinyvm.miniforth;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for MiniForth vs pure Java performance comparison.
 * Run with: java -cp target/miniforth-1.0-SNAPSHOT.jar com.tinyvm.miniforth.JMHBenchmark
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class JMHBenchmark {

    private ForthVM factorialVM;
    private ForthVM fibVM;
    private ForthVM loopVM;

    @Setup
    public void setup() {
        factorialVM = createSilentVM();
        factorialVM.execute(": FACTORIAL DUP 1 <= IF DROP 1 ELSE 1 SWAP 1 + 2 DO I * LOOP THEN ;");

        fibVM = createSilentVM();
        fibVM.execute(": FIB DUP 1 <= IF ELSE 0 1 ROT 1 - 0 DO SWAP OVER + LOOP NIP THEN ;");

        loopVM = createSilentVM();
        loopVM.execute(": COUNTSUM 0 SWAP 0 DO I + LOOP ;");
    }

    @org.openjdk.jmh.annotations.Benchmark
    public long javaFactorial() {
        long result = 1;
        for (int i = 2; i <= 20; i++) {
            result *= i;
        }
        return result;
    }

    @org.openjdk.jmh.annotations.Benchmark
    public void forthFactorial() {
        factorialVM.reset();
        factorialVM.execute("20 FACTORIAL DROP");
    }

    @org.openjdk.jmh.annotations.Benchmark
    public long javaFibonacci() {
        long a = 0, b = 1;
        for (int i = 0; i < 30; i++) {
            long temp = a + b;
            a = b;
            b = temp;
        }
        return b;
    }

    @org.openjdk.jmh.annotations.Benchmark
    public void forthFibonacci() {
        fibVM.reset();
        fibVM.execute("30 FIB DROP");
    }

    @org.openjdk.jmh.annotations.Benchmark
    public long javaLoop() {
        long sum = 0;
        for (int i = 0; i < 10000; i++) {
            sum += i;
        }
        return sum;
    }

    @org.openjdk.jmh.annotations.Benchmark
    public void forthLoop() {
        loopVM.reset();
        loopVM.execute("10000 COUNTSUM DROP");
    }

    private static ForthVM createSilentVM() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        return new ForthVM(new PrintStream(baos), System.in);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(JMHBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
