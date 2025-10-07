# AGENTS.md — (code-semi-graph) Repository Guide

Scope: This file governs the entire repository.

Read this first if you're contributing, reviewing, or acting as an automated coding agent.

## Reading Order

README.md (overview, build instructions, usage)

GitHub Issues (tasks/backlog): https://github.com/ygmpkk/code-semi-graph/issues

build.gradle (dependencies and build configuration)

## Intent & Principles

- SOLID, KISS, YAGNI

- Modern Java best practices (Java 21+)

- CLI-first design: composable commands with clear interfaces

- Extensibility: modular command structure using picocli subcommands

- Performance: efficient embedding-based search with Transformer models

- Testability: modular boundaries, deterministic components, fast tests first

- Clarity: idiomatic Java naming, structured logging only (Log4j2)

## Expectations for Agents/Contributors

- Skim README.md for project context and usage patterns before coding.

- Drive all planning via GitHub Issues (no in-repo trackers).

- Keep changes small and focused; propose design decisions in issues.

- Add/Update tests for essential behaviors you change or add.

- For each new feature, add both unit and integration tests when feasible. Integration tests are as important as unit tests and should exercise end-to-end CLI behavior.

- Structured logging only (Log4j2); no System.out/System.err in production code except for CLI output.

- Build and test locally before pushing: `./gradlew build test`

## Session Handoff Protocol (GitHub Issues)

- Start: pick a ready P0 issue, self-assign, post a "Session Start" plan.

- During: post concise updates at milestones; adjust labels as needed.

- End: post "What landed" + "Next steps" and update labels/boards.

- If behavior/architecture changed, update README.md in the same commit.

## Code Organization

Project layout:

```
src/main/java/com/ygmpkk/codesearch/
├── CodeSearchCLI.java           # Main entry point, CLI orchestration
├── LoggingMixin.java            # Shared logging configuration
├── SemiSearchCommand.java       # Semi-structured search command
├── SemiBuildCommand.java        # Build embedding index
├── GraphSearchCommand.java      # Graph-based search command

src/main/resources/
└── log4j2.xml                   # Logging configuration

src/test/java/com/ygmpkk/codesearch/
└── CodeSearchCLITest.java       # CLI integration tests

build.gradle                      # Gradle build configuration
```

### File Layout Rules (Vertical Slice)

- One public class per file: each command class in its own file named after the class.

- Commands are organized by feature: Semi (embedding-based), Graph (traversal-based).

- Shared cross-cutting concerns (logging, configuration) use Mixins.

- Resources (log4j2.xml) kept in src/main/resources/.

- Tests mirror the main source structure under src/test/java/.

- Build artifacts go to build/, never commit generated files.

## Workflow & Quality

- Feature toggles/configuration via CLI options and log4j2.xml for runtime behavior.

- Public APIs (Command classes, options) must be stable and documented with Javadoc.

- Follow Java conventions: PascalCase for classes, camelCase for methods/fields.

- Dependency injection: use picocli's @Mixin and @Option for configuration.

- CLI commands must support --help and --version flags.

- Error handling: log structured context, provide clear error messages to users, exit with appropriate codes.

- Testing: use JUnit 5, test both success and error paths for commands.

- Source control: push cohesive changes after green build/tests (`./gradlew build test`).

- Keep the repo clean: .gitignore excludes build/, .gradle/, .idea/, .code-index/.

### Roadmap & Priorities

- **P0 - Core Search Functionality**:
  - Semi-structured search with Qwen3-Embedding-0.6B integration
  - Graph-based code search with BFS/DFS traversal
  - Index building and management

- **P1 - Enhanced Features**:
  - Support for multiple embedding models
  - Search result ranking and filtering
  - Configuration file support for default options
  - Search result caching

- **P2 - Developer Experience**:
  - Interactive mode for iterative searches
  - Output format options (JSON, plain text, colored)
  - Integration with popular IDEs and editors
  - Performance profiling and optimization

Keep GitHub issues atomic and linked to roadmap items; label by P0/P1/P2.

## Coding Standards

- Java 21+ features: use records, pattern matching, switch expressions where appropriate.

- Modern Java: enable all warnings, treat warnings as errors where feasible.

- One public class per file; filenames match class names exactly.

- Naming: classes PascalCase, methods camelCase, constants UPPER_SNAKE_CASE, packages lowercase.

- Logging: use Log4j2 with structured logging (logger.info/warn/error with parameters, not string concatenation).

- CLI output: user-facing output goes to System.out, errors to System.err; use logger for diagnostics.

- Commands: implement Callable<Integer>, return 0 for success, non-zero for errors.

- Options: use picocli annotations (@Command, @Option, @Parameters) with clear descriptions.

- No dead code: remove unused imports, methods, fields when no longer needed.

- Dependencies: minimize external dependencies, prefer standard library when possible.

- Build: use Gradle wrapper (./gradlew) for reproducible builds.

## Testing Standards

- Unit tests: test individual command logic in isolation.

- Integration tests: test full CLI execution with actual arguments.

- Use JUnit 5 features: @Test, @DisplayName, @ParameterizedTest for multiple scenarios.

- Test both success and failure cases.

- Mock external dependencies (file system, network) where appropriate.

- Keep tests fast: avoid unnecessary I/O or long-running operations.

- Run tests before committing: `./gradlew test`

## Build & Run

### Development

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run the application via Gradle
./gradlew run --args="--help"

# Run specific command
./gradlew run --args="semi build --path ./src"
```

### Production

```bash
# Build standalone JAR
./gradlew jar

# Run the JAR
java -jar build/libs/code-semi-graph-1.0.0.jar [command] [options]

# Or use distribution scripts
./gradlew installDist
./build/install/code-semi-graph/bin/code-semi-graph [command] [options]
```

## Documentation Rules

- README.md is the source of truth for usage and features. Keep it current.

- All task/progress tracking in GitHub Issues.

- Javadoc required for public classes and non-obvious methods.

- Update version in build.gradle and CodeSearchCLI when releasing.

## Ambiguity

- Prefer the simplest design that satisfies current requirements.

- If multiple options exist, document a brief rationale in the GitHub issue.

- For CLI design decisions, follow picocli best practices and conventions.

- User instructions take precedence over this document.

## Key Dependencies

- **picocli 4.7.7**: CLI framework for command parsing and execution

- **Log4j2 2.24.1**: Structured logging framework

- **JUnit 5.10.0**: Testing framework

- **Qwen3-Embedding-0.6B**: Transformer model for code embeddings (external)

## Common Tasks

### Adding a New Command

1. Create new class extending `Callable<Integer>` in `src/main/java/com/ygmpkk/codesearch/`
2. Annotate with `@Command` specifying name, description
3. Add `@Mixin LoggingMixin loggingMixin` for logging support
4. Add `@Option` and `@Parameters` for command arguments
5. Implement `call()` method returning exit code
6. Register as subcommand in parent command or `CodeSearchCLI`
7. Add tests in `src/test/java/com/ygmpkk/codesearch/`
8. Update README.md with usage examples

### Modifying Logging

- Edit `src/main/resources/log4j2.xml` for log levels and appenders
- Use structured logging: `logger.info("Processing file: {}", filename)`
- Never use `System.out.println` for logging in production code

### Releasing

1. Update version in `build.gradle`
2. Update version in `@Command` annotation in `CodeSearchCLI.java`
3. Build and test: `./gradlew clean build test`
4. Create distribution: `./gradlew distZip distTar`
5. Tag release in Git
6. Document changes in GitHub release notes

