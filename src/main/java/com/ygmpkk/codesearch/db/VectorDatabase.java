package com.ygmpkk.codesearch.db;

import java.util.List;

/**
 * Interface for vector database operations
 * Used to store and search code embeddings for semantic code search
 */
public interface VectorDatabase extends AutoCloseable {
    /**
     * Initialize the database schema
     */
    void initialize() throws Exception;
    
    /**
     * Store a code embedding
     * @param filePath Path to the code file
     * @param content Content of the code file
     * @param embedding Vector embedding of the code
     */
    void storeEmbedding(String filePath, String content, float[] embedding) throws Exception;
    
    /**
     * Store a code embedding with metadata
     * @param filePath Path to the code file
     * @param packageName Package name
     * @param className Class name
     * @param methodName Method name
     * @param content Content of the code chunk
     * @param embedding Vector embedding of the code
     */
    default void storeEmbeddingWithMetadata(String filePath, String packageName, String className, 
                                           String methodName, String content, float[] embedding) throws Exception {
        // Default implementation delegates to basic storeEmbedding
        // Implementations can override to store additional metadata
        storeEmbedding(filePath, content, embedding);
    }
    
    /**
     * Search for similar code embeddings
     * @param queryEmbedding Query vector embedding
     * @param limit Maximum number of results to return
     * @return List of search results with file paths and similarity scores
     */
    List<SearchResult> searchSimilar(float[] queryEmbedding, int limit) throws Exception;
    
    /**
     * Get the total number of embeddings stored
     * @return Count of stored embeddings
     */
    int getEmbeddingCount() throws Exception;
    
    /**
     * Result of a vector similarity search
     */
    class SearchResult {
        private final String filePath;
        private final String content;
        private final double similarity;
        
        public SearchResult(String filePath, String content, double similarity) {
            this.filePath = filePath;
            this.content = content;
            this.similarity = similarity;
        }
        
        public String getFilePath() {
            return filePath;
        }
        
        public String getContent() {
            return content;
        }
        
        public double getSimilarity() {
            return similarity;
        }
    }
}
