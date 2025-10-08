package com.ygmpkk.codesearch;

import com.ygmpkk.codesearch.analyzer.ClassInfo;
import com.ygmpkk.codesearch.analyzer.CodeChunk;
import com.ygmpkk.codesearch.analyzer.FileAnalysisResult;
import com.ygmpkk.codesearch.analyzer.MethodCall;
import com.ygmpkk.codesearch.analyzer.MethodInfo;
import com.ygmpkk.codesearch.analyzer.MethodParameter;
import com.ygmpkk.codesearch.analyzer.TokenAwareChunker;
import com.ygmpkk.codesearch.analyzer.TreeSitterCodeAnalyzer;
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

            TreeSitterCodeAnalyzer analyzer = new TreeSitterCodeAnalyzer();
            TokenAwareChunker chunker = new TokenAwareChunker();

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

                    int batchSize = Math.max(1, embeddingParameters.batchSize());
                    int processedFiles = 0;
                    int totalChunks = 0;

                    for (Path file : filesToIndex) {
                        processedFiles++;
                        try {
                            String content = Files.readString(file);
                            Optional<FileAnalysisResult> analysis = analyzer.analyze(file, content);
                            if (analysis.isEmpty()) {
                                logger.debug("Skipping {} (unsupported language or parse failure)", file);
                                continue;
                            }

                            int chunksForFile = processFile(analysis.get(), chunker, embeddingModel, vectorDb, graphDb);
                            totalChunks += chunksForFile;
                            logger.debug("Processed {} chunk(s) for {}", chunksForFile, file);
                        } catch (Exception e) {
                            logger.warn("Failed to process {}: {}", file, e.getMessage());
                            logger.debug("Stack trace:", e);
                        }

                        if (processedFiles % batchSize == 0) {
                            logger.info("Processed {} / {} files ({} chunks so far)",
                                    processedFiles, filesToIndex.size(), totalChunks);
                        }
                    }

                    logger.info("✓ Embedding index built successfully!");
                    logger.info("Index location: {}", indexDirectory);
                    logger.info("Total embeddings stored: {}", vectorDb.getEmbeddingCount());
                    logger.info("Graph nodes: {}", graphDb.getNodeCount());
                    logger.info("Graph edges: {}", graphDb.getEdgeCount());
                    logger.info("Files analyzed: {}", processedFiles);
                    logger.info("Generated chunks: {}", totalChunks);
                }
            }
        } catch (Exception e) {
            logger.error("Error building index: {}", e.getMessage());
            logger.debug("Stack trace:", e);
            return 1;
        }

        return 0;
    }

    private int processFile(FileAnalysisResult analysis,
                            TokenAwareChunker chunker,
                            EmbeddingModel embeddingModel,
                            VectorDatabase vectorDb,
                            GraphDatabase graphDb) throws Exception {
        Path filePath = analysis.getFilePath();
        String packageName = normalizePackageName(analysis.getPackageName());
        String packageNodeId = packageNodeId(packageName);

        graphDb.addNode(packageNodeId, "package", packageName, filePath.toString());

        int chunkCount = 0;

        for (ClassInfo classInfo : analysis.getClasses()) {
            String classNodeId = classNodeId(packageName, classInfo.getName());
            graphDb.addNode(classNodeId, "class", classInfo.getName(), filePath.toString());
            graphDb.addEdge(packageNodeId, classNodeId, "contains");

            Map<MethodInfo, String> methodNodeIds = new HashMap<>();
            Map<String, String> methodNameIndex = new HashMap<>();

            for (MethodInfo methodInfo : classInfo.getMethods()) {
                String methodNodeId = methodNodeId(packageName, classInfo.getName(), methodInfo);
                methodNodeIds.put(methodInfo, methodNodeId);
                methodNameIndex.putIfAbsent(methodInfo.getName(), methodNodeId);
                graphDb.addNode(methodNodeId, "method", methodInfo.getName(), filePath.toString());
                graphDb.addEdge(classNodeId, methodNodeId, "contains");
            }

            for (Map.Entry<MethodInfo, String> entry : methodNodeIds.entrySet()) {
                MethodInfo methodInfo = entry.getKey();
                String methodNodeId = entry.getValue();

                List<CodeChunk> chunks = chunker.chunkMethod(filePath, packageName, classInfo, methodInfo);
                chunkCount += chunks.size();
                for (CodeChunk chunk : chunks) {
                    String text = chunk.toEmbeddingText();
                    try {
                        float[] embedding = embeddingModel.generateEmbedding(text);
                        vectorDb.storeEmbedding(chunk.chunkId(), text, embedding);
                    } catch (Exception e) {
                        logger.warn("Failed to store chunk {}: {}", chunk.chunkId(), e.getMessage());
                        logger.debug("Stack trace:", e);
                    }
                }

                linkMethodCalls(graphDb, methodNodeId, packageName, classInfo, methodInfo, methodNameIndex);
            }
        }

        return chunkCount;
    }

    private void linkMethodCalls(GraphDatabase graphDb,
                                 String sourceNodeId,
                                 String packageName,
                                 ClassInfo classInfo,
                                 MethodInfo methodInfo,
                                 Map<String, String> methodNameIndex) throws Exception {
        for (MethodCall call : methodInfo.getMethodCalls()) {
            String targetId = resolveTargetMethod(packageName, classInfo, call, methodNameIndex);
            if (targetId == null) {
                String displayName = call.displayName();
                targetId = externalMethodNodeId(displayName);
                graphDb.addNode(targetId, "method_external", displayName, "(external)");
            }
            graphDb.addEdge(sourceNodeId, targetId, "calls");
        }
    }

    private String resolveTargetMethod(String packageName,
                                       ClassInfo classInfo,
                                       MethodCall call,
                                       Map<String, String> methodNameIndex) {
        String qualifier = call.qualifier();
        String methodName = call.name();

        if (qualifier == null || qualifier.isBlank()
                || "this".equals(qualifier)
                || "super".equals(qualifier)
                || qualifier.equals(classInfo.getName())) {
            return methodNameIndex.get(methodName);
        }

        if (qualifier.contains(".")) {
            String simpleQualifier = qualifier.substring(qualifier.lastIndexOf('.') + 1);
            if (simpleQualifier.equals(classInfo.getName())) {
                return methodNameIndex.get(methodName);
            }
        }

        return null;
    }

    private static String packageNodeId(String packageName) {
        return "package::" + sanitizeForId(packageName);
    }

    private static String classNodeId(String packageName, String className) {
        return "class::" + sanitizeForId(packageName) + "::" + sanitizeForId(className);
    }

    private static String methodNodeId(String packageName, String className, MethodInfo methodInfo) {
        String params = methodInfo.getParameters().stream()
                .map(param -> sanitizeForId(param.type()))
                .collect(java.util.stream.Collectors.joining(","));
        return "method::" + sanitizeForId(packageName)
                + "::" + sanitizeForId(className)
                + "::" + sanitizeForId(methodInfo.getName())
                + "(" + params + ")";
    }

    private static String externalMethodNodeId(String displayName) {
        return "external::" + sanitizeForId(displayName);
    }

    private static String sanitizeForId(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }
        return value.replaceAll("[^A-Za-z0-9_.#/]+", "_");
    }

    private static String normalizePackageName(String packageName) {
        return (packageName == null || packageName.isBlank()) ? "(default)" : packageName;
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
