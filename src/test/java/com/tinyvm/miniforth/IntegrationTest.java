package com.tinyvm.miniforth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that verify end-to-end behavior of the MiniForth interpreter.
 */
class IntegrationTest {

    private ForthVM vm;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        vm = new ForthVM(new PrintStream(outputStream), System.in);
    }

    private String getOutput() {
        return outputStream.toString().trim();
    }

    // --- Multi-line programs ---

    @Test
    void testMultiWordProgram() {
        vm.execute("""
            : SQUARE DUP * ;
            : CUBE DUP SQUARE * ;
            : SUM-CUBES ( n -- sum )
              0 SWAP 1 + 1 DO
                I CUBE +
              LOOP
            ;
            5 SUM-CUBES .
            """);
        assertEquals("225", getOutput());
    }

    @Test
    void testRecursiveFactorial() {
        vm.execute("""
            : FACTORIAL
              DUP 1 <= IF
                DROP 1
              ELSE
                1 SWAP 1 + 2 DO
                  I *
                LOOP
              THEN
            ;
            10 FACTORIAL .
            """);
        assertEquals("3628800", getOutput());
    }

    @Test
    void testVariablesInWords() {
        vm.execute("""
            VARIABLE total
            0 total !
            : ACCUMULATE ( n -- ) total @ + total ! ;
            5 ACCUMULATE
            10 ACCUMULATE
            3 ACCUMULATE
            total @ .
            """);
        assertEquals("18", getOutput());
    }

    @Test
    void testConstantsInWords() {
        vm.execute("""
            3 CONSTANT THREE
            : TIMES-THREE THREE * ;
            7 TIMES-THREE .
            """);
        assertEquals("21", getOutput());
    }

    @Test
    void testNestedControlFlow() {
        vm.execute("""
            : CLASSIFY ( n -- )
              DUP 0 < IF
                ." negative"
              ELSE
                DUP 0 = IF
                  ." zero"
                ELSE
                  ." positive"
                THEN
              THEN
              DROP
            ;
            -5 CLASSIFY
            """);
        assertEquals("negative", getOutput());
    }

    @Test
    void testNestedLoops() {
        vm.execute("""
            : MULT-TABLE ( n -- )
              DUP 1 + 1 DO
                DUP 1 + 1 DO
                  I J * .
                LOOP
                CR
              LOOP
              DROP
            ;
            3 MULT-TABLE
            """);
        String out = getOutput();
        assertTrue(out.contains("1"));
        assertTrue(out.contains("9"));
    }

    @Test
    void testWhileLoop() {
        vm.execute("""
            : COLLATZ ( n -- steps )
              0 SWAP
              BEGIN
                DUP 1 >
              WHILE
                DUP 2 MOD 0 = IF
                  2 /
                ELSE
                  3 * 1 +
                THEN
                SWAP 1 + SWAP
              REPEAT
              DROP
            ;
            27 COLLATZ .
            """);
        assertEquals("111", getOutput());
    }

    // --- File execution ---

    @Test
    void testFileExecution(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("test.mf");
        Files.writeString(file, ": DOUBLE 2 * ; 21 DOUBLE .");
        vm.executeFile(file.toString());
        assertEquals("42", getOutput());
    }

    @Test
    void testFileNotFound() {
        assertThrows(MiniForthException.class, () -> vm.executeFile("/nonexistent/file.mf"));
    }

    // --- Error handling ---

    @Test
    void testStackUnderflowMessage() {
        MiniForthException e = assertThrows(MiniForthException.class, () -> vm.execute("+"));
        assertTrue(e.getMessage().contains("Stack underflow"));
    }

    @Test
    void testUndefinedWordMessage() {
        MiniForthException e = assertThrows(MiniForthException.class, () -> vm.execute("FOOBAR"));
        assertTrue(e.getMessage().contains("Undefined word"));
        assertTrue(e.getMessage().contains("FOOBAR"));
    }

    @Test
    void testDivisionByZeroMessage() {
        MiniForthException e = assertThrows(MiniForthException.class, () -> vm.execute("1 0 /"));
        assertTrue(e.getMessage().contains("Division by zero"));
    }

    @Test
    void testStackOverflow() {
        // Create a word that would overflow the stack
        vm.execute(": OVERFLOW BEGIN 1 FALSE UNTIL ;");
        assertThrows(MiniForthException.class, () -> vm.execute("OVERFLOW"));
    }

    // --- Edge cases ---

    @Test
    void testEmptyInput() {
        vm.execute("");
        assertTrue(vm.getDataStack().isEmpty());
    }

    @Test
    void testOnlyComments() {
        vm.execute("\\ this is just a comment\n( and a block comment )");
        assertTrue(vm.getDataStack().isEmpty());
    }

    @Test
    void testLargeNumbers() {
        vm.execute("9223372036854775807"); // Long.MAX_VALUE
        assertEquals(Long.MAX_VALUE, vm.getDataStack().peek());
    }

    @Test
    void testNegativeNumbers() {
        vm.execute("0 5 -");
        assertEquals(-5L, vm.getDataStack().peek());
    }

    @Test
    void testHexNumbers() {
        vm.execute("0xFF");
        assertEquals(255L, vm.getDataStack().peek());
    }

    @Test
    void testWordRedefinition() {
        vm.execute(": FOO 1 ;");
        vm.execute("FOO");
        assertEquals(1L, vm.getDataStack().peek());
        vm.getDataStack().clear();

        vm.execute(": FOO 2 ;");
        vm.execute("FOO");
        assertEquals(2L, vm.getDataStack().peek());
    }

    @Test
    void testBooleanOperations() {
        vm.execute("TRUE FALSE AND");
        assertEquals(0L, vm.getDataStack().peek());
        vm.getDataStack().clear();

        vm.execute("TRUE FALSE OR");
        assertEquals(-1L, vm.getDataStack().peek());
    }

    @Test
    void testDotS() {
        vm.execute("1 2 3 .S");
        assertTrue(getOutput().contains("<3>"));
        assertTrue(getOutput().contains("1"));
        assertTrue(getOutput().contains("2"));
        assertTrue(getOutput().contains("3"));
    }

    @Test
    void testWordsCommand() {
        vm.execute(": FOO 1 ;");
        vm.execute(": BAR 2 ;");
        vm.execute("WORDS");
        String out = getOutput();
        assertTrue(out.contains("FOO"));
        assertTrue(out.contains("BAR"));
    }

    @Test
    void testResetKeepsDictionary() {
        vm.execute(": FOO 42 ;");
        vm.reset();
        vm.execute("FOO");
        assertEquals(42L, vm.getDataStack().peek());
    }

    @Test
    void testResetAllClearsDictionary() {
        vm.execute(": FOO 42 ;");
        vm.resetAll();
        assertThrows(MiniForthException.class, () -> vm.execute("FOO"));
    }

    // --- Optimizer integration ---

    @Test
    void testOptimizerPreservesCorrectness() {
        vm.execute("""
            : GCD
              BEGIN
                DUP 0 >
              WHILE
                SWAP OVER MOD
              REPEAT
              DROP
            ;
            """);

        Optimizer optimizer = new Optimizer();
        optimizer.optimize(vm);

        vm.execute("48 18 GCD .");
        assertEquals("6", getOutput());
    }
}
