# code-semi-graph
A command line for semi or graph code search

## Features

- **Semi Code Search**: Perform semi-structured code search with customizable options
  - **Build Index**: Build embedding index using Transformer models (Qwen3-Embedding-0.6B)
  - **Search**: Search code using the built index
- **Graph Code Search**: Perform graph-based code search with traversal capabilities
- Built with [picocli](https://picocli.info/) for a robust CLI experience
- Java 21+ compatible
- Gradle build system

## Building the Project

Build the project using Gradle:

```bash
./gradlew build
```

This will create a standalone JAR file in `build/libs/code-semi-graph-1.0.0.jar`.

## Running the Application

### Using Gradle

```bash
./gradlew run --args="[command] [options]"
```

### Using the JAR

```bash
java -jar build/libs/code-semi-graph-1.0.0.jar [command] [options]
```

## Usage

### Basic Commands

Display help information:
```bash
java -jar build/libs/code-semi-graph-1.0.0.jar --help
```

Display version:
```bash
java -jar build/libs/code-semi-graph-1.0.0.jar --version
```

### Semi Code Search

#### Building the Index

Before searching, you should build an embedding index for your codebase:

```bash
java -jar build/libs/code-semi-graph-1.0.0.jar semi build
```

Available options:
- `-p, --path <path>`: Path to build index from (default: current directory)
- `-o, --output <path>`: Output directory for the index (default: ./.code-index)
- `-d, --depth <number>`: Maximum directory depth to traverse
- `-e, --extensions <ext1,ext2>`: File extensions to index (comma-separated)
- `-m, --model <model>`: Embedding model to use (default: Qwen/Qwen3-Embedding-0.6B)
- `--batch-size <size>`: Batch size for processing files (default: 32)
- `-h, --help`: Display help for the build command

Example:
```bash
java -jar build/libs/code-semi-graph-1.0.0.jar semi build --path ./src --extensions java,kt --batch-size 16
```

#### Searching Code

Perform a semi-structured code search:

```bash
java -jar build/libs/code-semi-graph-1.0.0.jar semi "search query"
```

Available options:
- `-p, --path <path>`: Path to search in (default: current directory)
- `-d, --depth <number>`: Maximum search depth
- `-e, --extensions <ext1,ext2>`: File extensions to search (comma-separated)
- `-h, --help`: Display help for the semi command

Example:
```bash
java -jar build/libs/code-semi-graph-1.0.0.jar semi "function name" --path /src --depth 3 --extensions java,kt
```

### Graph Code Search

Perform a graph-based code search:

```bash
java -jar build/libs/code-semi-graph-1.0.0.jar graph "node identifier"
```

Available options:
- `-p, --path <path>`: Path to search in (default: current directory)
- `-t, --traversal <type>`: Graph traversal type: BFS or DFS (default: BFS)
- `-m, --max-nodes <number>`: Maximum number of nodes to visit
- `-r, --relationship <type1,type2>`: Filter by relationship type (comma-separated)
- `-h, --help`: Display help for the graph command

Example:
```bash
java -jar build/libs/code-semi-graph-1.0.0.jar graph "MyClass" --path /src --traversal DFS --max-nodes 100
```

## Testing

Run the test suite:

```bash
./gradlew test
```

## Requirements

- Java 21 or higher
- Gradle 8.14 or higher (included via Gradle wrapper)

## Development

The project structure:
```
src/
├── main/
│   └── java/
│       └── com/ygmpkk/codesearch/
│           ├── CodeSearchCLI.java         # Main CLI entry point
│           ├── SemiSearchCommand.java     # Semi search command group
│           ├── SemiBuildCommand.java      # Semi build index command
│           └── GraphSearchCommand.java    # Graph search command
└── test/
    └── java/
        └── com/ygmpkk/codesearch/
            └── CodeSearchCLITest.java     # Unit tests
```

## License

See the repository license for details.
