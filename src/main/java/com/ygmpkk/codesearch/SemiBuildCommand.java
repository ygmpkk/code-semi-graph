package com.ygmpkk.codesearch;

import ch.usi.si.seart.treesitter.Language;
import com.ygmpkk.codesearch.config.AppConfig;
import com.ygmpkk.codesearch.config.ConfigLoader;
import com.ygmpkk.codesearch.db.ArcadeDBVectorDatabase;
import com.ygmpkk.codesearch.db.ArcadeDBGraphDatabase;
import com.ygmpkk.codesearch.db.VectorDatabase;
import com.ygmpkk.codesearch.db.GraphDatabase;
import com.ygmpkk.codesearch.embedding.EmbeddingModel;
import com.ygmpkk.codesearch.parser.TreeSitterParser;
import com.ygmpkk.codesearch.parser.CodeMetadata;
import com.ygmpkk.codesearch.parser.CodeChunker;
import com.ygmpkk.codesearch.parser.LanguageDetector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * Command for building embedding index for semi code search
 */
@Command(
        name = "build",
        description = "Build embedding index for code search using Qwen3-Embedding-0.6B",
        mixinStandardHelpOptions = true
)
public class SemiBuildCommand implements Callable<Integer> {
    private static final Logger logger = LogManager.getLogger(SemiBuildCommand.class);

    @Mixin
    LoggingMixin loggingMixin;

    @Mixin
    EmbeddingOptions embeddingOptions;

    @Option(
            names = {"-p", "--path"},
            description = "Path to build index from",
            paramLabel = "PATH"
    )
    private Path sourcePath;

    @Option(
            names = {"-o", "--output"},
            description = "Output directory for the index",
            paramLabel = "DIR"
    )
    private Path outputDir;

    @Option(
            names = {"-e", "--extensions"},
            description = "File extensions to index (comma-separated)",
            split = ","
    )
    private String[] extensions;

    @Option(
            names = {"-d", "--depth"},
            description = "Maximum directory depth to traverse (default: unlimited)"
    )
    private Integer maxDepth;

    @Option(
            names = {"--home"},
            description = "Home directory for configuration (default: userHome/.code-semi-graph)",
            paramLabel = "DIR"
    )
    private Path homeDirectory = SemiCommandSupport.defaultHome();

