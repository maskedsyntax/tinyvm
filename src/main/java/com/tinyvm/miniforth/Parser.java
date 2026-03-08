package com.tinyvm.miniforth;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a list of tokens into bytecode instructions.
 * Handles word definitions, control structures (IF/ELSE/THEN, DO/LOOP, BEGIN/UNTIL/WHILE/REPEAT),
 * variables, and constants.
 */
public class Parser {

    private final List<Token> tokens;
    private int pos;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    /**
     * Parse tokens into a ParseResult containing immediate instructions and word definitions.
     */
    public ParseResult parse() {
        List<Instruction> immediateCode = new ArrayList<>();
        List<WordDefinition> definitions = new ArrayList<>();

        while (pos < tokens.size() && tokens.get(pos).type() != Token.TokenType.EOF) {
            Token token = tokens.get(pos);

            if (token.type() == Token.TokenType.COLON) {
                definitions.add(parseWordDefinition());
            } else if (token.type() == Token.TokenType.VARIABLE) {
                int line = token.line();
                int col = token.column();
                pos++;
                Token name = expect(Token.TokenType.WORD, "Expected variable name after VARIABLE");
                immediateCode.add(new Instruction(Instruction.OpCode.VARIABLE, name.value(), line, col));
            } else if (token.type() == Token.TokenType.CONSTANT) {
                int line = token.line();
                int col = token.column();
                pos++;
                Token name = expect(Token.TokenType.WORD, "Expected constant name after CONSTANT");
                immediateCode.add(new Instruction(Instruction.OpCode.CONSTANT, name.value(), line, col));
            } else if (token.type() == Token.TokenType.IF) {
                parseIf(immediateCode);
            } else if (token.type() == Token.TokenType.DO) {
                parseDo(immediateCode);
            } else if (token.type() == Token.TokenType.BEGIN) {
                parseBegin(immediateCode);
            } else {
                immediateCode.add(parseToken(token));
                pos++;
            }
        }

        return new ParseResult(immediateCode, definitions);
    }

    private WordDefinition parseWordDefinition() {
        Token colonToken = tokens.get(pos);
        pos++; // skip :

        Token nameToken = expect(Token.TokenType.WORD, "Expected word name after ':'");
        String name = nameToken.value();

        List<Instruction> body = new ArrayList<>();

        while (pos < tokens.size() && tokens.get(pos).type() != Token.TokenType.SEMICOLON) {
            if (tokens.get(pos).type() == Token.TokenType.EOF) {
                throw new MiniForthException("Unterminated word definition for '%s'".formatted(name),
                        colonToken.line(), colonToken.column());
            }

            Token token = tokens.get(pos);

            if (token.type() == Token.TokenType.IF) {
                parseIf(body);
            } else if (token.type() == Token.TokenType.DO) {
                parseDo(body);
            } else if (token.type() == Token.TokenType.BEGIN) {
                parseBegin(body);
            } else {
                body.add(parseToken(token));
                pos++;
            }
        }

        if (pos >= tokens.size()) {
            throw new MiniForthException("Expected ';' to end word definition '%s'".formatted(name),
                    colonToken.line(), colonToken.column());
        }
        pos++; // skip ;

        body.add(new Instruction(Instruction.OpCode.RETURN, null, colonToken.line(), colonToken.column()));
        return new WordDefinition(name, body);
    }

