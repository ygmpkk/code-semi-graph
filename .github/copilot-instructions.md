# GitHub Copilot Instructions for code-semi-graph

This is a Java 21+ CLI application for code search using embeddings and graph traversal.

## Project Overview

- **Language**: Java 21+ with modern features (records, pattern matching, switch expressions)
- **CLI Framework**: picocli 4.7.7 for command parsing and execution
- **Logging**: Log4j2 2.24.1 with structured logging (no System.out/System.err in production code except CLI output)
- **Testing**: JUnit 5.10.0 with both unit and integration tests
- **Build**: Gradle with wrapper (./gradlew)

## Architecture

- CLI-first design with composable commands
- Commands implement `Callable<Integer>` and return 0 for success, non-zero for errors
- Modular structure: Semi (embedding-based search) and Graph (traversal-based search)
- ArcadeDB for vector and graph databases

## Code Standards

### Naming Conventions
- Classes: PascalCase
- Methods/fields: camelCase
- Constants: UPPER_SNAKE_CASE
- Packages: lowercase

### File Organization
- One public class per file, filename matches class name exactly
- Commands in `src/main/java/com/ygmpkk/codesearch/`
- Tests mirror main source under `src/test/java/`
- Resources in `src/main/resources/`

### Commands
- Annotate with `@Command` (name, description, mixinStandardHelpOptions = true)
- Include `@Mixin LoggingMixin loggingMixin` for logging support
- Use `@Option` and `@Parameters` with clear descriptions
- All commands must support --help and --version flags
- Implement `call()` method returning Integer exit code

### Logging
- Use Log4j2 structured logging: `logger.info("Message: {}", param)`
- No string concatenation in log messages
- CLI user output: System.out for results, System.err for errors
- Diagnostic/debug info: use logger

### Testing
- Unit tests for individual command logic
- Integration tests for full CLI execution
- Test both success and error paths
- Use @Test, @DisplayName, @ParameterizedTest
- Keep tests fast, mock external dependencies

## Development Workflow

1. Build: `./gradlew build`
2. Test: `./gradlew test`
3. Run: `./gradlew run --args="--help"`

## Key Files

- `AGENTS.md`: Comprehensive repository guide (read this first)
- `README.md`: User-facing documentation and usage
- `build.gradle`: Dependencies and build configuration
- `src/main/resources/log4j2.xml`: Logging configuration

## Dependencies

- picocli 4.7.7
- Log4j2 2.24.1
- JUnit 5.10.0
- ArcadeDB (vector and graph database)
- Qwen3-Embedding-0.6B (external embedding model)

## Principles

- SOLID, KISS, YAGNI
- Minimal dependencies, prefer standard library
- Javadoc for public classes and non-obvious methods
- No dead code
- Modular boundaries, deterministic components
- Performance-conscious with efficient algorithms

## When Making Changes

1. Keep changes small and focused
2. Add/update tests for changed behavior
3. Ensure build passes: `./gradlew build test`
4. Update README.md if behavior/architecture changes
5. Follow existing code patterns and style
