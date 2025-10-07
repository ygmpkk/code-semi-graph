package com.ygmpkk.codesearch.db;

import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite implementation of VectorDatabase
 * Stores code embeddings and performs similarity search using cosine similarity
 */
public class SqliteVectorDatabase implements VectorDatabase {
    private static final Logger logger = LogManager.getLogger(SqliteVectorDatabase.class);
    private final Connection connection;
    private final Gson gson = new Gson();
    
    public SqliteVectorDatabase(String dbPath) throws SQLException {
        String url = "jdbc:sqlite:" + dbPath;
        this.connection = DriverManager.getConnection(url);
        logger.debug("Connected to SQLite database: {}", dbPath);
    }
    
    @Override
    public void initialize() throws Exception {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS embeddings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_path TEXT NOT NULL UNIQUE,
                content TEXT NOT NULL,
                embedding TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSql);
            
            // Create index on file_path for faster lookups
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_path ON embeddings(file_path)");
            
            logger.debug("Database schema initialized");
        }
    }
    
    @Override
    public void storeEmbedding(String filePath, String content, float[] embedding) throws Exception {
        String sql = """
            INSERT OR REPLACE INTO embeddings (file_path, content, embedding)
            VALUES (?, ?, ?)
            """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, filePath);
            pstmt.setString(2, content);
            pstmt.setString(3, gson.toJson(embedding));
            pstmt.executeUpdate();
            
            logger.debug("Stored embedding for: {}", filePath);
        }
    }
    
    @Override
    public List<SearchResult> searchSimilar(float[] queryEmbedding, int limit) throws Exception {
        List<SearchResult> results = new ArrayList<>();
        
        String sql = "SELECT file_path, content, embedding FROM embeddings";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            // Calculate cosine similarity for each embedding
            List<ScoredResult> scoredResults = new ArrayList<>();
            
            while (rs.next()) {
                String filePath = rs.getString("file_path");
                String content = rs.getString("content");
                String embeddingJson = rs.getString("embedding");
                
                float[] storedEmbedding = gson.fromJson(embeddingJson, float[].class);
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
        }
        
        return results;
    }
    
    @Override
    public int getEmbeddingCount() throws Exception {
        String sql = "SELECT COUNT(*) FROM embeddings";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        
        return 0;
    }
    
    @Override
    public void close() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            logger.debug("Database connection closed");
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
