package com.ygmpkk.codesearch.embedding;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EmbeddingModelFactory
 */
class EmbeddingModelFactoryTest {
    
    @Test
    void testCreateMockModel() throws Exception {
        try (EmbeddingModel model = EmbeddingModelFactory.createMockModel()) {
            assertNotNull(model);
            assertTrue(model instanceof MockEmbeddingModel);
            assertEquals(768, model.getEmbeddingDimension());
        }
    }
    
    @Test
    void testCreateModelWithMockName() throws Exception {
        try (EmbeddingModel model = EmbeddingModelFactory.createModel("mock")) {
            assertNotNull(model);
            assertTrue(model instanceof MockEmbeddingModel);
        }
    }
    
    @Test
    void testCreateModelWithNullName() throws Exception {
        try (EmbeddingModel model = EmbeddingModelFactory.createModel(null)) {
            assertNotNull(model);
            assertTrue(model instanceof MockEmbeddingModel);
        }
    }
    
    @Test
    void testCreateModelWithEmptyName() throws Exception {
        try (EmbeddingModel model = EmbeddingModelFactory.createModel("")) {
            assertNotNull(model);
            assertTrue(model instanceof MockEmbeddingModel);
        }
    }
    
    @Test
    void testCreateHttpModel() throws Exception {
        try (EmbeddingModel model = EmbeddingModelFactory.createModel("http://localhost:8080/embeddings", "qwen3-embedding:0.6b", 768, null, null)) {
            assertNotNull(model);
            assertTrue(model instanceof HttpEmbeddingModel);
            assertEquals(768, model.getEmbeddingDimension());
        }
    }
    
    @Test
    void testCreateHttpsModel() throws Exception {
        try (EmbeddingModel model = EmbeddingModelFactory.createModel("https://api.example.com/embeddings", "qwen3-embedding:0.6b", 768, null, "test-key")) {
            assertNotNull(model);
            assertTrue(model instanceof HttpEmbeddingModel);
        }
    }
    
    @Test
    void testCreateDjlModel() throws Exception {
        String modelPath = "/tmp/test-model";
        // DJL model initialization will fail without actual model files, which is expected
        // We just verify that the factory creates the right type
        try (EmbeddingModel model = EmbeddingModelFactory.createModel("Qwen/Qwen3-Embedding-0.6B", null, null, modelPath, null)) {
            fail("Should fail because model doesn't exist at path");
        } catch (Exception e) {
            // Expected - model doesn't exist
            assertTrue(e.getMessage().contains("Failed to initialize DJL model") || 
                      e.getMessage().contains("ModelNotFoundException"));
        }
    }
    
    @Test
    void testCreateModelFallsBackToMock() throws Exception {
        // Unknown model name without path should fall back to mock
        try (EmbeddingModel model = EmbeddingModelFactory.createModel("UnknownModel", null, null, null, null)) {
            assertNotNull(model);
            assertTrue(model instanceof MockEmbeddingModel);
        }
    }
    
    @Test
    void testGenerateEmbeddingWithFactoryModel() throws Exception {
        try (EmbeddingModel model = EmbeddingModelFactory.createModel("mock")) {
            String content = "test content";
            float[] embedding = model.generateEmbedding(content);
            
            assertNotNull(embedding);
            assertEquals(768, embedding.length);
        }
    }
}