    private void parseIf(List<Instruction> body) {
        Token ifToken = tokens.get(pos);
        pos++; // skip IF

        int branchFalseIdx = body.size();
        body.add(new Instruction(Instruction.OpCode.BRANCH_FALSE, 0, ifToken.line(), ifToken.column())); // placeholder

        while (pos < tokens.size()) {
            Token token = tokens.get(pos);
            if (token.type() == Token.TokenType.THEN) {
                pos++;
                // Patch the branch target
                Instruction old = body.get(branchFalseIdx);
                body.set(branchFalseIdx, new Instruction(Instruction.OpCode.BRANCH_FALSE, body.size(), old.line(), old.column()));
                return;
            } else if (token.type() == Token.TokenType.ELSE) {
                Token elseToken = tokens.get(pos);
                pos++;
                int branchIdx = body.size();
                body.add(new Instruction(Instruction.OpCode.BRANCH, 0, elseToken.line(), elseToken.column())); // placeholder for skip-else
                // Patch IF's false branch to here (start of else)
                Instruction oldIf = body.get(branchFalseIdx);
                body.set(branchFalseIdx, new Instruction(Instruction.OpCode.BRANCH_FALSE, body.size(), oldIf.line(), oldIf.column()));

                // Parse ELSE body
                while (pos < tokens.size() && tokens.get(pos).type() != Token.TokenType.THEN) {
                    Token eToken = tokens.get(pos);
                    if (eToken.type() == Token.TokenType.IF) {
                        parseIf(body);
                    } else if (eToken.type() == Token.TokenType.DO) {
                        parseDo(body);
                    } else if (eToken.type() == Token.TokenType.BEGIN) {
                        parseBegin(body);
                    } else {
                        body.add(parseToken(eToken));
                        pos++;
                    }
                }
                if (pos >= tokens.size()) {
                    throw new MiniForthException("Expected THEN after ELSE", elseToken.line(), elseToken.column());
                }
                pos++; // skip THEN
                body.set(branchIdx, new Instruction(Instruction.OpCode.BRANCH, body.size(), elseToken.line(), elseToken.column()));
                return;
            } else if (token.type() == Token.TokenType.SEMICOLON || token.type() == Token.TokenType.EOF) {
                throw new MiniForthException("Expected THEN to close IF block", token.line(), token.column());
            } else if (token.type() == Token.TokenType.IF) {
                parseIf(body);
            } else if (token.type() == Token.TokenType.DO) {
                parseDo(body);
            } else if (token.type() == Token.TokenType.BEGIN) {
                parseBegin(body);
            } else {
                body.add(parseToken(token));
                pos++;
            }
        }
        throw new MiniForthException("Unterminated IF block", ifToken.line(), ifToken.column());
    }

    private void parseDo(List<Instruction> body) {
        Token doToken = tokens.get(pos);
        pos++; // skip DO

        int doIdx = body.size();
        body.add(new Instruction(Instruction.OpCode.DO, 0, doToken.line(), doToken.column())); // placeholder for loop-end address

        int loopStart = body.size();

        while (pos < tokens.size()) {
            Token token = tokens.get(pos);
            if (token.type() == Token.TokenType.LOOP) {
                pos++;
                body.add(new Instruction(Instruction.OpCode.LOOP, loopStart, token.line(), token.column()));
                Instruction oldDo = body.get(doIdx);
                body.set(doIdx, new Instruction(Instruction.OpCode.DO, body.size(), oldDo.line(), oldDo.column()));
                return;
            } else if (token.type() == Token.TokenType.PLUS_LOOP) {
                pos++;
                body.add(new Instruction(Instruction.OpCode.PLUS_LOOP, loopStart, token.line(), token.column()));
                Instruction oldDo = body.get(doIdx);
                body.set(doIdx, new Instruction(Instruction.OpCode.DO, body.size(), oldDo.line(), oldDo.column()));
                return;
            } else if (token.type() == Token.TokenType.SEMICOLON || token.type() == Token.TokenType.EOF) {
                throw new MiniForthException("Expected LOOP to close DO block", token.line(), token.column());
            } else if (token.type() == Token.TokenType.IF) {
                parseIf(body);
            } else if (token.type() == Token.TokenType.DO) {
                parseDo(body);
            } else if (token.type() == Token.TokenType.BEGIN) {
                parseBegin(body);
            } else {
                body.add(parseToken(token));
                pos++;
            }
        }
        throw new MiniForthException("Unterminated DO block", doToken.line(), doToken.column());
    }

