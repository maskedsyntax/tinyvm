package com.tinyvm.miniforth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {

    private Parser.ParseResult parse(String source) {
        Lexer lexer = new Lexer(source);
        Parser parser = new Parser(lexer.tokenize());
        return parser.parse();
    }

    @Test
    void testSimpleExpression() {
        var result = parse("1 2 +");

        assertEquals(3, result.immediateCode().size());
        assertEquals(Instruction.OpCode.PUSH, result.immediateCode().get(0).opCode());
        assertEquals(1L, result.immediateCode().get(0).operand());
        assertEquals(Instruction.OpCode.PUSH, result.immediateCode().get(1).opCode());
        assertEquals(Instruction.OpCode.ADD, result.immediateCode().get(2).opCode());
    }

    @Test
    void testWordDefinition() {
        var result = parse(": SQUARE DUP * ;");

        assertEquals(0, result.immediateCode().size());
        assertEquals(1, result.definitions().size());
        assertEquals("SQUARE", result.definitions().get(0).name());

        List<Instruction> body = result.definitions().get(0).body();
        assertEquals(Instruction.OpCode.DUP, body.get(0).opCode());
        assertEquals(Instruction.OpCode.MUL, body.get(1).opCode());
        assertEquals(Instruction.OpCode.RETURN, body.get(2).opCode());
    }

    @Test
    void testIfThen() {
        var result = parse(": TEST 0 > IF 1 THEN ;");

        List<Instruction> body = result.definitions().get(0).body();
        // Should contain: PUSH(0), GT, BRANCH_FALSE, PUSH(1), RETURN
        boolean hasBranchFalse = body.stream()
                .anyMatch(i -> i.opCode() == Instruction.OpCode.BRANCH_FALSE);
        assertTrue(hasBranchFalse);
    }

    @Test
    void testIfElseThen() {
        var result = parse(": TEST 0 > IF 1 ELSE 2 THEN ;");

        List<Instruction> body = result.definitions().get(0).body();
        boolean hasBranch = body.stream()
                .anyMatch(i -> i.opCode() == Instruction.OpCode.BRANCH);
        assertTrue(hasBranch);
    }

    @Test
    void testDoLoop() {
        var result = parse(": TEST 10 0 DO I LOOP ;");

        List<Instruction> body = result.definitions().get(0).body();
        boolean hasDo = body.stream()
                .anyMatch(i -> i.opCode() == Instruction.OpCode.DO);
        boolean hasLoop = body.stream()
                .anyMatch(i -> i.opCode() == Instruction.OpCode.LOOP);
        assertTrue(hasDo);
        assertTrue(hasLoop);
    }

    @Test
    void testBeginUntil() {
        var result = parse(": TEST BEGIN DUP 10 > UNTIL ;");

        List<Instruction> body = result.definitions().get(0).body();
        boolean hasBranchFalse = body.stream()
                .anyMatch(i -> i.opCode() == Instruction.OpCode.BRANCH_FALSE);
        assertTrue(hasBranchFalse);
    }

    @Test
    void testUnterminatedDefinition() {
        assertThrows(MiniForthException.class, () -> parse(": TEST DUP"));
    }

    @Test
    void testVariable() {
        var result = parse("VARIABLE x");

        assertEquals(1, result.immediateCode().size());
        assertEquals(Instruction.OpCode.VARIABLE, result.immediateCode().get(0).opCode());
        assertEquals("x", result.immediateCode().get(0).operand());
    }

    @Test
    void testConstant() {
        var result = parse("42 CONSTANT ANSWER");

        assertEquals(2, result.immediateCode().size()); // PUSH(42), CONSTANT("ANSWER")
        assertEquals(Instruction.OpCode.PUSH, result.immediateCode().get(0).opCode());
        assertEquals(Instruction.OpCode.CONSTANT, result.immediateCode().get(1).opCode());
    }
}
