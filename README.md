# code-semi-graph
A command line for semi or graph code search

## Features

- **Semi Code Search**: Perform semi-structured code search with customizable options
  - **Build Index**: Build embedding index using configurable embedding models with intelligent code parsing
  - **Tree-sitter Integration**: Parse source code AST for 25+ languages to extract structure
  - **Intelligent Chunking**: Split code by method blocks (max 32K tokens per chunk)
  - **Metadata Extraction**: Capture package name, class name, properties, methods, and call chains
  - **Embedding Strategies**: Support for multiple embedding sources:
    - Mock embeddings for testing and demonstration
    - DJL-based local models (e.g., Qwen3-Embedding-0.6B) 
    - HTTP-based remote API services
  - **Vector Database**: Store code embeddings for efficient similarity search using ArcadeDB
  - **Search**: Search code using the built index with cosine similarity
- **Graph Code Search**: Perform graph-based code search with traversal capabilities
  - **Graph Database**: Store code relationships (classes, methods, inheritance, calls) using ArcadeDB
  - **Call Chain Analysis**: Automatically extract and store method call relationships
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

Before searching, you should build an embedding index for your codebase. This creates ArcadeDB databases with code embeddings and graph relationships:

```bash
java -jar build/libs/code-semi-graph-1.0.0.jar semi build
```

The build command will:
- Scan your codebase for code files
- **Automatically detect language from file extension**
- **Parse files with tree-sitter** to extract package, class, method, and property information for 25+ languages
- **Chunk code intelligently** by method blocks (max 32K tokens per chunk) with full metadata context
- **Extract call chains** by analyzing method invocations in the AST
- Generate embeddings for each code chunk using the specified embedding model
- Store chunks with metadata in ArcadeDB vector database at `~/.code-semi-graph/index/arcadedb-vector`
- Store call chain relationships in ArcadeDB graph database at `~/.code-semi-graph/index/arcadedb-graph`
- For unsupported file types, fall back to simple file-based embedding

Each code chunk includes:
- File path, package name, class name, method name
- Field/property information
- Method body code
- List of methods called within the method (callees)

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

#### Tree-sitter Code Parsing

The build command uses tree-sitter to automatically parse source files based on their file extension. It supports **25+ programming languages** including:

**Supported Languages:**
- Java, Kotlin, Scala
- Python
- JavaScript, TypeScript, TSX
- Go
- Rust
- C, C++, C#
- Ruby, PHP
- Swift
- Dart
- Lua
- R
- Bash/Shell

**What gets extracted:**
- Package/module declarations (language-specific)
- Class/interface/enum/struct/trait names
- Field declarations (properties)
- Method/function declarations with signatures
- Method call chains (which methods call which)

**Intelligent Chunking:**
- Each method becomes a separate chunk with full context
- Maximum 32K tokens per chunk (approximately 128K characters)
- Each chunk includes metadata: file path, package, class, method name, properties, and callees
- Enables more precise code search at the method level

**Call Chain Graph:**
- Automatically builds a graph database of code relationships
- Stores nodes for classes and methods
- Stores edges for "contains" (class contains method) and "calls" (method calls method)
- Enables graph-based code exploration and dependency analysis

**Automatic Language Detection:**
- Detects language from file extension automatically
- No manual configuration needed
- Supports all tree-sitter languages with a single parser

**Fallback Behavior:**
- Tree-sitter requires native libraries for optimal performance
- If native libraries are not available, automatically falls back to simple file-based indexing
- Unsupported file types use simple file-based indexing

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

Perform a graph-based code search using ArcadeDB graph database. The graph database stores code relationships such as class-method containment and method call chains.

**Note:** The graph database is automatically populated when running `semi build` on supported source files with tree-sitter parsing enabled.

```bash
java -jar build/libs/code-semi-graph-1.0.0.jar graph "node identifier"
```

The graph search command will:
- Load the graph database created during `semi build`
- Find the starting node by name
- Traverse the graph using BFS or DFS
- Display all connected nodes with their relationships

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
- **Purpose**: Store code embeddings for semantic search with rich metadata
- **Schema**:
  - `Embedding` document type: Stores file paths, package name, class name, method name, content, and embedding arrays
  - Indexed on `filePath` for fast lookups
  - Each embedding represents a code chunk (typically a method) with full context
- **Search**: Uses cosine similarity to find similar code
- **Metadata Fields**:
  - `filePath`: Source file path
  - `packageName`: Java package name
  - `className`: Class/interface/enum name
  - `methodName`: Method name (or empty for file-level chunks)
  - `content`: Code chunk content with metadata context
  - `embedding`: Float array of embedding vector

### Graph Database
- **Purpose**: Store code relationships and enable graph traversal
- **Schema**:
  - `CodeNode` vertex type: Stores code entities with properties (nodeId, nodeType, name, filePath)
  - Edge types created dynamically for relationships (extends, calls, implements, contains, uses)
  - Native graph traversal with built-in BFS/DFS support
- **Traversal**: Supports BFS and DFS with relationship filtering
- **Relationship Types**:
  - `contains`: Class contains method
  - `calls`: Method calls another method
  - `extends`, `implements`, `uses`: (Future: inheritance and usage relationships)

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
- Tree-sitter native libraries (optional, for enhanced Java parsing)

### Key Dependencies

- **picocli 4.7.7**: Command-line interface framework
- **Log4j2 2.24.1**: Structured logging
- **ArcadeDB 24.6.1**: Multi-model database (vector + graph)
- **DJL 0.34.0**: Deep Java Library for embedding models
- **java-tree-sitter 1.12.0**: Tree-sitter bindings for source code parsing
- **OkHttp 4.12.0**: HTTP client for remote APIs
- **Gson 2.10.1**: JSON parsing
- **SnakeYAML 2.2**: YAML configuration

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
│           ├── embedding/
│           │   ├── EmbeddingModel.java              # Abstract embedding model
│           │   ├── EmbeddingModelFactory.java       # Factory for creating models
│           │   ├── MockEmbeddingModel.java          # Mock implementation
│           │   ├── DjlEmbeddingModel.java           # DJL-based local models
│           │   └── HttpEmbeddingModel.java          # HTTP-based remote APIs
│           └── parser/
│               ├── TreeSitterParser.java            # Tree-sitter AST parser
│               ├── CodeMetadata.java                # Code metadata record
│               └── CodeChunker.java                 # Intelligent code chunking
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
