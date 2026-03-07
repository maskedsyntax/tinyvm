package com.tinyvm.miniforth;

/**
 * Represents a lexical token from MiniForth source code.
 */
public record Token(TokenType type, String value, int line, int column) {

    public enum TokenType {
        NUMBER,
        FLOAT,
        STRING,
        WORD,
        COLON,          // :
        SEMICOLON,      // ;
        IF,
        ELSE,
        THEN,
        DO,
        LOOP,
        PLUS_LOOP,      // +LOOP
        BEGIN,
        UNTIL,
        WHILE,
        REPEAT,
        VARIABLE,
        CONSTANT,
        FETCH,          // @
        STORE,          // !
        I_WORD,         // I (loop counter)
        J_WORD,         // J (outer loop counter)
        EOF
    }

    @Override
    public String toString() {
        return "Token[%s '%s' at %d:%d]".formatted(type, value, line, column);
    }
}
