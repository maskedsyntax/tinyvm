package com.tinyvm.miniforth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class ForthVMTest {

    private ForthVM vm;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(outputStream);
        vm = new ForthVM(ps, System.in);
    }

    private String getOutput() {
        return outputStream.toString().trim();
    }

    // --- Arithmetic ---

    @Test
    void testAddition() {
        vm.execute("3 4 +");
        assertEquals(7L, vm.getDataStack().peek());
    }

    @Test
    void testSubtraction() {
        vm.execute("10 3 -");
        assertEquals(7L, vm.getDataStack().peek());
    }

    @Test
    void testMultiplication() {
        vm.execute("6 7 *");
        assertEquals(42L, vm.getDataStack().peek());
    }

    @Test
    void testDivision() {
        vm.execute("20 4 /");
        assertEquals(5L, vm.getDataStack().peek());
    }

    @Test
    void testModulo() {
        vm.execute("17 5 MOD");
        assertEquals(2L, vm.getDataStack().peek());
    }

    @Test
    void testNegate() {
        vm.execute("5 NEGATE");
        assertEquals(-5L, vm.getDataStack().peek());
    }

    @Test
    void testAbs() {
        vm.execute("-5 ABS");
        assertEquals(5L, vm.getDataStack().peek());
    }

    @Test
    void testDivisionByZero() {
        assertThrows(MiniForthException.class, () -> vm.execute("1 0 /"));
    }

    // --- Stack Operations ---

    @Test
    void testDup() {
        vm.execute("5 DUP");
        assertEquals(2, vm.getDataStack().size());
        assertEquals(5L, vm.getDataStack().peek());
    }

    @Test
    void testDrop() {
        vm.execute("1 2 DROP");
        assertEquals(1, vm.getDataStack().size());
        assertEquals(1L, vm.getDataStack().peek());
    }

    @Test
    void testSwap() {
        vm.execute("1 2 SWAP");
        assertEquals(1L, vm.getDataStack().peek());
    }

    @Test
    void testOver() {
        vm.execute("1 2 OVER");
        assertEquals(3, vm.getDataStack().size());
        assertEquals(1L, vm.getDataStack().peek());
    }

    @Test
    void testRot() {
        vm.execute("1 2 3 ROT");
        assertEquals(1L, vm.getDataStack().peek());
    }

    @Test
    void testDepth() {
        vm.execute("1 2 3 DEPTH");
        // Stack before DEPTH: [1, 2, 3] = 3 items, DEPTH pushes 3
        assertEquals(3L, vm.getDataStack().peek());
    }

    @Test
    void testStackUnderflow() {
        assertThrows(MiniForthException.class, () -> vm.execute("DROP"));
    }

    // --- Comparison ---

    @Test
    void testEqual() {
        vm.execute("5 5 =");
        assertEquals(-1L, vm.getDataStack().peek()); // true
    }

    @Test
    void testNotEqual() {
        vm.execute("5 3 =");
        assertEquals(0L, vm.getDataStack().peek()); // false
    }

    @Test
    void testLessThan() {
        vm.execute("3 5 <");
        assertEquals(-1L, vm.getDataStack().peek());
    }

    @Test
    void testGreaterThan() {
        vm.execute("5 3 >");
        assertEquals(-1L, vm.getDataStack().peek());
    }

    // --- Logic ---

    @Test
    void testAnd() {
        vm.execute("-1 -1 AND");
        assertEquals(-1L, vm.getDataStack().peek());
    }

    @Test
    void testOr() {
        vm.execute("0 -1 OR");
        assertEquals(-1L, vm.getDataStack().peek());
    }

    @Test
    void testInvert() {
        vm.execute("0 INVERT");
        assertEquals(-1L, vm.getDataStack().peek());
    }

    // --- I/O ---

    @Test
    void testDot() {
        vm.execute("42 .");
        assertEquals("42", getOutput());
    }

    @Test
    void testCr() {
        vm.execute("CR");
        assertTrue(outputStream.toString().contains("\n"));
    }

    @Test
    void testPrintString() {
        vm.execute(".\" Hello World\"");
        assertEquals("Hello World", getOutput());
    }

    @Test
    void testEmit() {
        vm.execute("65 EMIT");
        assertEquals("A", getOutput());
    }

    // --- Word Definitions ---

    @Test
    void testSimpleWord() {
        vm.execute(": SQUARE DUP * ;");
        vm.execute("5 SQUARE");
        assertEquals(25L, vm.getDataStack().peek());
    }

    @Test
    void testNestedWords() {
        vm.execute(": SQUARE DUP * ;");
        vm.execute(": CUBE DUP SQUARE * ;");
        vm.execute("3 CUBE");
        assertEquals(27L, vm.getDataStack().peek());
    }

    @Test
    void testUndefinedWord() {
        assertThrows(MiniForthException.class, () -> vm.execute("NONEXISTENT"));
    }

    // --- Control Flow ---

    @Test
    void testIfThen() {
        vm.execute(": POS? DUP 0 > IF .\" positive\" THEN ;");
        vm.execute("5 POS?");
        assertTrue(getOutput().contains("positive"));
    }

    @Test
    void testIfElseThen() {
        vm.execute(": SIGN DUP 0 > IF .\" positive\" ELSE .\" non-positive\" THEN ;");
        vm.execute("-1 SIGN");
        assertTrue(getOutput().contains("non-positive"));
    }

    @Test
    void testDoLoop() {
        vm.execute(": COUNT 5 0 DO I . LOOP ;");
        vm.execute("COUNT");
        String out = getOutput();
        assertTrue(out.contains("0"));
        assertTrue(out.contains("4"));
    }

    @Test
    void testBeginUntil() {
        vm.execute(": COUNTDOWN BEGIN DUP . 1 - DUP 0 = UNTIL DROP ;");
        vm.execute("3 COUNTDOWN");
        String out = getOutput();
        assertTrue(out.contains("3"));
        assertTrue(out.contains("1"));
    }

    @Test
    void testBeginWhileRepeat() {
        vm.execute(": COUNTUP BEGIN DUP 5 < WHILE DUP . 1 + REPEAT DROP ;");
        vm.execute("0 COUNTUP");
        String out = getOutput();
        assertTrue(out.contains("0"));
        assertTrue(out.contains("4"));
    }

    // --- Variables and Constants ---

    @Test
    void testVariable() {
        vm.execute("VARIABLE x");
        vm.execute("42 x !");
        vm.execute("x @");
        assertEquals(42L, vm.getDataStack().peek());
    }

    @Test
    void testConstant() {
        vm.execute("42 CONSTANT ANSWER");
        vm.execute("ANSWER");
        assertEquals(42L, vm.getDataStack().peek());
    }

    // --- Floating Point ---

    @Test
    void testFloatArithmetic() {
        vm.execute("3.14 2.0 *");
        Object result = vm.getDataStack().peek();
        assertTrue(result instanceof Double);
        assertEquals(6.28, (Double) result, 0.001);
    }

    // --- Boolean ---

    @Test
    void testTrue() {
        vm.execute("TRUE");
        assertEquals(-1L, vm.getDataStack().peek());
    }

    @Test
    void testFalse() {
        vm.execute("FALSE");
        assertEquals(0L, vm.getDataStack().peek());
    }

    // --- Factorial ---

    @Test
    void testFactorial() {
        vm.execute(": FACTORIAL DUP 1 <= IF DROP 1 ELSE 1 SWAP 1 + 2 DO I * LOOP THEN ;");
        vm.execute("5 FACTORIAL");
        assertEquals(120L, vm.getDataStack().peek());
    }

    // --- Fibonacci ---

    @Test
    void testFibonacci() {
        vm.execute(": FIB DUP 1 <= IF ELSE 0 1 ROT 1 - 0 DO SWAP OVER + LOOP NIP THEN ;");
        vm.execute("10 FIB");
        assertEquals(55L, vm.getDataStack().peek());
    }

    // --- Nested loops ---

    @Test
    void testNestedDoLoop() {
        vm.execute(": NESTED 3 0 DO 3 0 DO I J * . LOOP CR LOOP ;");
        vm.execute("NESTED");
        String out = getOutput();
        assertFalse(out.isEmpty());
    }
}
