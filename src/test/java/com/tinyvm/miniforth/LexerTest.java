package com.tinyvm.miniforth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LexerTest {

    @Test
    void testBasicTokenization() {
        Lexer lexer = new Lexer("1 2 +");
        List<Token> tokens = lexer.tokenize();

        assertEquals(4, tokens.size()); // 1, 2, +, EOF
        assertEquals(Token.TokenType.NUMBER, tokens.get(0).type());
        assertEquals("1", tokens.get(0).value());
        assertEquals(Token.TokenType.NUMBER, tokens.get(1).type());
        assertEquals(Token.TokenType.WORD, tokens.get(2).type());
        assertEquals("+", tokens.get(2).value());
        assertEquals(Token.TokenType.EOF, tokens.get(3).type());
    }

    @Test
    void testWordDefinition() {
        Lexer lexer = new Lexer(": SQUARE DUP * ;");
        List<Token> tokens = lexer.tokenize();

        assertEquals(Token.TokenType.COLON, tokens.get(0).type());
        assertEquals(Token.TokenType.WORD, tokens.get(1).type());
        assertEquals("SQUARE", tokens.get(1).value());
        assertEquals(Token.TokenType.WORD, tokens.get(2).type());
        assertEquals(Token.TokenType.WORD, tokens.get(3).type());
        assertEquals(Token.TokenType.SEMICOLON, tokens.get(4).type());
    }

    @Test
    void testLineComment() {
        Lexer lexer = new Lexer("1 2 \\ this is a comment\n3 +");
        List<Token> tokens = lexer.tokenize();

        // 1, 2, 3, +, EOF = 5
        assertEquals(5, tokens.size());
    }

    @Test
    void testBlockComment() {
        Lexer lexer = new Lexer("1 ( this is a block comment ) 2 +");
        List<Token> tokens = lexer.tokenize();

        assertEquals(4, tokens.size()); // 1, 2, +, EOF
    }

    @Test
    void testStringLiteral() {
        Lexer lexer = new Lexer(".\" Hello World\" s\" FooBar\"");
        List<Token> tokens = lexer.tokenize();

        assertEquals(3, tokens.size()); // PRINT_STRING, STRING, EOF
        assertEquals(Token.TokenType.PRINT_STRING, tokens.get(0).type());
        assertEquals("Hello World", tokens.get(0).value());
        assertEquals(Token.TokenType.STRING, tokens.get(1).type());
        assertEquals("FooBar", tokens.get(1).value());
    }

    @Test
    void testControlStructures() {
        Lexer lexer = new Lexer("IF ELSE THEN DO LOOP BEGIN UNTIL");
        List<Token> tokens = lexer.tokenize();

        assertEquals(Token.TokenType.IF, tokens.get(0).type());
        assertEquals(Token.TokenType.ELSE, tokens.get(1).type());
        assertEquals(Token.TokenType.THEN, tokens.get(2).type());
        assertEquals(Token.TokenType.DO, tokens.get(3).type());
        assertEquals(Token.TokenType.LOOP, tokens.get(4).type());
        assertEquals(Token.TokenType.BEGIN, tokens.get(5).type());
        assertEquals(Token.TokenType.UNTIL, tokens.get(6).type());
    }

    @Test
    void testFloatNumber() {
        Lexer lexer = new Lexer("3.14");
        List<Token> tokens = lexer.tokenize();

        assertEquals(Token.TokenType.FLOAT, tokens.get(0).type());
        assertEquals("3.14", tokens.get(0).value());
    }

    @Test
    void testHexNumber() {
        Lexer lexer = new Lexer("0xFF");
        List<Token> tokens = lexer.tokenize();

        assertEquals(Token.TokenType.NUMBER, tokens.get(0).type());
        assertEquals("0xFF", tokens.get(0).value());
    }

    @Test
    void testLineAndColumnTracking() {
        Lexer lexer = new Lexer("1\n2\n3");
        List<Token> tokens = lexer.tokenize();

        assertEquals(1, tokens.get(0).line());
        assertEquals(2, tokens.get(1).line());
        assertEquals(3, tokens.get(2).line());
    }

    @Test
    void testUnterminatedBlockComment() {
        Lexer lexer = new Lexer("1 ( unclosed comment");
        assertThrows(MiniForthException.class, lexer::tokenize);
    }

    @Test
    void testUnterminatedString() {
        Lexer lexer = new Lexer(".\" unclosed string");
        assertThrows(MiniForthException.class, lexer::tokenize);
    }

    @Test
    void testVariableAndConstant() {
        Lexer lexer = new Lexer("VARIABLE x CONSTANT y");
        List<Token> tokens = lexer.tokenize();

        assertEquals(Token.TokenType.VARIABLE, tokens.get(0).type());
        assertEquals(Token.TokenType.WORD, tokens.get(1).type());
        assertEquals(Token.TokenType.CONSTANT, tokens.get(2).type());
        assertEquals(Token.TokenType.WORD, tokens.get(3).type());
    }
}
