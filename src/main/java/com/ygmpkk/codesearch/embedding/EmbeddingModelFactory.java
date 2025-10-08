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
     * @param model Type of model ("mock", "http", "djl")
     * @param modelName Name of the model (e.g., "Qwen/Qwen3-Embedding-0.6B", "http://...", "mock")
     * @param embeddingDimension Optional embedding dimension (default depends on model)
     * @param modelPath Optional path to local model files (for DJL models)
     * @param apiKey Optional API key for HTTP models
     * @return Initialized embedding model
     * @throws Exception if model creation fails
     */
    public static EmbeddingModel createModel(String model, String modelName, Integer embeddingDimension, String modelPath, String apiKey) throws Exception {
        logger.info("Creating embedding model: {}", modelName);
        
        EmbeddingModel embeddingModel;
        
        // Determine model type based on model name
        if (model == null || model.isEmpty() || model.equalsIgnoreCase("mock")) {
            // Mock model for testing
            logger.info("Using mock embedding model");
            embeddingModel = new MockEmbeddingModel(model != null ? model : "mock");
            
        } else if (model.startsWith("http://") || model.startsWith("https://")) {
            // HTTP-based model
            logger.info("Using HTTP embedding model with URL: {}", model);
            embeddingModel = new HttpEmbeddingModel(modelName, model, apiKey);
            
        } else if (modelPath != null && !modelPath.isEmpty()) {
            // DJL-based local model
            logger.info("Using DJL embedding model from path: {}", modelPath);
            embeddingModel = new DjlEmbeddingModel(model, modelPath);
            
        } else {
            // Default to mock for unsupported configurations
            logger.warn("Model type not recognized for '{}', falling back to mock model", model);
            embeddingModel = new MockEmbeddingModel(model);
        }
        
        // Initialize the model
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
}
