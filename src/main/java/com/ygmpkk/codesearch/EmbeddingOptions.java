package com.ygmpkk.codesearch;

import picocli.CommandLine.Option;

import java.nio.file.Path;

/**
 * Shared embedding options between build and search commands.
 */
public class EmbeddingOptions {
    @Option(
            names = {"-m", "--model"},
            description = "Embedding model identifier. Supports 'mock', HTTP endpoints, or model names",
            paramLabel = "MODEL"
    )
    private String model;

    @Option(
            names = {"--model-name"},
            description = "Display name for the embedding model",
            paramLabel = "NAME"
    )
    private String modelName;

    @Option(
            names = {"--model-path"},
            description = "Path to local model files for DJL-based models",
            paramLabel = "PATH"
    )
    private Path modelPath;

    @Option(
            names = {"--embedding-dim"},
            description = "Dimension of the embeddings",
            paramLabel = "DIMENSION"
    )
    private Integer embeddingDimension;

    @Option(
            names = {"--api-key"},
            description = "API key for HTTP embedding providers",
            paramLabel = "KEY"
    )
    private String apiKey;

    @Option(
            names = {"--batch-size"},
            description = "Batch size for processing items",
            paramLabel = "SIZE"
    )
    private Integer batchSize;

    public String getModel() {
        return normalize(model);
    }

    public String getModelName() {
        return normalize(modelName);
    }

    public Path getModelPath() {
        return modelPath;
    }

    public Integer getEmbeddingDimension() {
        return embeddingDimension;
    }

    public String getApiKey() {
        return normalize(apiKey);
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
