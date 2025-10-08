package com.ygmpkk.codesearch.db;

import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.database.Document;
import com.arcadedb.database.MutableDocument;
import com.arcadedb.query.sql.executor.ResultSet;
import com.ygmpkk.codesearch.analysis.CodeChunk;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

                database.getSchema().getType(EMBEDDING_TYPE).createProperty("chunkId", String.class);
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("filePath", String.class);
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("fileName", String.class);
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("packageName", String.class);
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("className", String.class);
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("qualifiedClassName", String.class);
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("properties", List.class);
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("methodName", String.class);
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("methodSignature", String.class);
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("tokenCount", Integer.class);
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("content", String.class);
                database.getSchema().getType(EMBEDDING_TYPE).createProperty("embedding", float[].class);

                database.getSchema().createTypeIndex(
                        com.arcadedb.schema.Schema.INDEX_TYPE.LSM_TREE,
                        true,
                        EMBEDDING_TYPE,
                        "chunkId"
                );

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
    public void storeEmbedding(CodeChunk chunk, float[] embedding) throws Exception {
        database.transaction(() -> {
            // Check if document already exists
            ResultSet result = database.query("sql",
                "SELECT FROM " + EMBEDDING_TYPE + " WHERE chunkId = ?", chunk.chunkId());

            MutableDocument doc;
            if (result.hasNext()) {
                // Update existing document
                doc = result.next().getRecord().get().asDocument().modify();
            } else {
                // Create new document
                doc = database.newDocument(EMBEDDING_TYPE);
                doc.set("chunkId", chunk.chunkId());
            }

            doc.set("filePath", chunk.filePath().toString());
            doc.set("fileName", chunk.fileName());
            doc.set("packageName", chunk.packageName());
            doc.set("className", chunk.className());
            doc.set("qualifiedClassName", chunk.qualifiedClassName());
            doc.set("properties", new ArrayList<>(chunk.properties()));
            doc.set("methodName", chunk.methodName());
            doc.set("methodSignature", chunk.methodSignature());
            doc.set("tokenCount", chunk.tokenCount());
            doc.set("content", chunk.content());
            doc.set("embedding", embedding);
            doc.save();

            logger.debug("Stored embedding for chunk: {}", chunk.chunkId());
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
            String chunkId = doc.getString("chunkId");
            String filePath = doc.getString("filePath");
            String fileName = doc.getString("fileName");
            String packageName = doc.getString("packageName");
            String className = doc.getString("className");
            String qualifiedClassName = doc.getString("qualifiedClassName");
            @SuppressWarnings("unchecked")
            List<String> properties = Optional.ofNullable((List<String>) doc.get("properties"))
                    .map(ArrayList::new)
                    .orElseGet(ArrayList::new);
            String methodName = doc.getString("methodName");
            String methodSignature = doc.getString("methodSignature");
            String content = doc.getString("content");
            Integer tokenCount = doc.getInteger("tokenCount");
            float[] storedEmbedding = (float[]) doc.get("embedding");

            double similarity = cosineSimilarity(queryEmbedding, storedEmbedding);
            scoredResults.add(new ScoredResult(
                    chunkId,
                    filePath,
                    fileName,
                    packageName,
                    className,
                    qualifiedClassName,
                    properties,
                    methodName,
                    methodSignature,
                    content,
                    tokenCount != null ? tokenCount : 0,
                    similarity));
        }
        
        // Sort by similarity (descending) and take top N
        scoredResults.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        
        int resultCount = Math.min(limit, scoredResults.size());
        for (int i = 0; i < resultCount; i++) {
            ScoredResult scored = scoredResults.get(i);
            results.add(new SearchResult(
                    scored.chunkId,
                    scored.filePath,
                    scored.fileName,
                    scored.packageName,
                    scored.className,
                    scored.qualifiedClassName,
                    List.copyOf(scored.properties),
                    scored.methodName,
                    scored.methodSignature,
                    scored.content,
                    scored.tokenCount,
                    scored.similarity));
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
        final String chunkId;
        final String filePath;
        final String fileName;
        final String packageName;
        final String className;
        final String qualifiedClassName;
        final List<String> properties;
        final String methodName;
        final String methodSignature;
        final String content;
        final int tokenCount;
        final double similarity;

        ScoredResult(String chunkId,
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
                     double similarity) {
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
    }
}
