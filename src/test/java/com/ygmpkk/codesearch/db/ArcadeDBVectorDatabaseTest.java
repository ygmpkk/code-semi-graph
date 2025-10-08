package com.ygmpkk.codesearch.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArcadeDBVectorDatabaseTest {
    
    @TempDir
    Path tempDir;
    
    private VectorDatabase vectorDb;
    
    @BeforeEach
    void setUp() throws Exception {
        String dbPath = tempDir.resolve("test-arcadedb-embeddings").toString();
        vectorDb = new ArcadeDBVectorDatabase(dbPath);
        vectorDb.initialize();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (vectorDb != null) {
            vectorDb.close();
        }
    }
    
    @Test
    void testStoreAndRetrieveEmbedding() throws Exception {
        // Create a simple embedding
        float[] embedding = {0.1f, 0.2f, 0.3f, 0.4f, 0.5f};
        
        // Store the embedding
        vectorDb.storeEmbedding("test/file.java", "public class Test {}", embedding);
        
        // Verify it was stored
        assertEquals(1, vectorDb.getEmbeddingCount());
    }
    
    @Test
    void testSearchSimilar() throws Exception {
        // Store multiple embeddings
        float[] embedding1 = {1.0f, 0.0f, 0.0f};
        float[] embedding2 = {0.0f, 1.0f, 0.0f};
        float[] embedding3 = {0.9f, 0.1f, 0.0f};
        
        vectorDb.storeEmbedding("file1.java", "content1", embedding1);
        vectorDb.storeEmbedding("file2.java", "content2", embedding2);
        vectorDb.storeEmbedding("file3.java", "content3", embedding3);
        
        // Search for similar to embedding1
        List<VectorDatabase.SearchResult> results = vectorDb.searchSimilar(embedding1, 2);
        
        // Should get 2 results
        assertEquals(2, results.size());
        
        // First result should be file1 (exact match)
        assertEquals("file1.java", results.get(0).getFilePath());
        assertTrue(results.get(0).getSimilarity() > 0.99);
        
        // Second result should be file3 (similar)
        assertEquals("file3.java", results.get(1).getFilePath());
        assertTrue(results.get(1).getSimilarity() > 0.89);
    }
    
    @Test
    void testReplaceEmbedding() throws Exception {
        float[] embedding1 = {1.0f, 0.0f};
        float[] embedding2 = {0.0f, 1.0f};
        
        // Store first embedding
        vectorDb.storeEmbedding("file.java", "content1", embedding1);
        assertEquals(1, vectorDb.getEmbeddingCount());
        
        // Replace with new embedding
        vectorDb.storeEmbedding("file.java", "content2", embedding2);
        assertEquals(1, vectorDb.getEmbeddingCount());
    }
    
    @Test
    void testEmptySearch() throws Exception {
        float[] queryEmbedding = {1.0f, 0.0f};
        
        List<VectorDatabase.SearchResult> results = vectorDb.searchSimilar(queryEmbedding, 5);
        
        assertTrue(results.isEmpty());
    }
}
