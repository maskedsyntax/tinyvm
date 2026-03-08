package com.tinyvm.miniforth;

/**
 * Represents a single bytecode instruction for the MiniForth VM.
 */
public record Instruction(OpCode opCode, Object operand, int line, int column) {

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
        F_DOT,          // F. (print float)
        EMIT,           // emit character
        CR,             // carriage return
        PRINT_STRING,   // ." ... "
        PUSH_STRING,    // s" ... " (push to stack)
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

        // Extended Library: Strings
        STR_LEN,
        STR_CAT,
        STR_SUB,

        // Extended Library: File I/O
        FILE_OPEN,
        FILE_CLOSE,
        FILE_READ,
        FILE_WRITE,

        // Misc
        HALT,
        WORDS,          // list all defined words
        SEE,            // decompile a word
        BYE,            // exit interpreter
        INCLUDE,        // include a file
        NOP,            // no operation (used by optimizer)
        NATIVE_CALL,    // call a compiled JVM method
    }

    public Instruction(OpCode opCode, Object operand) {
        this(opCode, operand, -1, -1);
    }

    public Instruction(OpCode opCode) {
        this(opCode, null, -1, -1);
    }

    @Override
    public String toString() {
        String base = (operand != null) ? "%s(%s)".formatted(opCode, operand) : opCode.toString();
        if (line > 0 && column > 0) {
            return "%s [%d:%d]".formatted(base, line, column);
        }
        return base;
    }
}
