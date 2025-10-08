# code-semi-graph
A command line for semi or graph code search

## Features

- **Semi Code Search**: Perform semi-structured code search with customizable options
  - **Build Index**: Build embedding index using configurable embedding models
  - **Embedding Strategies**: Support for multiple embedding sources:
    - Mock embeddings for testing and demonstration
    - DJL-based local models (e.g., Qwen3-Embedding-0.6B) 
    - HTTP-based remote API services
  - **Vector Database**: Store code embeddings for efficient similarity search using ArcadeDB
  - **Search**: Search code using the built index with cosine similarity
- **Graph Code Search**: Perform graph-based code search with traversal capabilities
  - **Graph Database**: Store code relationships (classes, methods, inheritance, calls) using ArcadeDB
  - **Graph Traversal**: BFS and DFS traversal with relationship filtering
  - **Relationship Types**: Support for extends, implements, contains, calls, uses relationships
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

Before searching, you should build an embedding index for your codebase. This creates an ArcadeDB vector database with code embeddings:

```bash
java -jar build/libs/code-semi-graph-1.0.0.jar semi build
```

The build command will:
- Scan your codebase for code files
- Generate embeddings for each file using the specified embedding model
- Store embeddings in an ArcadeDB database at `~/.code-semi-graph/index/arcadedb-vector` by default

Available options:
- `-p, --path <path>`: Path to build the index from (default: current directory)
- `-o, --output <path>`: Output directory for the index (defaults to the configured index directory)
- `-d, --depth <number>`: Maximum directory depth to traverse
- `-e, --extensions <ext1,ext2>`: File extensions to index (comma-separated)
- `-m, --model <model>`: Embedding model to use. Can be:
  - `mock`: Mock embeddings for testing (default)
  - `Qwen/Qwen3-Embedding-0.6B` or other model names: Use with `--model-path`
  - `http://...` or `https://...`: Remote API endpoint URL
- `--model-name <name>`: Display name for the embedding model
- `--model-path <path>`: Path to local model files for DJL models
- `--embedding-dim <number>`: Embedding dimension override
- `--api-key <key>`: API key for HTTP-based embedding models
- `--batch-size <size>`: Batch size for processing files
- `--home <dir>`: Override the configuration home directory (default: `~/.code-semi-graph`)
- `-h, --help`: Display help for the build command

#### Embedding Model Strategies

The application supports three embedding strategies:

1. **Mock Embeddings** (default): For testing and demonstration
   ```bash
   java -jar build/libs/code-semi-graph-1.0.0.jar semi build --model mock
   ```

2. **DJL Local Models**: Load models like Qwen3-Embedding-0.6B locally using Deep Java Library
   ```bash
   java -jar build/libs/code-semi-graph-1.0.0.jar semi build \
     --model Qwen/Qwen3-Embedding-0.6B \
     --model-path /path/to/model/files
   ```

3. **HTTP Remote API**: Call remote embedding services via HTTP
   ```bash
   java -jar build/libs/code-semi-graph-1.0.0.jar semi build \
     --model https://api.example.com/v1/embeddings \
     --api-key your-api-key-here
   ```

Example:
```bash
java -jar build/libs/code-semi-graph-1.0.0.jar semi build --path ./src --extensions java,kt --batch-size 16
```

#### Configuration File

The CLI reads defaults from `config.yaml`, located in the home directory `~/.code-semi-graph/` by default. The file is created
automatically the first time you run a command and can be customised to change the default embedding model, index location and
search preferences.

```yaml
embedding:
  model: mock
  modelName: mock
  embeddingDimension: 1536
  modelPath: ./models/qwen
  apiKey: your-api-key
  batchSize: 32
index:
  directory: ./index
search:
  topK: 5
```

Use the `--home <dir>` option on any semi command to load configuration from an alternative directory.

#### Searching Code

Perform a semi-structured code search:

```bash
java -jar build/libs/code-semi-graph-1.0.0.jar semi "search query"
```