    private void parseBegin(List<Instruction> body) {
        Token beginToken = tokens.get(pos);
        pos++; // skip BEGIN

        int beginIdx = body.size();

        while (pos < tokens.size()) {
            Token token = tokens.get(pos);
            if (token.type() == Token.TokenType.UNTIL) {
                pos++;
                body.add(new Instruction(Instruction.OpCode.BRANCH_FALSE, beginIdx, token.line(), token.column()));
                return;
            } else if (token.type() == Token.TokenType.WHILE) {
                Token whileToken = tokens.get(pos);
                pos++;
                int whileBranchIdx = body.size();
                body.add(new Instruction(Instruction.OpCode.BRANCH_FALSE, 0, whileToken.line(), whileToken.column())); // placeholder

                while (pos < tokens.size() && tokens.get(pos).type() != Token.TokenType.REPEAT) {
                    Token repToken = tokens.get(pos);
                    if (repToken.type() == Token.TokenType.SEMICOLON || repToken.type() == Token.TokenType.EOF) {
                        throw new MiniForthException("Expected REPEAT after WHILE", repToken.line(), repToken.column());
                    } else if (repToken.type() == Token.TokenType.IF) {
                        parseIf(body);
                    } else if (repToken.type() == Token.TokenType.DO) {
                        parseDo(body);
                    } else if (repToken.type() == Token.TokenType.BEGIN) {
                        parseBegin(body);
                    } else {
                        body.add(parseToken(repToken));
                        pos++;
                    }
                }
                if (pos >= tokens.size()) {
                    throw new MiniForthException("Expected REPEAT after WHILE", whileToken.line(), whileToken.column());
                }
                Token repeatToken = tokens.get(pos);
                pos++; // skip REPEAT
                body.add(new Instruction(Instruction.OpCode.BRANCH, beginIdx, repeatToken.line(), repeatToken.column()));
                Instruction oldWhile = body.get(whileBranchIdx);
                body.set(whileBranchIdx, new Instruction(Instruction.OpCode.BRANCH_FALSE, body.size(), oldWhile.line(), oldWhile.column()));
                return;
            } else if (token.type() == Token.TokenType.SEMICOLON || token.type() == Token.TokenType.EOF) {
                throw new MiniForthException("Expected UNTIL or WHILE in BEGIN block", token.line(), token.column());
            } else if (token.type() == Token.TokenType.IF) {
                parseIf(body);
            } else if (token.type() == Token.TokenType.DO) {
                parseDo(body);
            } else if (token.type() == Token.TokenType.BEGIN) {
                parseBegin(body);
            } else {
                body.add(parseToken(token));
                pos++;
            }
        }
        throw new MiniForthException("Unterminated BEGIN block", beginToken.line(), beginToken.column());
    }

    private Instruction parseToken(Token token) {
        return switch (token.type()) {
            case NUMBER -> {
                String val = token.value();
                long num;
                if (val.startsWith("0x") || val.startsWith("0X")) {
                    num = Long.parseLong(val.substring(2), 16);
                } else {
                    num = Long.parseLong(val);
                }
                yield new Instruction(Instruction.OpCode.PUSH, num, token.line(), token.column());
            }
            case FLOAT -> new Instruction(Instruction.OpCode.PUSH, Double.parseDouble(token.value()), token.line(), token.column());
            case PRINT_STRING -> new Instruction(Instruction.OpCode.PRINT_STRING, token.value(), token.line(), token.column());
            case STRING -> new Instruction(Instruction.OpCode.PUSH_STRING, token.value(), token.line(), token.column());
            case FETCH -> new Instruction(Instruction.OpCode.FETCH, null, token.line(), token.column());
            case STORE -> new Instruction(Instruction.OpCode.STORE, null, token.line(), token.column());
            case I_WORD -> new Instruction(Instruction.OpCode.LOOP_I, null, token.line(), token.column());
            case J_WORD -> new Instruction(Instruction.OpCode.LOOP_J, null, token.line(), token.column());
            case WORD -> resolveWord(token);
            default -> throw new MiniForthException(
                    "Unexpected token: %s".formatted(token), token.line(), token.column());
        };
    }

