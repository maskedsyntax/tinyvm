package com.tinyvm.miniforth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class OptimizerTest {

    private ForthVM vm;
    private Optimizer optimizer;

    @BeforeEach
    void setUp() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        vm = new ForthVM(new PrintStream(baos), System.in);
        optimizer = new Optimizer();
    }

    @Test
    void testConstantFolding() {
        vm.execute(": TEST 3 4 + ;");
        int originalSize = vm.getDictionary().get("TEST").size();

        optimizer.optimize(vm);
        int optimizedSize = vm.getDictionary().get("TEST").size();

        // Should fold 3 4 + into a single PUSH(7)
        assertTrue(optimizedSize < originalSize, "Optimizer should reduce instruction count");

        // Verify correctness
        vm.execute("TEST");
        assertEquals(7L, vm.getDataStack().peek());
    }

    @Test
    void testPeepholeSwapSwap() {
        vm.execute(": TEST 1 2 SWAP SWAP ;");
        optimizer.optimize(vm);

        vm.execute("TEST");
        assertEquals(2L, vm.pop());
        assertEquals(1L, vm.pop());
    }

    @Test
    void testOptimizedFactorial() {
        vm.execute(": FACTORIAL DUP 1 <= IF DROP 1 ELSE 1 SWAP 1 + 2 DO I * LOOP THEN ;");
        optimizer.optimize(vm);

        // Should still produce correct results after optimization
        vm.execute("5 FACTORIAL");
        assertEquals(120L, vm.getDataStack().peek());
    }

    @Test
    void testOptimizedFibonacci() {
        vm.execute(": FIB DUP 1 <= IF ELSE 0 1 ROT 1 - 0 DO SWAP OVER + LOOP NIP THEN ;");
        optimizer.optimize(vm);

        vm.execute("10 FIB");
        assertEquals(55L, vm.getDataStack().peek());
    }
}
