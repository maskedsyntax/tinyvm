package com.tinyvm.miniforth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class DebuggerTest {

    private ByteArrayOutputStream outputStream;
    private Debugger debugger;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(outputStream);
        debugger = new Debugger(ps);
    }

    @Test
    void testAddBreakpoint() {
        debugger.addBreakpoint("TEST");
        assertTrue(outputStream.toString().contains("Breakpoint set on: TEST"));
    }

    @Test
    void testRemoveBreakpoint() {
        debugger.addBreakpoint("TEST");
        outputStream.reset();
        debugger.removeBreakpoint("TEST");
        assertTrue(outputStream.toString().contains("Breakpoint removed: TEST"));
    }

    @Test
    void testRemoveNonexistentBreakpoint() {
        debugger.removeBreakpoint("NONEXISTENT");
        assertTrue(outputStream.toString().contains("No breakpoint on: NONEXISTENT"));
    }

    @Test
    void testCaseInsensitiveBreakpoints() {
        debugger.addBreakpoint("test");
        outputStream.reset();
        debugger.removeBreakpoint("TEST");
        assertTrue(outputStream.toString().contains("Breakpoint removed: TEST"));
    }
}