    @Override
    public Integer call() {
        try {
            Path resolvedHome = SemiCommandSupport.resolveHomeDirectory(homeDirectory);
            AppConfig appConfig = SemiCommandSupport.loadConfig(resolvedHome);
            SemiCommandSupport.EmbeddingParameters embeddingParameters =
                    SemiCommandSupport.resolveEmbeddingParameters(appConfig, embeddingOptions);

            Path resolvedSource = (sourcePath != null ? sourcePath : Paths.get(".")).toAbsolutePath().normalize();
            Path indexDirectory = SemiCommandSupport.resolveIndexDirectory(appConfig, outputDir);
            Path vectorDbPath = SemiCommandSupport.resolveVectorDatabasePath(indexDirectory);
            Path graphDbPath = indexDirectory.resolve("arcadedb-graph");

            logger.info("Building embedding index with tree-sitter parsing...");
            logger.info("Home directory: {}", resolvedHome);
            logger.info("Configuration file: {}", ConfigLoader.resolveConfigPath(resolvedHome));
            logger.info("Source path: {}", resolvedSource);
            logger.info("Index directory: {}", indexDirectory);
            logger.info("Vector database path: {}", vectorDbPath);
            logger.info("Graph database path: {}", graphDbPath);
            logger.info("Model: {}", embeddingParameters.model());
            logger.info("Model name: {}", embeddingParameters.modelName());
            logger.info("Embedding dimension: {}", embeddingParameters.embeddingDimension());
            if (embeddingParameters.modelPath() != null) {
                logger.info("Model path: {}", embeddingParameters.modelPath());
            }
            logger.info("API key provided: {}", embeddingParameters.apiKey() != null);
            logger.info("Batch size: {}", embeddingParameters.batchSize());
            if (maxDepth != null) {
                logger.info("Max depth: {}", maxDepth);
            }
            if (extensions != null && extensions.length > 0) {
                logger.info("Extensions: {}", String.join(", ", extensions));
            }

            Files.createDirectories(vectorDbPath);
            Files.createDirectories(graphDbPath);

            try (VectorDatabase vectorDb = new ArcadeDBVectorDatabase(vectorDbPath.toString());
                 GraphDatabase graphDb = new ArcadeDBGraphDatabase(graphDbPath.toString())) {
                
                vectorDb.initialize();
                graphDb.initialize();

                List<Path> filesToIndex = collectFiles(resolvedSource);
                logger.info("Found {} files to index", filesToIndex.size());

                if (filesToIndex.isEmpty()) {
                    logger.info("No files found to index. Exiting.");
                    return 0;
                }

                try (EmbeddingModel embeddingModel = SemiCommandSupport.createEmbeddingModel(embeddingParameters)) {
                    logger.info("Embedding model initialized: {} (dimension: {})",
                            embeddingModel.getModelName(),
                            embeddingModel.getEmbeddingDimension());

                    CodeChunker chunker = new CodeChunker();
                    int totalChunks = 0;
                    int totalCallChains = 0;
                    // Cache parsers by language to avoid recreating them
                    Map<Language, TreeSitterParser> parserCache = new HashMap<>();
                    final boolean[] treeSitterAvailable = {true}; // Use array to allow mutation in lambda

                    for (Path file : filesToIndex) {
                        try {
                            // Detect language from file extension
                            Optional<Language> detectedLanguage = LanguageDetector.detectLanguage(file);
                            
                            if (detectedLanguage.isEmpty()) {
                                // Not a supported language for tree-sitter, use simple embedding
                                String content = Files.readString(file);
                                float[] embedding = embeddingModel.generateEmbedding(content);
                                vectorDb.storeEmbedding(file.toString(), content, embedding);
                                logger.debug("Indexed (unsupported language): {}", file);
                                continue;
                            }

                            // Check if tree-sitter is available (only once)
                            if (!treeSitterAvailable[0]) {
                                // Fall back to simple embedding
                                String content = Files.readString(file);
                                float[] embedding = embeddingModel.generateEmbedding(content);
                                vectorDb.storeEmbedding(file.toString(), content, embedding);
                                logger.debug("Indexed (tree-sitter unavailable): {}", file);
                                continue;
                            }

                            Language language = detectedLanguage.get();
                            
                            // Get or create parser for this language
                            TreeSitterParser treeSitterParser = parserCache.get(language);
                            if (treeSitterParser == null && treeSitterAvailable[0]) {
                                try {
                                    treeSitterParser = new TreeSitterParser(language);
                                    parserCache.put(language, treeSitterParser);
                                    logger.debug("TreeSitter parser initialized for {} parsing", language.name());
                                } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
                                    logger.warn("TreeSitter native libraries not available. Falling back to simple indexing: {}", e.getMessage());
                                    treeSitterAvailable[0] = false;
                                    treeSitterParser = null;
                                }
                            }
                            
                            if (treeSitterParser == null) {
                                // Tree-sitter initialization failed, fall back to simple embedding
                                String content = Files.readString(file);
                                float[] embedding = embeddingModel.generateEmbedding(content);
                                vectorDb.storeEmbedding(file.toString(), content, embedding);
                                logger.debug("Indexed (fallback): {}", file);
                                continue;
                            }

                            CodeMetadata metadata = treeSitterParser.parseFile(file);
                            logger.debug("Parsed {} ({}): package={}, class={}, methods={}",
                                    file, language.name(), metadata.packageName(), metadata.className(), metadata.methods().size());

                            // Add class node to graph
                            String classNodeId = metadata.filePath() + ":" + metadata.className();
                            graphDb.addNode(classNodeId, "class", metadata.className(), metadata.filePath());

                            // Process call chains and add to graph
                            for (CodeMetadata.MethodInfo method : metadata.methods()) {
                                String methodNodeId = classNodeId + ":" + method.name();
                                graphDb.addNode(methodNodeId, "method", method.name(), metadata.filePath());
                                
                                // Link method to class
                                graphDb.addEdge(classNodeId, methodNodeId, "contains");
                                
                                // Add call relationships
                                for (String callee : method.callees()) {
                                    String calleeNodeId = classNodeId + ":" + callee;
                                    // Create callee node if it doesn't exist (might be external)
                                    graphDb.addNode(calleeNodeId, "method", callee, metadata.filePath());
                                    graphDb.addEdge(methodNodeId, calleeNodeId, "calls");
                                    totalCallChains++;
                                }
                            }

                            // Chunk the code
                            List<CodeChunker.CodeChunk> chunks = chunker.chunkCode(metadata);
                            logger.debug("Created {} chunks from {}", chunks.size(), file);

                            // Store each chunk with embedding
                            for (CodeChunker.CodeChunk chunk : chunks) {
                                String chunkContent = chunk.getFullContext();
                                float[] embedding = embeddingModel.generateEmbedding(chunkContent);
                                
                                vectorDb.storeEmbeddingWithMetadata(
                                        chunk.filePath(),
                                        chunk.packageName(),
                                        chunk.className(),
                                        chunk.methodName(),
                                        chunkContent,
                                        embedding
                                );
                                totalChunks++;
                            }

                            logger.debug("Indexed: {} ({} chunks)", file, chunks.size());
                        } catch (Exception e) {
                            logger.warn("Failed to index {}: {}", file, e.getMessage());
                            logger.debug("Stack trace:", e);
                        }
                    }
                    
                    // Close all tree-sitter parsers
                    for (TreeSitterParser parser : parserCache.values()) {
                        if (parser != null) {
                            try {
                                parser.close();
                            } catch (Exception e) {
                                logger.debug("Error closing tree-sitter parser: {}", e.getMessage());
                            }
                        }
                    }

                    int embeddingCount = vectorDb.getEmbeddingCount();
                    int nodeCount = graphDb.getNodeCount();
                    int edgeCount = graphDb.getEdgeCount();
                    
                    logger.info("✓ Embedding index built successfully!");
                    logger.info("Index location: {}", indexDirectory);
                    logger.info("Total chunks stored: {}", totalChunks);
                    logger.info("Total embeddings in vector DB: {}", embeddingCount);
                    logger.info("Total nodes in graph DB: {}", nodeCount);
                    logger.info("Total edges (call chains) in graph DB: {}", edgeCount);
                    logger.info("Total call relationships extracted: {}", totalCallChains);
                }
            }
        } catch (Exception e) {
            logger.error("Error building index: {}", e.getMessage());
            logger.debug("Stack trace:", e);
            return 1;
        }

