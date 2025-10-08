package com.ygmpkk.codesearch;

import com.ygmpkk.codesearch.config.AppConfig;
import com.ygmpkk.codesearch.config.ConfigLoader;
import com.ygmpkk.codesearch.embedding.EmbeddingModel;
import com.ygmpkk.codesearch.embedding.EmbeddingModelFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Helper utilities shared between semi build and search commands.
 */
public final class SemiCommandSupport {
    private static final Path DEFAULT_HOME = Paths.get(System.getProperty("user.home"), ".code-semi-graph");
    private static final String VECTOR_DB_DIRECTORY = "arcadedb-vector";

    private SemiCommandSupport() {
    }

    public static Path defaultHome() {
        return DEFAULT_HOME;
    }

    public static Path resolveHomeDirectory(Path requestedHome) throws Exception {
        Path home = (requestedHome != null ? requestedHome : DEFAULT_HOME).toAbsolutePath().normalize();
        Files.createDirectories(home);
        return home;
    }

    public static AppConfig loadConfig(Path homeDirectory) {
        return ConfigLoader.load(homeDirectory);
    }

    public static EmbeddingParameters resolveEmbeddingParameters(AppConfig appConfig, EmbeddingOptions options) {
        AppConfig.EmbeddingConfig config = appConfig.getEmbedding();

        String model = firstNonBlank(options.getModel(), config.getModel());
        String modelName = firstNonBlank(options.getModelName(), config.getModelName());
        if (modelName == null || modelName.isBlank()) {
            modelName = model;
        }

        Integer embeddingDimension = options.getEmbeddingDimension() != null
                ? options.getEmbeddingDimension()
                : config.getEmbeddingDimension();

        Path modelPath = options.getModelPath() != null
                ? options.getModelPath().toAbsolutePath().normalize()
                : config.getModelPath();

        String apiKey = firstNonBlank(options.getApiKey(), config.getApiKey());
        int batchSize = options.getBatchSize() != null && options.getBatchSize() > 0
                ? options.getBatchSize()
                : config.getBatchSize();

        return new EmbeddingParameters(model, modelName, embeddingDimension, modelPath, apiKey, batchSize);
    }

    public static EmbeddingModel createEmbeddingModel(EmbeddingParameters parameters) throws Exception {
        return EmbeddingModelFactory.createModel(
                parameters.model(),
                parameters.modelName(),
                parameters.embeddingDimension(),
                parameters.modelPath() != null ? parameters.modelPath().toString() : null,
                parameters.apiKey()
        );
    }

    public static Path resolveIndexDirectory(AppConfig appConfig, Path override) throws Exception {
        Path directory = override != null ? override : appConfig.getIndex().getDirectory();
        Path normalized = directory.toAbsolutePath().normalize();
        Files.createDirectories(normalized);
        return normalized;
    }

    public static Path resolveVectorDatabasePath(Path indexDirectory) {
        return indexDirectory.resolve(VECTOR_DB_DIRECTORY);
    }

    private static String firstNonBlank(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback;
    }

    /**
     * Aggregated embedding parameters resolved from configuration and CLI options.
     */
    public record EmbeddingParameters(
            String model,
            String modelName,
            Integer embeddingDimension,
            Path modelPath,
            String apiKey,
            int batchSize
    ) {
    }
}
