package com.ygmpkk.codesearch.embedding;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MockEmbeddingModel
 */
class MockEmbeddingModelTest {
    
    @Test
    void testInitialize() throws Exception {
        try (MockEmbeddingModel model = new MockEmbeddingModel()) {
            model.initialize();
            assertEquals("Mock-Embedding-768", model.getModelName());
            assertEquals(768, model.getEmbeddingDimension());
        }
    }
    
    @Test
    void testGenerateEmbedding() throws Exception {
        try (MockEmbeddingModel model = new MockEmbeddingModel()) {
            model.initialize();
            
            String content = "public class Test { }";
            float[] embedding = model.generateEmbedding(content);
            
            assertNotNull(embedding);
            assertEquals(768, embedding.length);
            
            // Check that embedding is normalized (norm should be close to 1)
            float norm = 0;
            for (float v : embedding) {
                norm += v * v;
            }
            norm = (float) Math.sqrt(norm);
            
            assertEquals(1.0f, norm, 0.0001f, "Embedding should be normalized");
        }
    }
    
    @Test
    void testDeterministicEmbeddings() throws Exception {
        try (MockEmbeddingModel model = new MockEmbeddingModel()) {
            model.initialize();
            
            String content = "public class Test { }";
            float[] embedding1 = model.generateEmbedding(content);
            float[] embedding2 = model.generateEmbedding(content);
            
            assertArrayEquals(embedding1, embedding2, "Same content should produce same embedding");
        }
    }
    
    @Test
    void testDifferentContentProducesDifferentEmbeddings() throws Exception {
        try (MockEmbeddingModel model = new MockEmbeddingModel()) {
            model.initialize();
            
            String content1 = "public class Test1 { }";
            String content2 = "public class Test2 { }";
            
            float[] embedding1 = model.generateEmbedding(content1);
            float[] embedding2 = model.generateEmbedding(content2);
            
            // Check that embeddings are different
            boolean different = false;
            for (int i = 0; i < embedding1.length; i++) {
                if (Math.abs(embedding1[i] - embedding2[i]) > 0.0001f) {
                    different = true;
                    break;
                }
            }
            
            assertTrue(different, "Different content should produce different embeddings");
        }
    }
    
    @Test
    void testCustomModelName() throws Exception {
        try (MockEmbeddingModel model = new MockEmbeddingModel("CustomMock")) {
            model.initialize();
            assertEquals("CustomMock", model.getModelName());
        }
    }
}
