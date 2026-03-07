# MiniForth - A Forth Interpreter on the JVM

A stack-based Forth variant interpreter running on the Java Virtual Machine. MiniForth supports word definitions, control structures, variables, constants, an interactive debugger, an optimizing compiler pass, and performance benchmarks.

## Features

- **Lexer & Parser** - Tokenizes and compiles MiniForth source into bytecode
- **Stack-Based VM** - Data stack, return stack, word dictionary, loop support
- **Control Structures** - IF/ELSE/THEN, DO/LOOP/+LOOP, BEGIN/UNTIL/WHILE/REPEAT
- **Variables & Constants** - VARIABLE, CONSTANT, @, !
- **Interactive REPL** - Read-eval-print loop with multi-line definition support
- **File Execution** - Run MiniForth scripts from files with INCLUDE support
- **Debugger** - Breakpoints, step-through, stack inspection, variable viewing
- **Optimizer** - Constant folding, peephole optimizations, word inlining, dead code elimination
- **Benchmarks** - Performance comparison against pure Java (simple + JMH)
- **Standard Library** - Arithmetic, stack manipulation, I/O, comparisons, logic

## Requirements

- Java 21+
- Maven 3.8+

## Build & Run

```bash
# Build
mvn package

# Run REPL
java -jar target/miniforth-1.0-SNAPSHOT.jar

# Execute a file
java -jar target/miniforth-1.0-SNAPSHOT.jar examples/hello.mf

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
\ Arithmetic
3 4 +          \ 7
10 3 -         \ 7
6 7 *          \ 42
20 4 /         \ 5

\ Stack manipulation
5 DUP          \ 5 5
1 2 SWAP       \ 2 1
1 2 OVER       \ 1 2 1
1 2 DROP       \ 1

\ I/O
42 .           \ prints "42"
65 EMIT        \ prints "A"
CR             \ newline
." Hello"      \ prints "Hello"
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

\ BEGIN/WHILE/REPEAT
: COUNTUP BEGIN DUP 5 < WHILE DUP . 1 + REPEAT DROP ;
```

### Variables & Constants

```forth
VARIABLE x
42 x !         \ store 42 in x
x @ .          \ prints 42

42 CONSTANT ANSWER
ANSWER .       \ prints 42
```

## CLI Options

```
--repl, -r          Start REPL (also starts after file execution)
--debug, -d         Enable debugger
--break, -b <word>  Set breakpoint on word
--optimize, -O      Enable optimization passes
--verbose, -v       Enable verbose logging
--help, -h          Show help
```

## License

MIT
