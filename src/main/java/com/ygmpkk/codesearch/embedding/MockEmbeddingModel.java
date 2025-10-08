package com.ygmpkk.codesearch.embedding;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;

/**
 * Mock embedding model implementation for testing and demonstration
 * Generates deterministic embeddings based on content hash
 */
public class MockEmbeddingModel extends EmbeddingModel {
    private static final Logger logger = LogManager.getLogger(MockEmbeddingModel.class);
    
    /**
     * Constructor for MockEmbeddingModel
     * @param modelName Name of the model (for logging purposes)
     */
    public MockEmbeddingModel(String modelName) {
        super(modelName, 768); // Standard embedding dimension
    }
    
    /**
     * Default constructor with standard model name
     */
    public MockEmbeddingModel() {
        this("Mock-Embedding-768");
    }
    
    @Override
    public void initialize() throws Exception {
        logger.info("Initialized mock embedding model: {}", modelName);
        logger.warn("Using mock embeddings - this is for demonstration only");
    }
    
    @Override
    public float[] generateEmbedding(String content) throws Exception {
        // Create a simple deterministic mock embedding based on content hash
        Random random = new Random(content.hashCode());
        float[] embedding = new float[embeddingDimension];
        
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = random.nextFloat() * 2 - 1; // Range: -1 to 1
        }
        
        // Normalize the embedding
        float norm = 0;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        
        if (norm > 0) {
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] /= norm;
            }
        }
        
        return embedding;
    }
}