    private Instruction resolveWord(Token token) {
        String upper = token.value().toUpperCase();
        int line = token.line();
        int col = token.column();
        return switch (upper) {
            case "+" -> new Instruction(Instruction.OpCode.ADD, null, line, col);
            case "-" -> new Instruction(Instruction.OpCode.SUB, null, line, col);
            case "*" -> new Instruction(Instruction.OpCode.MUL, null, line, col);
            case "/" -> new Instruction(Instruction.OpCode.DIV, null, line, col);
            case "MOD" -> new Instruction(Instruction.OpCode.MOD, null, line, col);
            case "NEGATE" -> new Instruction(Instruction.OpCode.NEGATE, null, line, col);
            case "ABS" -> new Instruction(Instruction.OpCode.ABS, null, line, col);
            case "MIN" -> new Instruction(Instruction.OpCode.MIN, null, line, col);
            case "MAX" -> new Instruction(Instruction.OpCode.MAX, null, line, col);
            case "DUP" -> new Instruction(Instruction.OpCode.DUP, null, line, col);
            case "DROP" -> new Instruction(Instruction.OpCode.DROP, null, line, col);
            case "SWAP" -> new Instruction(Instruction.OpCode.SWAP, null, line, col);
            case "OVER" -> new Instruction(Instruction.OpCode.OVER, null, line, col);
            case "ROT" -> new Instruction(Instruction.OpCode.ROT, null, line, col);
            case "NIP" -> new Instruction(Instruction.OpCode.NIP, null, line, col);
            case "TUCK" -> new Instruction(Instruction.OpCode.TUCK, null, line, col);
            case "PICK" -> new Instruction(Instruction.OpCode.PICK, null, line, col);
            case "DEPTH" -> new Instruction(Instruction.OpCode.DEPTH, null, line, col);
            case "2DUP" -> new Instruction(Instruction.OpCode.TWO_DUP, null, line, col);
            case "2DROP" -> new Instruction(Instruction.OpCode.TWO_DROP, null, line, col);
            case "2SWAP" -> new Instruction(Instruction.OpCode.TWO_SWAP, null, line, col);
            case "2OVER" -> new Instruction(Instruction.OpCode.TWO_OVER, null, line, col);
            case "=" -> new Instruction(Instruction.OpCode.EQ, null, line, col);
            case "<>" -> new Instruction(Instruction.OpCode.NEQ, null, line, col);
            case "<" -> new Instruction(Instruction.OpCode.LT, null, line, col);
            case ">" -> new Instruction(Instruction.OpCode.GT, null, line, col);
            case "<=" -> new Instruction(Instruction.OpCode.LE, null, line, col);
            case ">=" -> new Instruction(Instruction.OpCode.GE, null, line, col);
            case "0=" -> new Instruction(Instruction.OpCode.ZERO_EQ, null, line, col);
            case "0<" -> new Instruction(Instruction.OpCode.ZERO_LT, null, line, col);
            case "0>" -> new Instruction(Instruction.OpCode.ZERO_GT, null, line, col);
            case "AND" -> new Instruction(Instruction.OpCode.AND, null, line, col);
            case "OR" -> new Instruction(Instruction.OpCode.OR, null, line, col);
            case "XOR" -> new Instruction(Instruction.OpCode.XOR, null, line, col);
            case "INVERT" -> new Instruction(Instruction.OpCode.INVERT, null, line, col);
            case "." -> new Instruction(Instruction.OpCode.DOT, null, line, col);
            case "F." -> new Instruction(Instruction.OpCode.F_DOT, null, line, col);
            case "EMIT" -> new Instruction(Instruction.OpCode.EMIT, null, line, col);
            case "CR" -> new Instruction(Instruction.OpCode.CR, null, line, col);
            case "KEY" -> new Instruction(Instruction.OpCode.KEY, null, line, col);
            case ".S" -> new Instruction(Instruction.OpCode.DOT_S, null, line, col);
            case "TRUE" -> new Instruction(Instruction.OpCode.PUSH, -1L, line, col);
            case "FALSE" -> new Instruction(Instruction.OpCode.PUSH, 0L, line, col);
            case "WORDS" -> new Instruction(Instruction.OpCode.WORDS, null, line, col);
            case "SEE" -> new Instruction(Instruction.OpCode.SEE, null, line, col);
            case "BYE" -> new Instruction(Instruction.OpCode.BYE, null, line, col);
            case "INCLUDE" -> new Instruction(Instruction.OpCode.INCLUDE, null, line, col);
            case "LEAVE" -> new Instruction(Instruction.OpCode.LEAVE, null, line, col);
            case "SLEN" -> new Instruction(Instruction.OpCode.STR_LEN, null, line, col);
            case "S+" -> new Instruction(Instruction.OpCode.STR_CAT, null, line, col);
            case "SSUB" -> new Instruction(Instruction.OpCode.STR_SUB, null, line, col);
            case "FOPEN" -> new Instruction(Instruction.OpCode.FILE_OPEN, null, line, col);
            case "FCLOSE" -> new Instruction(Instruction.OpCode.FILE_CLOSE, null, line, col);
            case "FREAD" -> new Instruction(Instruction.OpCode.FILE_READ, null, line, col);
            case "FWRITE" -> new Instruction(Instruction.OpCode.FILE_WRITE, null, line, col);
            default -> new Instruction(Instruction.OpCode.CALL, token.value(), line, col);
        };
    }

    private Token expect(Token.TokenType type, String errorMsg) {
        if (pos >= tokens.size()) {
            throw new MiniForthException(errorMsg);
        }
        Token token = tokens.get(pos);
        if (token.type() != type) {
            throw new MiniForthException(errorMsg + ", got: " + token, token.line(), token.column());
        }
        pos++;
        return token;
    }

    /**
     * Result of parsing: immediate code to execute and word definitions to register.
     */
    public record ParseResult(List<Instruction> immediateCode, List<WordDefinition> definitions) {}

    /**
     * A named word definition with its compiled bytecode body.
     */
    public record WordDefinition(String name, List<Instruction> body) {}
}
