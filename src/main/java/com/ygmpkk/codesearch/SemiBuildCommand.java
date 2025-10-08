package com.ygmpkk.codesearch;

import com.ygmpkk.codesearch.db.ArcadeDBVectorDatabase;
import com.ygmpkk.codesearch.db.VectorDatabase;
import com.ygmpkk.codesearch.embedding.EmbeddingModel;
import com.ygmpkk.codesearch.embedding.EmbeddingModelFactory;
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

    @Mixin
    LoggingMixin loggingMixin;

    @Option(
            names = {"-p", "--path"},
            description = "Path to build index from (default: current directory)"
    )
    private String path = ".";

    @Option(
            names = {"-o", "--output"},
            description = "Output directory for the index (default: ./.code-index)"
    )
    private String outputDir = "./.code-index";

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
            names = {"-m", "--model"},
            description = "Embedding model to use (default: mock). Can be 'mock', a model name like 'Qwen/Qwen3-Embedding-0.6B', or an HTTP URL"
    )
    private String model = "mock";
    
    @Option(
            names = {"--model-path"},
            description = "Path to local model files for DJL models"
    )
    private String modelPath;
    
    @Option(
            names = {"--api-key"},
            description = "API key for HTTP-based embedding models"
    )
    private String apiKey;

    @Option(
            names = {"--batch-size"},
            description = "Batch size for processing files (default: 32)"
    )
    private int batchSize = 32;

    @Override
    public Integer call() {
        logger.info("Building embedding index...");
        logger.info("Source path: {}", path);
        logger.info("Output directory: {}", outputDir);
        logger.info("Model: {}", model);
        logger.info("Batch size: {}", batchSize);

        if (maxDepth != null) {
            logger.info("Max depth: {}", maxDepth);
        }

        if (extensions != null && extensions.length > 0) {
            logger.info("Extensions: {}", String.join(", ", extensions));
        }

        try {
            // Create output directory if it doesn't exist
            Path outputPath = Paths.get(outputDir);
            Files.createDirectories(outputPath);

            // Initialize vector database
            String dbPath = outputPath.resolve("arcadedb-vector").toString();
            logger.info("");
            logger.info("Initializing vector database: {}", dbPath);
            
            try (VectorDatabase vectorDb = new ArcadeDBVectorDatabase(dbPath)) {
                vectorDb.initialize();
                
                // Collect files to index
                List<Path> filesToIndex = collectFiles();
                logger.info("");
                logger.info("Found {} files to index", filesToIndex.size());

                if (filesToIndex.isEmpty()) {
                    logger.info("No files found to index. Exiting.");
                    return 0;
                }

                // Initialize embedding model using factory
                logger.info("");
                logger.info("Initializing embedding model: {}", model);
                if (modelPath != null) {
                    logger.info("Model path: {}", modelPath);
                }
                
                try (EmbeddingModel embeddingModel = EmbeddingModelFactory.createModel(model, modelPath, apiKey)) {
                    logger.info("Embedding model initialized: {} (dimension: {})", 
                            embeddingModel.getModelName(), 
                            embeddingModel.getEmbeddingDimension());

                    // Process files in batches
                    logger.info("");
                    logger.info("Processing files in batches of {}...", batchSize);
                    int totalBatches = (int) Math.ceil((double) filesToIndex.size() / batchSize);

                    for (int i = 0; i < filesToIndex.size(); i += batchSize) {
                        int batchNum = (i / batchSize) + 1;
                        int endIdx = Math.min(i + batchSize, filesToIndex.size());
                        List<Path> batch = filesToIndex.subList(i, endIdx);

                        logger.info("Processing batch {}/{} ({} files)", batchNum, totalBatches, batch.size());

                        for (Path file : batch) {
                            try {
                                // Read file content
                                String content = Files.readString(file);
                                
                                // Generate embedding using the model
                                float[] embedding = embeddingModel.generateEmbedding(content);
                                
                                // Store in database
                                vectorDb.storeEmbedding(file.toString(), content, embedding);
                                
                                logger.debug("  - Indexed: {}", file);
                            } catch (Exception e) {
                                logger.warn("  - Failed to index {}: {}", file, e.getMessage());
                            }
                        }
                    }

                    int embeddingCount = vectorDb.getEmbeddingCount();
                    logger.info("");
                    logger.info("✓ Embedding index built successfully!");
                    logger.info("Index location: {}", outputPath.toAbsolutePath());
                    logger.info("Total embeddings stored: {}", embeddingCount);
                }
            }

        } catch (Exception e) {
            logger.error("Error building index: {}", e.getMessage());
            logger.debug("Stack trace:", e);
            return 1;
        }

        return 0;
    }

    private List<Path> collectFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        Path sourcePath = Paths.get(path);

        if (!Files.exists(sourcePath)) {
            logger.error("Error: Path does not exist: {}", path);
            return files;
        }

        if (Files.isRegularFile(sourcePath)) {
            if (shouldIncludeFile(sourcePath)) {
                files.add(sourcePath);
            }
            return files;
        }

        // Traverse directory with error handling
        int depth = (maxDepth != null) ? maxDepth : Integer.MAX_VALUE;

        try (Stream<Path> pathStream = Files.walk(sourcePath, depth)) {
            pathStream
                    .filter(p -> {
                        try {
                            return Files.isRegularFile(p);
                        } catch (Exception e) {
                            // Skip files that can't be accessed
                            return false;
                        }
                    })
                    .filter(this::shouldIncludeFile)
                    .forEach(files::add);
        } catch (Exception e) {
            // If walk fails completely, try to handle gracefully
            logger.warn("Warning: Error traversing directory: {}", e.getMessage());
        }

        return files;
    }

    private boolean shouldIncludeFile(Path file) {
        String fileName = file.getFileName().toString();

        // Skip hidden files and common non-source directories
        if (fileName.startsWith(".") ||
                file.toString().contains("/.git/") ||
                file.toString().contains("/node_modules/") ||
                file.toString().contains("/build/") ||
                file.toString().contains("/target/") ||
                file.toString().contains("/.gradle/")) {
            return false;
        }

        // If extensions are specified, check if file matches
        if (extensions != null && extensions.length > 0) {
            for (String ext : extensions) {
                if (fileName.endsWith("." + ext)) {
                    return true;
                }
            }
            return false;
        }

        // Default: include common code file extensions
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
