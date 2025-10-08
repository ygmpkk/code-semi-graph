package com.ygmpkk.codesearch.embedding;

/**
 * Abstract base class for embedding models
 * Provides a common interface for generating code embeddings using different strategies
 */
public abstract class EmbeddingModel implements AutoCloseable {
    protected final String modelName;
    protected final int embeddingDimension;
    
    /**
     * Constructor for EmbeddingModel
     * @param modelName Name of the embedding model
     * @param embeddingDimension Dimension of the embedding vectors
     */
    protected EmbeddingModel(String modelName, int embeddingDimension) {
        this.modelName = modelName;
        this.embeddingDimension = embeddingDimension;
    }
    
    /**
     * Initialize the embedding model
     * This method should be called before generating any embeddings
     * @throws Exception if initialization fails
     */
    public abstract void initialize() throws Exception;
    
    /**
     * Generate embedding for the given text content
     * @param content Text content to generate embedding for
     * @return Float array representing the embedding vector
     * @throws Exception if embedding generation fails
     */
    public abstract float[] generateEmbedding(String content) throws Exception;
    
    /**
     * Get the name of the embedding model
     * @return Model name
     */
    public String getModelName() {
        return modelName;
    }
    
    /**
     * Get the dimension of the embedding vectors
     * @return Embedding dimension
     */
    public int getEmbeddingDimension() {
        return embeddingDimension;
    }
    
    /**
     * Close the embedding model and release resources
     * Default implementation does nothing, subclasses should override if needed
     */
    @Override
    public void close() throws Exception {
        // Default: no-op, subclasses can override
    }
}
