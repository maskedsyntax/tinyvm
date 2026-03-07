package com.tinyvm.miniforth;

/**
 * Exception type for MiniForth runtime and parse errors.
 * Includes source location information when available.
 */
public class MiniForthException extends RuntimeException {

    private final int line;
    private final int column;

    public MiniForthException(String message) {
        super(message);
        this.line = -1;
        this.column = -1;
    }

    public MiniForthException(String message, int line, int column) {
        super(formatMessage(message, line, column));
        this.line = line;
        this.column = column;
    }

    public MiniForthException(String message, Throwable cause) {
        super(message, cause);
        this.line = -1;
        this.column = -1;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    private static String formatMessage(String message, int line, int column) {
        if (line > 0 && column > 0) {
            return "%s [at line %d, column %d]".formatted(message, line, column);
        }
        return message;
    }
}
