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
                pos++;
                Token name = expect(Token.TokenType.WORD, "Expected variable name after VARIABLE");
                immediateCode.add(new Instruction(Instruction.OpCode.VARIABLE, name.value()));
            } else if (token.type() == Token.TokenType.CONSTANT) {
                pos++;
                Token name = expect(Token.TokenType.WORD, "Expected constant name after CONSTANT");
                immediateCode.add(new Instruction(Instruction.OpCode.CONSTANT, name.value()));
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

        body.add(new Instruction(Instruction.OpCode.RETURN));
        return new WordDefinition(name, body);
    }

    private void parseIf(List<Instruction> body) {
        pos++; // skip IF

        int branchFalseIdx = body.size();
        body.add(new Instruction(Instruction.OpCode.BRANCH_FALSE, 0)); // placeholder

        while (pos < tokens.size()) {
            Token token = tokens.get(pos);
            if (token.type() == Token.TokenType.THEN) {
                pos++;
                // Patch the branch target
                body.set(branchFalseIdx, new Instruction(Instruction.OpCode.BRANCH_FALSE, body.size()));
                return;
            } else if (token.type() == Token.TokenType.ELSE) {
                pos++;
                int branchIdx = body.size();
                body.add(new Instruction(Instruction.OpCode.BRANCH, 0)); // placeholder for skip-else
                // Patch IF's false branch to here (start of else)
                body.set(branchFalseIdx, new Instruction(Instruction.OpCode.BRANCH_FALSE, body.size()));

                // Parse ELSE body
                while (pos < tokens.size() && tokens.get(pos).type() != Token.TokenType.THEN) {
                    Token elseToken = tokens.get(pos);
                    if (elseToken.type() == Token.TokenType.IF) {
                        parseIf(body);
                    } else if (elseToken.type() == Token.TokenType.DO) {
                        parseDo(body);
                    } else if (elseToken.type() == Token.TokenType.BEGIN) {
                        parseBegin(body);
                    } else {
                        body.add(parseToken(elseToken));
                        pos++;
                    }
                }
                if (pos >= tokens.size()) {
                    throw new MiniForthException("Expected THEN after ELSE");
                }
                pos++; // skip THEN
                body.set(branchIdx, new Instruction(Instruction.OpCode.BRANCH, body.size()));
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
        throw new MiniForthException("Unterminated IF block");
    }

    private void parseDo(List<Instruction> body) {
        pos++; // skip DO

        int doIdx = body.size();
        body.add(new Instruction(Instruction.OpCode.DO, 0)); // placeholder for loop-end address

        int loopStart = body.size();

        while (pos < tokens.size()) {
            Token token = tokens.get(pos);
            if (token.type() == Token.TokenType.LOOP) {
                pos++;
                body.add(new Instruction(Instruction.OpCode.LOOP, loopStart));
                body.set(doIdx, new Instruction(Instruction.OpCode.DO, body.size()));
                return;
            } else if (token.type() == Token.TokenType.PLUS_LOOP) {
                pos++;
                body.add(new Instruction(Instruction.OpCode.PLUS_LOOP, loopStart));
                body.set(doIdx, new Instruction(Instruction.OpCode.DO, body.size()));
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
        throw new MiniForthException("Unterminated DO block");
    }

    private void parseBegin(List<Instruction> body) {
        pos++; // skip BEGIN

        int beginIdx = body.size();

        while (pos < tokens.size()) {
            Token token = tokens.get(pos);
            if (token.type() == Token.TokenType.UNTIL) {
                pos++;
                body.add(new Instruction(Instruction.OpCode.BRANCH_FALSE, beginIdx));
                return;
            } else if (token.type() == Token.TokenType.WHILE) {
                pos++;
                int whileBranchIdx = body.size();
                body.add(new Instruction(Instruction.OpCode.BRANCH_FALSE, 0)); // placeholder

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
                    throw new MiniForthException("Expected REPEAT after WHILE");
                }
                pos++; // skip REPEAT
                body.add(new Instruction(Instruction.OpCode.BRANCH, beginIdx));
                body.set(whileBranchIdx, new Instruction(Instruction.OpCode.BRANCH_FALSE, body.size()));
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
        throw new MiniForthException("Unterminated BEGIN block");
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
                yield new Instruction(Instruction.OpCode.PUSH, num);
            }
            case FLOAT -> new Instruction(Instruction.OpCode.PUSH, Double.parseDouble(token.value()));
            case STRING -> new Instruction(Instruction.OpCode.PRINT_STRING, token.value());
            case FETCH -> new Instruction(Instruction.OpCode.FETCH);
            case STORE -> new Instruction(Instruction.OpCode.STORE);
            case I_WORD -> new Instruction(Instruction.OpCode.LOOP_I);
            case J_WORD -> new Instruction(Instruction.OpCode.LOOP_J);
            case WORD -> resolveWord(token);
            default -> throw new MiniForthException(
                    "Unexpected token: %s".formatted(token), token.line(), token.column());
        };
    }

    private Instruction resolveWord(Token token) {
        String upper = token.value().toUpperCase();
        return switch (upper) {
            case "+" -> new Instruction(Instruction.OpCode.ADD);
            case "-" -> new Instruction(Instruction.OpCode.SUB);
            case "*" -> new Instruction(Instruction.OpCode.MUL);
            case "/" -> new Instruction(Instruction.OpCode.DIV);
            case "MOD" -> new Instruction(Instruction.OpCode.MOD);
            case "NEGATE" -> new Instruction(Instruction.OpCode.NEGATE);
            case "ABS" -> new Instruction(Instruction.OpCode.ABS);
            case "MIN" -> new Instruction(Instruction.OpCode.MIN);
            case "MAX" -> new Instruction(Instruction.OpCode.MAX);
            case "DUP" -> new Instruction(Instruction.OpCode.DUP);
            case "DROP" -> new Instruction(Instruction.OpCode.DROP);
            case "SWAP" -> new Instruction(Instruction.OpCode.SWAP);
            case "OVER" -> new Instruction(Instruction.OpCode.OVER);
            case "ROT" -> new Instruction(Instruction.OpCode.ROT);
            case "NIP" -> new Instruction(Instruction.OpCode.NIP);
            case "TUCK" -> new Instruction(Instruction.OpCode.TUCK);
            case "PICK" -> new Instruction(Instruction.OpCode.PICK);
            case "DEPTH" -> new Instruction(Instruction.OpCode.DEPTH);
            case "2DUP" -> new Instruction(Instruction.OpCode.TWO_DUP);
            case "2DROP" -> new Instruction(Instruction.OpCode.TWO_DROP);
            case "2SWAP" -> new Instruction(Instruction.OpCode.TWO_SWAP);
            case "2OVER" -> new Instruction(Instruction.OpCode.TWO_OVER);
            case "=" -> new Instruction(Instruction.OpCode.EQ);
            case "<>" -> new Instruction(Instruction.OpCode.NEQ);
            case "<" -> new Instruction(Instruction.OpCode.LT);
            case ">" -> new Instruction(Instruction.OpCode.GT);
            case "<=" -> new Instruction(Instruction.OpCode.LE);
            case ">=" -> new Instruction(Instruction.OpCode.GE);
            case "0=" -> new Instruction(Instruction.OpCode.ZERO_EQ);
            case "0<" -> new Instruction(Instruction.OpCode.ZERO_LT);
            case "0>" -> new Instruction(Instruction.OpCode.ZERO_GT);
            case "AND" -> new Instruction(Instruction.OpCode.AND);
            case "OR" -> new Instruction(Instruction.OpCode.OR);
            case "XOR" -> new Instruction(Instruction.OpCode.XOR);
            case "INVERT" -> new Instruction(Instruction.OpCode.INVERT);
            case "." -> new Instruction(Instruction.OpCode.DOT);
            case "EMIT" -> new Instruction(Instruction.OpCode.EMIT);
            case "CR" -> new Instruction(Instruction.OpCode.CR);
            case "KEY" -> new Instruction(Instruction.OpCode.KEY);
            case ".S" -> new Instruction(Instruction.OpCode.DOT_S);
            case "TRUE" -> new Instruction(Instruction.OpCode.PUSH, -1L);
            case "FALSE" -> new Instruction(Instruction.OpCode.PUSH, 0L);
            case "WORDS" -> new Instruction(Instruction.OpCode.WORDS);
            case "SEE" -> new Instruction(Instruction.OpCode.SEE);
            case "BYE" -> new Instruction(Instruction.OpCode.BYE);
            case "INCLUDE" -> new Instruction(Instruction.OpCode.INCLUDE);
            case "LEAVE" -> new Instruction(Instruction.OpCode.LEAVE);
            default -> new Instruction(Instruction.OpCode.CALL, token.value());
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
