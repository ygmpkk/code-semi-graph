package com.ygmpkk.codesearch;

import com.ygmpkk.codesearch.analysis.CodeChunk;
import com.ygmpkk.codesearch.analysis.CodeFileAnalysis;
import com.ygmpkk.codesearch.analysis.GraphEdge;
import com.ygmpkk.codesearch.analysis.GraphNode;
import com.ygmpkk.codesearch.analysis.TreeSitterCodeAnalyzer;
import com.ygmpkk.codesearch.config.AppConfig;
import com.ygmpkk.codesearch.config.ConfigLoader;
import com.ygmpkk.codesearch.db.ArcadeDBGraphDatabase;
import com.ygmpkk.codesearch.db.ArcadeDBVectorDatabase;
import com.ygmpkk.codesearch.db.GraphDatabase;
import com.ygmpkk.codesearch.db.VectorDatabase;
import com.ygmpkk.codesearch.embedding.EmbeddingModel;
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
import java.util.List;
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

    private final TreeSitterCodeAnalyzer codeAnalyzer = new TreeSitterCodeAnalyzer();

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
            Path graphDbPath = SemiCommandSupport.resolveGraphDatabasePath(indexDirectory);

            logger.info("Building embedding index...");
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

                    int batchSize = embeddingParameters.batchSize();
                    logger.info("Processing code chunks in batches of {}...", batchSize);

                    List<CodeChunk> chunkBatch = new ArrayList<>(batchSize);
                    int processedChunks = 0;
                    int processedFiles = 0;

                    for (Path file : filesToIndex) {
                        processedFiles++;
                        try {
                            String content = Files.readString(file);
                            CodeFileAnalysis analysis = codeAnalyzer.analyse(file, content);

                            persistGraphMetadata(graphDb, analysis);

                            for (CodeChunk chunk : analysis.chunks()) {
                                chunkBatch.add(chunk);
                                if (chunkBatch.size() >= batchSize) {
                                    processedChunks += storeChunkBatch(chunkBatch, embeddingModel, vectorDb);
                                    chunkBatch.clear();
                                }
                            }

                            if (processedFiles % 10 == 0) {
                                logger.info("Processed {} / {} files ({} chunks)",
                                        processedFiles, filesToIndex.size(), processedChunks);
                            }
                        } catch (Exception e) {
                            logger.warn("Failed to process {}: {}", file, e.getMessage());
                            logger.debug("Stack trace:", e);
                        }
                    }

                    if (!chunkBatch.isEmpty()) {
                        processedChunks += storeChunkBatch(chunkBatch, embeddingModel, vectorDb);
                        chunkBatch.clear();
                    }

                    int embeddingCount = vectorDb.getEmbeddingCount();
                    int nodeCount = graphDb.getNodeCount();
                    int edgeCount = graphDb.getEdgeCount();

                    logger.info("✓ Embedding index built successfully!");
                    logger.info("Index location: {}", indexDirectory);
                    logger.info("Total chunks stored: {}", processedChunks);
                    logger.info("Total embeddings stored: {}", embeddingCount);
                    logger.info("Graph nodes stored: {}", nodeCount);
                    logger.info("Graph edges stored: {}", edgeCount);
                }
            }
        } catch (Exception e) {
            logger.error("Error building index: {}", e.getMessage());
            logger.debug("Stack trace:", e);
            return 1;
        }

        return 0;
    }

    private int storeChunkBatch(List<CodeChunk> chunkBatch,
                                EmbeddingModel embeddingModel,
                                VectorDatabase vectorDb) {
        int stored = 0;
        for (CodeChunk chunk : chunkBatch) {
            try {
                float[] embedding = embeddingModel.generateEmbedding(chunk.content());
                vectorDb.storeEmbedding(chunk, embedding);
                stored++;
                logger.debug("Indexed chunk: {}", chunk.chunkId());
            } catch (Exception e) {
                logger.warn("Failed to store chunk {} from {}: {}",
                        chunk.chunkId(), chunk.filePath(), e.getMessage());
                logger.debug("Stack trace:", e);
            }
        }
        return stored;
    }

    private void persistGraphMetadata(GraphDatabase graphDb, CodeFileAnalysis analysis) {
        for (GraphNode node : analysis.nodes()) {
            try {
                graphDb.addNode(node.nodeId(), node.nodeType(), node.name(), node.filePath());
            } catch (Exception e) {
                logger.warn("Failed to persist graph node {}: {}", node.nodeId(), e.getMessage());
                logger.debug("Stack trace:", e);
            }
        }

        for (GraphEdge edge : analysis.edges()) {
            try {
                graphDb.addEdge(edge.fromNodeId(), edge.toNodeId(), edge.relationshipType());
            } catch (Exception e) {
                logger.warn("Failed to persist graph edge {} -> {} [{}]: {}",
                        edge.fromNodeId(), edge.toNodeId(), edge.relationshipType(), e.getMessage());
                logger.debug("Stack trace:", e);
            }
        }
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
