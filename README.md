# code-semi-graph
A command line for semi or graph code search

## Features

- **Semi Code Search**: Perform semi-structured code search with customizable options
  - **Build Index**: Build embedding index using Transformer models (Qwen3-Embedding-0.6B)
  - **Vector Database**: Store code embeddings for efficient similarity search (SQLite or ArcadeDB)
  - **Search**: Search code using the built index with cosine similarity
- **Graph Code Search**: Perform graph-based code search with traversal capabilities
  - **Graph Database**: Store code relationships (classes, methods, inheritance, calls) (SQLite or ArcadeDB)
  - **Graph Traversal**: BFS and DFS traversal with relationship filtering
  - **Relationship Types**: Support for extends, implements, contains, calls, uses relationships
- **Multi-Model Database Support**: Choose between SQLite and ArcadeDB for storage
  - **SQLite**: Lightweight, file-based database (default)
  - **ArcadeDB**: Multi-model database with native graph and vector support
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

Before searching, you should build an embedding index for your codebase. This creates a SQLite vector database with code embeddings:

```bash
java -jar build/libs/code-semi-graph-1.0.0.jar semi build
```

The build command will:
- Scan your codebase for code files
- Generate embeddings for each file (currently using mock embeddings)
- Store embeddings in a SQLite database at `./.code-index/embeddings.db`

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

Perform a graph-based code search using SQLite graph database. The graph database stores code relationships such as class inheritance, method calls, and imports.

```bash
java -jar build/libs/code-semi-graph-1.0.0.jar graph "node identifier"
```

The graph search command will:
- Create a sample graph database if it doesn't exist (for demonstration)
- Find the starting node by name
- Traverse the graph using BFS or DFS
- Display all connected nodes

Available options:
- `-p, --path <path>`: Path to search in (default: current directory)
- `-t, --traversal <type>`: Graph traversal type: BFS or DFS (default: BFS)
- `-m, --max-nodes <number>`: Maximum number of nodes to visit
- `-r, --relationship <type1,type2>`: Filter by relationship type (comma-separated)
  - Supported types: `extends`, `implements`, `contains`, `calls`, `uses`
- `--db-path <path>`: Path to graph database (default: ./.code-index/graph.db)
- `-h, --help`: Display help for the graph command

Example with BFS traversal:
```bash
java -jar build/libs/code-semi-graph-1.0.0.jar graph "MyClass" --traversal BFS
```

Example with relationship filtering:
```bash
java -jar build/libs/code-semi-graph-1.0.0.jar graph "MyClass" --relationship "contains,calls"
```

Example with DFS and custom database path:
```bash
java -jar build/libs/code-semi-graph-1.0.0.jar graph "MyClass" --traversal DFS --db-path /path/to/graph.db
```

## Testing

Run the test suite:

```bash
./gradlew test
```

## Database Architecture

The application supports two database backends for both vector and graph storage:

### Database Backends

#### SQLite (Default)
- **Vector Database Location**: `./.code-index/embeddings.db`
- **Graph Database Location**: `./.code-index/graph.db`
- **Pros**: Lightweight, single-file, no separate server needed
- **Cons**: Limited performance for large datasets

#### ArcadeDB
- **Database Location**: `./.code-index/arcadedb-vector` and `./.code-index/arcadedb-graph`
- **Pros**: Native graph support, better performance, multi-model (document, graph, vector)
- **Cons**: Slightly larger footprint

### Vector Database
- **Purpose**: Store code embeddings for semantic search
- **SQLite Schema**:
  - `embeddings` table: Stores file paths, content, and embedding vectors (JSON)
  - Indexed on `file_path` for fast lookups
- **ArcadeDB Schema**:
  - `Embedding` document type: Stores file paths, content, and embedding arrays
  - Indexed on `filePath` (unique) for fast lookups
- **Search**: Uses cosine similarity to find similar code

### Graph Database
- **Purpose**: Store code relationships and enable graph traversal
- **SQLite Schema**:
  - `nodes` table: Stores code entities (classes, methods, functions)
  - `edges` table: Stores relationships between nodes
  - Indexed on node names, types, and relationship types
- **ArcadeDB Schema**:
  - `CodeNode` vertex type: Stores code entities with properties
  - Edge types created dynamically for relationships (extends, calls, etc.)
  - Native graph traversal with built-in BFS/DFS support
- **Traversal**: Supports BFS and DFS with relationship filtering

### Using Different Database Backends

To use ArcadeDB implementations in your code:

```java
// Vector database with ArcadeDB
VectorDatabase vectorDb = new ArcadeDBVectorDatabase("./.code-index/arcadedb-vector");
vectorDb.initialize();

// Graph database with ArcadeDB
GraphDatabase graphDb = new ArcadeDBGraphDatabase("./.code-index/arcadedb-graph");
graphDb.initialize();
```

### Database Operations

View SQLite vector database contents:
```bash
sqlite3 ./.code-index/embeddings.db "SELECT file_path, length(embedding) FROM embeddings;"
```

View SQLite graph database contents:
```bash
sqlite3 ./.code-index/graph.db "SELECT * FROM nodes;"
sqlite3 ./.code-index/graph.db "SELECT * FROM edges;"
```

## Requirements

- Java 21 or higher
- Gradle 8.14 or higher (included via Gradle wrapper)
- Database drivers (included via dependencies):
  - SQLite JDBC driver for SQLite support
  - ArcadeDB engine for ArcadeDB support

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
│           ├── GraphSearchCommand.java    # Graph search command
│           └── db/
│               ├── VectorDatabase.java              # Vector DB interface
│               ├── SqliteVectorDatabase.java        # SQLite vector implementation
│               ├── ArcadeDBVectorDatabase.java      # ArcadeDB vector implementation
│               ├── GraphDatabase.java               # Graph DB interface
│               ├── SqliteGraphDatabase.java         # SQLite graph implementation
│               └── ArcadeDBGraphDatabase.java       # ArcadeDB graph implementation
└── test/
    └── java/
        └── com/ygmpkk/codesearch/
            └── CodeSearchCLITest.java     # Unit tests
```

## License

See the repository license for details.
