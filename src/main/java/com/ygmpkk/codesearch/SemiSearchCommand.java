package com.ygmpkk.codesearch;

import com.ygmpkk.codesearch.config.AppConfig;
import com.ygmpkk.codesearch.config.ConfigLoader;
import com.ygmpkk.codesearch.db.ArcadeDBVectorDatabase;
import com.ygmpkk.codesearch.db.VectorDatabase;
import com.ygmpkk.codesearch.embedding.EmbeddingModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Command for performing semi code search with subcommands
 */
@Command(
    name = "semi",
    description = "Perform semi code search operations",
    mixinStandardHelpOptions = true,
    subcommands = {
        SemiBuildCommand.class
    }
)
public class SemiSearchCommand implements Callable<Integer> {
    private static final Logger logger = LogManager.getLogger(SemiSearchCommand.class);

    @Mixin
    LoggingMixin loggingMixin;

    @Mixin
    EmbeddingOptions embeddingOptions;

    @Parameters(
        index = "0",
        description = "Search query",
        arity = "0..1"
    )
    private String query;

    @Option(
        names = {"-i", "--index-dir"},
        description = "Directory containing the embedding index",
        paramLabel = "DIR"
    )
    private Path indexDirectory;

    @Option(
        names = {"-l", "--limit"},
        description = "Maximum number of results to return",
        paramLabel = "N"
    )
    private Integer resultLimit;

    @Option(
        names = {"--home"},
        description = "Home directory for configuration (default: userHome/.code-semi-graph)",
        paramLabel = "DIR"
    )
    private Path homeDirectory = SemiCommandSupport.defaultHome();

    @Override
    public Integer call() {
        if (query == null || query.isEmpty()) {
            logger.info("Use 'semi --help' to see available commands and options");
            logger.info("Available subcommands:");
            logger.info("  build - Build embedding index for code search");
            logger.info("");
            logger.info("Or provide a search query to perform a search:");
            logger.info("  semi \"your query\" [options]");
            return 0;
        }

        try {
            Path resolvedHome = SemiCommandSupport.resolveHomeDirectory(homeDirectory);
            AppConfig appConfig = SemiCommandSupport.loadConfig(resolvedHome);
            SemiCommandSupport.EmbeddingParameters embeddingParameters =
                    SemiCommandSupport.resolveEmbeddingParameters(appConfig, embeddingOptions);

            Path resolvedIndexDir = determineIndexDirectory(appConfig);
            Path vectorDbPath = SemiCommandSupport.resolveVectorDatabasePath(resolvedIndexDir);

            if (Files.notExists(vectorDbPath)) {
                logger.error("Embedding index not found at {}", vectorDbPath);
                System.err.printf("Embedding index not found at %s%n", vectorDbPath);
                return 1;
            }

            int limit = (resultLimit != null && resultLimit > 0)
                    ? resultLimit
                    : appConfig.getSearch().getTopK();

            logger.info("Performing semi code search...");
            logger.info("Home directory: {}", resolvedHome);
            logger.info("Configuration file: {}", ConfigLoader.resolveConfigPath(resolvedHome));
            logger.info("Index directory: {}", resolvedIndexDir);
            logger.info("Vector database path: {}", vectorDbPath);
            logger.info("Model: {}", embeddingParameters.model());
            logger.info("Model name: {}", embeddingParameters.modelName());
            logger.info("Embedding dimension: {}", embeddingParameters.embeddingDimension());
            if (embeddingParameters.modelPath() != null) {
                logger.info("Model path: {}", embeddingParameters.modelPath());
            }
            logger.info("Result limit: {}", limit);

            try (VectorDatabase vectorDb = new ArcadeDBVectorDatabase(vectorDbPath.toString());
                 EmbeddingModel embeddingModel = SemiCommandSupport.createEmbeddingModel(embeddingParameters)) {

                float[] queryEmbedding = embeddingModel.generateEmbedding(query);
                List<VectorDatabase.SearchResult> results = vectorDb.searchSimilar(queryEmbedding, limit);

                if (results.isEmpty()) {
                    logger.info("No matching code found for query '{}'.", query);
                    System.out.println("No matching code found.");
                    return 0;
                }

                logger.info("Found {} matching results", results.size());
                System.out.println();
                System.out.printf("Top %d results for '%s':%n", results.size(), query);

                int rank = 1;
                for (VectorDatabase.SearchResult result : results) {
                    System.out.printf(
                            "%d. %s (similarity: %.4f)%n",
                            rank++,
                            result.getFilePath(),
                            result.getSimilarity()
                    );
                }
            }
        } catch (Exception e) {
            logger.error("Error performing search: {}", e.getMessage());
            logger.debug("Stack trace:", e);
            System.err.printf("Search failed: %s%n", e.getMessage());
            return 1;
        }

        return 0;
    }

    private Path determineIndexDirectory(AppConfig appConfig) {
        Path directory = indexDirectory != null ? indexDirectory : appConfig.getIndex().getDirectory();
        return (directory != null ? directory : Paths.get(".")).toAbsolutePath().normalize();
    }
}
