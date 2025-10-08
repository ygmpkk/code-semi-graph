package com.ygmpkk.codesearch.embedding;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Factory for creating embedding model instances based on configuration
 */
public class EmbeddingModelFactory {
    private static final Logger logger = LogManager.getLogger(EmbeddingModelFactory.class);
    
    /**
     * Create an embedding model based on the model name and configuration
     *
     * @param model Identifier describing the model to use (mock, HTTP URL, or DJL model id)
     * @param modelName Display name for the model (optional)
     * @param embeddingDimension Optional embedding dimension (default depends on model)
     * @param modelPath Optional path to local model files (for DJL models)
     * @param apiKey Optional API key for HTTP models
     * @return Initialized embedding model
     * @throws Exception if model creation fails
     */
    public static EmbeddingModel createModel(String model, String modelName, Integer embeddingDimension, String modelPath, String apiKey) throws Exception {
        String trimmedModel = normalize(model);
        String resolvedName = resolveModelName(trimmedModel, modelName);
        String effectiveModelId = trimmedModel != null ? trimmedModel : resolvedName;
        String normalizedPath = modelPath != null ? modelPath.trim() : null;

        logger.info("Creating embedding model: {}", resolvedName);

        EmbeddingModel embeddingModel;

        if (isMock(effectiveModelId)) {
            logger.info("Using mock embedding model");
            embeddingModel = new MockEmbeddingModel(resolvedName);
        } else if (isHttp(effectiveModelId)) {
            int dimension = embeddingDimension != null ? embeddingDimension : 1024;
            logger.info("Using HTTP embedding model with URL: {} and dimension {}", effectiveModelId, dimension);
            embeddingModel = new HttpEmbeddingModel(resolvedName, effectiveModelId, apiKey, dimension);
        } else if (normalizedPath != null && !normalizedPath.isBlank()) {
            logger.info("Using DJL embedding model from path: {}", normalizedPath);
            embeddingModel = embeddingDimension != null
                    ? new DjlEmbeddingModel(effectiveModelId, normalizedPath, embeddingDimension)
                    : new DjlEmbeddingModel(effectiveModelId, normalizedPath);
        } else {
            logger.warn("Model type not recognized for '{}', falling back to mock model", effectiveModelId);
            embeddingModel = new MockEmbeddingModel(resolvedName);
        }

        embeddingModel.initialize();
        return embeddingModel;
    }
    
    /**
     * Create an embedding model with just the model name
     * Uses mock model by default if model path is not provided
     */
    public static EmbeddingModel createModel(String modelName) throws Exception {
        return createModel(modelName, modelName, null, null, null);
    }
    
    /**
     * Create a mock embedding model (default)
     */
    public static EmbeddingModel createMockModel() throws Exception {
        return createModel("mock", "mock", null, null, null);
    }

    private static boolean isMock(String model) {
        return model == null || model.isEmpty() || "mock".equalsIgnoreCase(model);
    }

    private static boolean isHttp(String model) {
        return model != null && (model.startsWith("http://") || model.startsWith("https://"));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String resolveModelName(String model, String modelName) {
        if (modelName != null && !modelName.isBlank()) {
            return modelName.trim();
        }
        if (model != null && !model.isBlank()) {
            return model;
        }
        return "mock";
    }
}
