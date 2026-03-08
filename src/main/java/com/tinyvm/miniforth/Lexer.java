package com.tinyvm.miniforth;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizes MiniForth source code into a list of tokens.
 * Handles numbers, strings, comments (\ for line, ( ) for block), and Forth words.
 */
public class Lexer {

    private final String source;
    private int pos;
    private int line;
    private int column;

    public Lexer(String source) {
        this.source = source;
        this.pos = 0;
        this.line = 1;
        this.column = 1;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        while (pos < source.length()) {
            skipWhitespace();
            if (pos >= source.length()) break;

            char c = source.charAt(pos);

            // Line comment: \ followed by space or end of line
            if (c == '\\' && (pos + 1 >= source.length() || source.charAt(pos + 1) == ' '
                    || source.charAt(pos + 1) == '\t' || source.charAt(pos + 1) == '\n')) {
                skipLineComment();
                continue;
            }

            // Block comment: ( followed by space
            if (c == '(' && pos + 1 < source.length() && source.charAt(pos + 1) == ' ') {
                skipBlockComment();
                continue;
            }

            // String literal: ." ... "
            if (c == '.' && pos + 1 < source.length() && source.charAt(pos + 1) == '"') {
                tokens.add(readPrintString());
                continue;
            }

            // String literal: s" ... "
            if ((c == 's' || c == 'S') && pos + 1 < source.length() && source.charAt(pos + 1) == '"') {
                tokens.add(readString());
                continue;
            }

            // Read a word (space-delimited)
            String word = readWord();
            if (word.isEmpty()) continue;

            tokens.add(classifyWord(word));
        }

        tokens.add(new Token(Token.TokenType.EOF, "", line, column));
        return tokens;
    }

    private Token classifyWord(String word) {
        int tokenLine = line;
        int tokenCol = column - word.length();

        // Check for numbers
        try {
            if (word.contains(".")) {
                Double.parseDouble(word);
                return new Token(Token.TokenType.FLOAT, word, tokenLine, tokenCol);
            }
            Long.parseLong(word);
            return new Token(Token.TokenType.NUMBER, word, tokenLine, tokenCol);
        } catch (NumberFormatException ignored) {
        }

        // Hex numbers (0x prefix)
        if (word.startsWith("0x") || word.startsWith("0X")) {
            try {
                Long.parseLong(word.substring(2), 16);
                return new Token(Token.TokenType.NUMBER, word, tokenLine, tokenCol);
            } catch (NumberFormatException ignored) {
            }
        }

        String upper = word.toUpperCase();
        Token.TokenType type = switch (upper) {
            case ":" -> Token.TokenType.COLON;
            case ";" -> Token.TokenType.SEMICOLON;
            case "IF" -> Token.TokenType.IF;
            case "ELSE" -> Token.TokenType.ELSE;
            case "THEN" -> Token.TokenType.THEN;
            case "DO" -> Token.TokenType.DO;
            case "LOOP" -> Token.TokenType.LOOP;
            case "+LOOP" -> Token.TokenType.PLUS_LOOP;
            case "BEGIN" -> Token.TokenType.BEGIN;
            case "UNTIL" -> Token.TokenType.UNTIL;
            case "WHILE" -> Token.TokenType.WHILE;
            case "REPEAT" -> Token.TokenType.REPEAT;
            case "VARIABLE" -> Token.TokenType.VARIABLE;
            case "CONSTANT" -> Token.TokenType.CONSTANT;
            case "@" -> Token.TokenType.FETCH;
            case "!" -> Token.TokenType.STORE;
            case "I" -> Token.TokenType.I_WORD;
            case "J" -> Token.TokenType.J_WORD;
            default -> Token.TokenType.WORD;
        };

        return new Token(type, word, tokenLine, tokenCol);
    }

    private void skipWhitespace() {
        while (pos < source.length()) {
            char c = source.charAt(pos);
            if (c == '\n') {
                line++;
                column = 1;
                pos++;
            } else if (c == ' ' || c == '\t' || c == '\r') {
                column++;
                pos++;
            } else {
                break;
            }
        }
    }

    private void skipLineComment() {
        while (pos < source.length() && source.charAt(pos) != '\n') {
            pos++;
        }
    }

    private void skipBlockComment() {
        pos += 2; // skip "(  "
        column += 2;
        while (pos < source.length()) {
            if (source.charAt(pos) == ')') {
                pos++;
                column++;
                return;
            }
            if (source.charAt(pos) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
            pos++;
        }
        throw new MiniForthException("Unterminated block comment", line, column);
    }

    private Token readPrintString() {
        int startLine = line;
        int startCol = column;
        pos += 2; // skip ."
        column += 2;

        // Skip leading space after ."
        if (pos < source.length() && source.charAt(pos) == ' ') {
            pos++;
            column++;
        }

        StringBuilder sb = new StringBuilder();
        while (pos < source.length() && source.charAt(pos) != '"') {
            if (source.charAt(pos) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
            sb.append(source.charAt(pos));
            pos++;
        }
        if (pos >= source.length()) {
            throw new MiniForthException("Unterminated string literal", startLine, startCol);
        }
        pos++; // skip closing "
        column++;

        return new Token(Token.TokenType.PRINT_STRING, sb.toString(), startLine, startCol);
    }

    private Token readString() {
        int startLine = line;
        int startCol = column;
        pos += 2; // skip s"
        column += 2;

        if (pos < source.length() && source.charAt(pos) == ' ') {
            pos++;
            column++;
        }

        StringBuilder sb = new StringBuilder();
        while (pos < source.length() && source.charAt(pos) != '"') {
            if (source.charAt(pos) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
            sb.append(source.charAt(pos));
            pos++;
        }
        if (pos >= source.length()) {
            throw new MiniForthException("Unterminated string literal", startLine, startCol);
        }
        pos++;
        column++;

        return new Token(Token.TokenType.STRING, sb.toString(), startLine, startCol);
    }

    private String readWord() {
        StringBuilder sb = new StringBuilder();
        while (pos < source.length() && !isWhitespace(source.charAt(pos))) {
            sb.append(source.charAt(pos));
            pos++;
            column++;
        }
        return sb.toString();
    }

    private boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }
}
