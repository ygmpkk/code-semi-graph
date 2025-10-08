package com.ygmpkk.codesearch.embedding;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * DJL-based embedding model implementation
 * Loads and runs transformer models like Qwen3-Embedding-0.6B using Deep Java Library
 */
public class DjlEmbeddingModel extends EmbeddingModel {
    private static final Logger logger = LogManager.getLogger(DjlEmbeddingModel.class);
    
    private final String modelPath;
    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;
    
    /**
     * Constructor for DJL embedding model
     * @param modelName Name of the model (e.g., "Qwen/Qwen3-Embedding-0.6B")
     * @param modelPath Local path to the model files
     * @param embeddingDimension Dimension of the embedding vectors
     */
    public DjlEmbeddingModel(String modelName, String modelPath, int embeddingDimension) {
        super(modelName, embeddingDimension);
        this.modelPath = modelPath;
    }
    
    /**
     * Constructor with default embedding dimension for Qwen3-Embedding-0.6B
     * @param modelName Name of the model
     * @param modelPath Local path to the model files
     */
    public DjlEmbeddingModel(String modelName, String modelPath) {
        this(modelName, modelPath, 768);
    }
    
    @Override
    public void initialize() throws Exception {
        logger.info("Initializing DJL embedding model: {}", modelName);
        logger.info("Model path: {}", modelPath);
        
        try {
            // Create criteria for loading the model
            Criteria<String, float[]> criteria = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optModelPath(Paths.get(modelPath))
                    .optEngine("PyTorch")
                    .build();
            
            // Load the model
            model = criteria.loadModel();
            predictor = model.newPredictor();
            
            logger.info("DJL embedding model initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize DJL embedding model: {}", e.getMessage());
            throw new Exception("Failed to initialize DJL model: " + e.getMessage(), e);
        }
    }
    
    @Override
    public float[] generateEmbedding(String content) throws Exception {
        if (predictor == null) {
            throw new IllegalStateException("Model not initialized. Call initialize() first.");
        }
        
        try {
            // Use the predictor to generate embeddings
            float[] embedding = predictor.predict(content);
            
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
        } catch (TranslateException e) {
            logger.error("Failed to generate embedding: {}", e.getMessage());
            throw new Exception("Failed to generate embedding: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void close() throws Exception {
        logger.info("Closing DJL embedding model");
        
        if (predictor != null) {
            predictor.close();
            predictor = null;
        }
        
        if (model != null) {
            model.close();
            model = null;
        }
    }
}
