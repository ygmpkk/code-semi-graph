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
     * @param modelName Name of the model (e.g., "Qwen/Qwen3-Embedding-0.6B", "http://...", "mock")
     * @param modelPath Optional path to local model files (for DJL models)
     * @param apiKey Optional API key for HTTP models
     * @return Initialized embedding model
     * @throws Exception if model creation fails
     */
    public static EmbeddingModel createModel(String modelName, String modelPath, String apiKey) throws Exception {
        logger.info("Creating embedding model: {}", modelName);
        
        EmbeddingModel model;
        
        // Determine model type based on model name
        if (modelName == null || modelName.isEmpty() || modelName.equalsIgnoreCase("mock")) {
            // Mock model for testing
            logger.info("Using mock embedding model");
            model = new MockEmbeddingModel(modelName != null ? modelName : "mock");
            
        } else if (modelName.startsWith("http://") || modelName.startsWith("https://")) {
            // HTTP-based model
            logger.info("Using HTTP embedding model with URL: {}", modelName);
            model = new HttpEmbeddingModel("HTTP-Embedding", modelName, apiKey);
            
        } else if (modelPath != null && !modelPath.isEmpty()) {
            // DJL-based local model
            logger.info("Using DJL embedding model from path: {}", modelPath);
            model = new DjlEmbeddingModel(modelName, modelPath);
            
        } else {
            // Default to mock for unsupported configurations
            logger.warn("Model type not recognized for '{}', falling back to mock model", modelName);
            model = new MockEmbeddingModel(modelName);
        }
        
        // Initialize the model
        model.initialize();
        
        return model;
    }
    
    /**
     * Create an embedding model with just the model name
     * Uses mock model by default if model path is not provided
     */
    public static EmbeddingModel createModel(String modelName) throws Exception {
        return createModel(modelName, null, null);
    }
    
    /**
     * Create a mock embedding model (default)
     */
    public static EmbeddingModel createMockModel() throws Exception {
        return createModel("mock", null, null);
    }
}
