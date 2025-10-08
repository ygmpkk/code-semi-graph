package com.ygmpkk.codesearch.db;

import com.ygmpkk.codesearch.analysis.CodeChunk;

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
    void storeEmbedding(CodeChunk chunk, float[] embedding) throws Exception;
    
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
        private final String chunkId;
        private final String filePath;
        private final String fileName;
        private final String packageName;
        private final String className;
        private final String qualifiedClassName;
        private final List<String> properties;
        private final String methodName;
        private final String methodSignature;
        private final String content;
        private final int tokenCount;
        private final double similarity;

        public SearchResult(
                String chunkId,
                String filePath,
                String fileName,
                String packageName,
                String className,
                String qualifiedClassName,
                List<String> properties,
                String methodName,
                String methodSignature,
                String content,
                int tokenCount,
                double similarity
        ) {
            this.chunkId = chunkId;
            this.filePath = filePath;
            this.fileName = fileName;
            this.packageName = packageName;
            this.className = className;
            this.qualifiedClassName = qualifiedClassName;
            this.properties = properties;
            this.methodName = methodName;
            this.methodSignature = methodSignature;
            this.content = content;
            this.tokenCount = tokenCount;
            this.similarity = similarity;
        }

        public String getChunkId() {
            return chunkId;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getFileName() {
            return fileName;
        }

        public String getPackageName() {
            return packageName;
        }

        public String getClassName() {
            return className;
        }

        public String getQualifiedClassName() {
            return qualifiedClassName;
        }

        public List<String> getProperties() {
            return properties;
        }

        public String getMethodName() {
            return methodName;
        }

        public String getMethodSignature() {
            return methodSignature;
        }

        public String getContent() {
            return content;
        }

        public int getTokenCount() {
            return tokenCount;
        }

        public double getSimilarity() {
            return similarity;
        }
    }
}
