package com.tinyvm.miniforth;

/**
 * Represents a single bytecode instruction for the MiniForth VM.
 */
public record Instruction(OpCode opCode, Object operand) {

    public enum OpCode {
        // Stack operations
        PUSH,           // Push literal value onto stack
        DUP,
        DROP,
        SWAP,
        OVER,
        ROT,
        NIP,
        TUCK,
        PICK,           // ( n -- x ) copy nth item
        DEPTH,          // ( -- n ) stack depth
        TWO_DUP,        // 2DUP
        TWO_DROP,       // 2DROP
        TWO_SWAP,       // 2SWAP
        TWO_OVER,       // 2OVER

        // Arithmetic
        ADD,
        SUB,
        MUL,
        DIV,
        MOD,
        NEGATE,
        ABS,
        MIN,
        MAX,

        // Comparison
        EQ,
        NEQ,
        LT,
        GT,
        LE,
        GE,
        ZERO_EQ,        // 0=
        ZERO_LT,        // 0<
        ZERO_GT,        // 0>

        // Logic
        AND,
        OR,
        XOR,
        INVERT,

        // I/O
        DOT,            // . (print top of stack)
        EMIT,           // emit character
        CR,             // carriage return
        PRINT_STRING,   // ." ... "
        KEY,            // read character
        DOT_S,          // .S (print stack)

        // Control flow
        CALL,           // call a word
        RETURN,
        BRANCH,         // unconditional jump
        BRANCH_FALSE,   // conditional jump (if false)
        DO,             // start DO loop
        LOOP,           // end DO loop
        PLUS_LOOP,      // +LOOP
        LOOP_I,         // I (loop counter)
        LOOP_J,         // J (outer loop counter)
        LEAVE,          // exit loop early

        // Variables and constants
        VARIABLE,       // declare variable
        CONSTANT,       // declare constant
        FETCH,          // @ (read variable)
        STORE,          // ! (write variable)

        // Misc
        HALT,
        WORDS,          // list all defined words
        SEE,            // decompile a word
        BYE,            // exit interpreter
        INCLUDE,        // include a file
        NOP,            // no operation (used by optimizer)
    }

    public Instruction(OpCode opCode) {
        this(opCode, null);
    }

    @Override
    public String toString() {
        if (operand != null) {
            return "%s(%s)".formatted(opCode, operand);
        }
        return opCode.toString();
    }
}
