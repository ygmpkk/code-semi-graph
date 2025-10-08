package com.ygmpkk.codesearch.db;

import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.database.Document;
import com.arcadedb.database.MutableDocument;
import com.arcadedb.query.sql.executor.ResultSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * ArcadeDB implementation of VectorDatabase
 * Stores code embeddings and performs similarity search using cosine similarity
 */
public class ArcadeDBVectorDatabase implements VectorDatabase {
    private static final Logger logger = LogManager.getLogger(ArcadeDBVectorDatabase.class);
    private final Database database;
    private static final String EMBEDDING_TYPE = "Embedding";
    
    public ArcadeDBVectorDatabase(String dbPath) {
        DatabaseFactory factory = new DatabaseFactory(dbPath);
        if (factory.exists()) {
            this.database = factory.open();
            logger.debug("Opened existing ArcadeDB database: {}", dbPath);
        } else {
            this.database = factory.create();
            logger.debug("Created new ArcadeDB database: {}", dbPath);
        }
    }
    
    @Override
    public void initialize() throws Exception {
        database.transaction(() -> {
            // Create document type for embeddings if it doesn't exist
            if (!database.getSchema().existsType(EMBEDDING_TYPE)) {
                database.getSchema().createDocumentType(EMBEDDING_TYPE);
                
                // Create index on file_path for faster lookups using ArcadeDB schema API
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("filePath", String.class);
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("packageName", String.class);
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("className", String.class);
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("methodName", String.class);
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("content", String.class);
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("embedding", float[].class);
                
                database.getSchema().createTypeIndex(
                    com.arcadedb.schema.Schema.INDEX_TYPE.LSM_TREE, 
                    false, 
                    EMBEDDING_TYPE, 
                    "filePath"
                );
                
                logger.debug("Created {} type with schema", EMBEDDING_TYPE);
            }
        });
    }
    
    @Override
    public void storeEmbedding(String filePath, String content, float[] embedding) throws Exception {
        storeEmbeddingWithMetadata(filePath, "", "", "", content, embedding);
    }
    
    @Override
    public void storeEmbeddingWithMetadata(String filePath, String packageName, String className,
                                          String methodName, String content, float[] embedding) throws Exception {
        database.transaction(() -> {
            // Create a unique key combining file path and method name
            String uniqueKey = filePath + ":" + (methodName != null && !methodName.isEmpty() ? methodName : "file");
            
            // Check if document already exists
            ResultSet result = database.query("sql", 
                "SELECT FROM " + EMBEDDING_TYPE + " WHERE filePath = ? AND methodName = ?", 
                filePath, methodName != null ? methodName : "");
            
            MutableDocument doc;
            if (result.hasNext()) {
                // Update existing document
                doc = result.next().getRecord().get().asDocument().modify();
            } else {
                // Create new document
                doc = database.newDocument(EMBEDDING_TYPE);
                doc.set("filePath", filePath);
            }
            
            doc.set("packageName", packageName != null ? packageName : "");
            doc.set("className", className != null ? className : "");
            doc.set("methodName", methodName != null ? methodName : "");
            doc.set("content", content);
            doc.set("embedding", embedding);
            doc.save();
            
            logger.debug("Stored embedding for: {} (method: {})", filePath, methodName);
        });
    }
    
    @Override
    public List<SearchResult> searchSimilar(float[] queryEmbedding, int limit) throws Exception {
        List<SearchResult> results = new ArrayList<>();
        List<ScoredResult> scoredResults = new ArrayList<>();
        
        // Query all embeddings
        ResultSet resultSet = database.query("sql", "SELECT FROM " + EMBEDDING_TYPE);
        
        while (resultSet.hasNext()) {
            Document doc = resultSet.next().getRecord().get().asDocument();
            String filePath = doc.getString("filePath");
            String content = doc.getString("content");
            float[] storedEmbedding = (float[]) doc.get("embedding");
            
            double similarity = cosineSimilarity(queryEmbedding, storedEmbedding);
            scoredResults.add(new ScoredResult(filePath, content, similarity));
        }
        
        // Sort by similarity (descending) and take top N
        scoredResults.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        
        int resultCount = Math.min(limit, scoredResults.size());
        for (int i = 0; i < resultCount; i++) {
            ScoredResult scored = scoredResults.get(i);
            results.add(new SearchResult(scored.filePath, scored.content, scored.similarity));
        }
        
        return results;
    }
    
    @Override
    public int getEmbeddingCount() throws Exception {
        ResultSet result = database.query("sql", "SELECT count(*) as count FROM " + EMBEDDING_TYPE);
        if (result.hasNext()) {
            return ((Number) result.next().getProperty("count")).intValue();
        }
        return 0;
    }
    
    @Override
    public void close() throws Exception {
        if (database != null) {
            database.close();
            logger.debug("ArcadeDB database closed");
        }
    }
    
    /**
     * Calculate cosine similarity between two vectors
     * @param a First vector
     * @param b Second vector
     * @return Cosine similarity (-1 to 1)
     */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * Helper class for sorting search results
     */
    private static class ScoredResult {
        final String filePath;
        final String content;
        final double similarity;
        
        ScoredResult(String filePath, String content, double similarity) {
            this.filePath = filePath;
            this.content = content;
            this.similarity = similarity;
        }
    }
}