Available options:
- `-i, --index-dir <path>`: Directory that contains the embedding index (defaults to the configured index directory)
- `-l, --limit <number>`: Maximum number of results to display (defaults to configuration)
- Embedding overrides: `-m/--model`, `--model-name`, `--model-path`, `--embedding-dim`, `--api-key`
- `--home <dir>`: Override the configuration home directory
- `-h, --help`: Display help for the semi command

Example:
```bash
java -jar build/libs/code-semi-graph-1.0.0.jar semi "function name" --index-dir ~/.code-semi-graph/index --limit 5
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

The application uses ArcadeDB, a multi-model database with native support for both vector embeddings and graph operations.

### ArcadeDB
- **Vector Database Location**: `~/.code-semi-graph/index/arcadedb-vector`
- **Graph Database Location**: `./.code-index/arcadedb-graph`
- **Benefits**: Native graph support, efficient vector similarity search, multi-model (document, graph, vector)

### Vector Database
- **Purpose**: Store code embeddings for semantic search
- **Schema**:
  - `Embedding` document type: Stores file paths, content, and embedding arrays
  - Indexed on `filePath` (unique) for fast lookups
- **Search**: Uses cosine similarity to find similar code

### Graph Database
- **Purpose**: Store code relationships and enable graph traversal
- **Schema**:
  - `CodeNode` vertex type: Stores code entities with properties (nodeId, nodeType, name, filePath)
  - Edge types created dynamically for relationships (extends, calls, implements, contains, uses)
  - Native graph traversal with built-in BFS/DFS support
- **Traversal**: Supports BFS and DFS with relationship filtering

### Using the Database in Code

```java
// Vector database with ArcadeDB
VectorDatabase vectorDb = new ArcadeDBVectorDatabase("./.code-index/arcadedb-vector");
vectorDb.initialize();

// Graph database with ArcadeDB
GraphDatabase graphDb = new ArcadeDBGraphDatabase("./.code-index/arcadedb-graph");
graphDb.initialize();
```

### Embedding Models Architecture

The application uses a strategy pattern for embedding generation:

```java
// Create embedding model using factory
EmbeddingModel model = EmbeddingModelFactory.createModel("mock");
model.initialize();

// Generate embeddings
float[] embedding = model.generateEmbedding("public class Test { }");

// Clean up
model.close();
```

Available implementations:
- **MockEmbeddingModel**: Deterministic mock embeddings for testing
- **DjlEmbeddingModel**: Load and run transformer models locally using Deep Java Library
- **HttpEmbeddingModel**: Call remote embedding APIs via HTTP/HTTPS

## Requirements

- Java 21 or higher
- Gradle 8.14 or higher (included via Gradle wrapper)
- ArcadeDB engine (included via dependency)

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
│           ├── db/
│           │   ├── VectorDatabase.java              # Vector DB interface
│           │   ├── ArcadeDBVectorDatabase.java      # ArcadeDB vector implementation
│           │   ├── GraphDatabase.java               # Graph DB interface
│           │   └── ArcadeDBGraphDatabase.java       # ArcadeDB graph implementation
│           └── embedding/
│               ├── EmbeddingModel.java              # Abstract embedding model
│               ├── EmbeddingModelFactory.java       # Factory for creating models
│               ├── MockEmbeddingModel.java          # Mock implementation
│               ├── DjlEmbeddingModel.java           # DJL-based local models
│               └── HttpEmbeddingModel.java          # HTTP-based remote APIs
└── test/
    └── java/
        └── com/ygmpkk/codesearch/
            ├── CodeSearchCLITest.java               # CLI tests
            ├── db/
            │   ├── ArcadeDBVectorDatabaseTest.java
            │   └── ArcadeDBGraphDatabaseTest.java
            └── embedding/
                ├── MockEmbeddingModelTest.java
                ├── EmbeddingModelFactoryTest.java
                └── EmbeddingModelIntegrationTest.java
```

## License

See the repository license for details.
