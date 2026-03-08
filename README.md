# MiniForth - A Forth Interpreter on the JVM

A high-performance, stack-based Forth variant interpreter running on the Java Virtual Machine. MiniForth supports word definitions, control structures, variables, constants, an interactive debugger, multiple optimization passes, and a JVM bytecode compiler.

## Features

- **JVM Bytecode Compiler** - Compiles MiniForth words to native JVM bytecode via ASM for maximum performance.
- **Lexer & Parser** - Hand-written recursive descent parser with precise line/column tracking.
- **Stack-Based VM** - Data stack, return stack, word dictionary, loop support.
- **Control Structures** - IF/ELSE/THEN, DO/LOOP/+LOOP, BEGIN/UNTIL/WHILE/REPEAT.
- **Variables & Constants** - VARIABLE, CONSTANT, @, !.
- **Interactive REPL** - Read-eval-print loop with multi-line definition support.
- **File Execution** - Run MiniForth scripts from files with INCLUDE support.
- **Debugger** - Breakpoints, step-through, stack inspection, variable viewing.
- **Optimizer** - Constant folding, peephole optimizations, word inlining, dead code elimination.
- **Extended Library** - Floating point (Double), Strings (SLEN, S+, SSUB), and File I/O (FOPEN, FCLOSE, FREAD, FWRITE).
- **Benchmarks** - Performance comparison against pure Java (simple + JMH).

## Requirements

- Java 21+
- Maven 3.8+

## Build & Run

```bash
# Build
mvn package

# Run REPL
java -jar target/miniforth-1.0-SNAPSHOT.jar

# Execute a file with JVM Bytecode Compilation
java -jar target/miniforth-1.0-SNAPSHOT.jar --compile examples/factorial.mf

# Run with debugger
java -jar target/miniforth-1.0-SNAPSHOT.jar --debug examples/factorial.mf

# Run with optimizer
java -jar target/miniforth-1.0-SNAPSHOT.jar --optimize examples/factorial.mf

# Run tests
mvn test

# Run benchmarks
java -cp target/miniforth-1.0-SNAPSHOT.jar com.tinyvm.miniforth.Benchmark
```

## MiniForth Language

### Basic Operations

```forth
\ Arithmetic & Floats
3 4 +          \ 7
3.14 2.0 * F.  \ 6.28
10 3 -         \ 7

\ Stack manipulation
5 DUP          \ 5 5
1 2 SWAP       \ 2 1
1 2 OVER       \ 1 2 1
1 2 DROP       \ 1

\ I/O & Strings
42 .           \ prints "42"
s" Hello" .S   \ push string to stack
." World"      \ prints "World"
CR             \ newline
```

### Word Definitions

```forth
: SQUARE ( n -- n*n ) DUP * ;
: CUBE ( n -- n*n*n ) DUP SQUARE * ;
5 SQUARE .     \ 25
3 CUBE .       \ 27
```

### Control Structures

```forth
\ IF/ELSE/THEN
: ABS DUP 0 < IF NEGATE THEN ;

\ DO/LOOP
: COUNT 10 0 DO I . LOOP ;

\ BEGIN/UNTIL
: COUNTDOWN BEGIN DUP . 1 - DUP 0 = UNTIL DROP ;
```

## CLI Options

```
--repl, -r          Start REPL (also starts after file execution)
--debug, -d         Enable debugger
--break, -b <word>  Set breakpoint on word
--optimize, -O      Enable optimization passes
--compile, -C       Enable JVM bytecode compilation (ASM)
--verbose, -v       Enable verbose logging
--help, -h          Show help
```

## Future Roadmap (TODO)

- [ ] **Invokedynamic Integration**: Use MethodHandles for advanced dynamic word dispatch optimization.
- [ ] **Security Sandboxing**: Restrict file system and system-level access for untrusted scripts.
- [ ] **GUI Debugger**: A graphical interface for stack inspection and breakpoint management (Swing/JavaFX).
- [ ] **Ahead-of-Time (AOT) Compilation**: Compile MiniForth scripts directly to standalone `.class` files.

## License

MIT