        return 0;
    }

    private List<Path> collectFiles(Path sourcePath) throws IOException {
        List<Path> files = new ArrayList<>();

        if (!Files.exists(sourcePath)) {
            logger.error("Error: Path does not exist: {}", sourcePath);
            return files;
        }

        if (Files.isRegularFile(sourcePath)) {
            if (shouldIncludeFile(sourcePath)) {
                files.add(sourcePath);
            }
            return files;
        }

        int depth = (maxDepth != null) ? maxDepth : Integer.MAX_VALUE;

        try (Stream<Path> pathStream = Files.walk(sourcePath, depth)) {
            pathStream
                    .filter(p -> {
                        try {
                            return Files.isRegularFile(p);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .filter(this::shouldIncludeFile)
                    .forEach(files::add);
        } catch (Exception e) {
            logger.warn("Warning: Error traversing directory: {}", e.getMessage());
        }

        return files;
    }

    private boolean shouldIncludeFile(Path file) {
        String fileName = file.getFileName().toString();

        if (fileName.startsWith(".") ||
                file.toString().contains("/.git/") ||
                file.toString().contains("/node_modules/") ||
                file.toString().contains("/build/") ||
                file.toString().contains("/target/") ||
                file.toString().contains("/.gradle/")) {
            return false;
        }

        if (extensions != null && extensions.length > 0) {
            for (String ext : extensions) {
                if (fileName.endsWith("." + ext)) {
                    return true;
                }
            }
            return false;
        }

        return fileName.endsWith(".java") ||
                fileName.endsWith(".kt") ||
                fileName.endsWith(".js") ||
                fileName.endsWith(".ts") ||
                fileName.endsWith(".py") ||
                fileName.endsWith(".go") ||
                fileName.endsWith(".rs") ||
                fileName.endsWith(".cpp") ||
                fileName.endsWith(".c") ||
                fileName.endsWith(".h") ||
                fileName.endsWith(".cs") ||
                fileName.endsWith(".rb") ||
                fileName.endsWith(".php") ||
                fileName.endsWith(".swift") ||
                fileName.endsWith(".scala");
    }
}
