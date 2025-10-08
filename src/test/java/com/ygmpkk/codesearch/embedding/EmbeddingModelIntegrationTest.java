package com.ygmpkk.codesearch.embedding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for embedding models with SemiBuildCommand workflow
 */
class EmbeddingModelIntegrationTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    void testMockEmbeddingModelEndToEnd() throws Exception {
        // Create a test file
        Path testFile = tempDir.resolve("TestClass.java");
        Files.writeString(testFile, "public class TestClass { }");
        
        // Create and initialize mock model
        try (EmbeddingModel model = EmbeddingModelFactory.createMockModel()) {
            assertNotNull(model);
            
            // Read file and generate embedding
            String content = Files.readString(testFile);
            float[] embedding = model.generateEmbedding(content);
            
            assertNotNull(embedding);
            assertEquals(768, embedding.length);
            
            // Verify embedding is normalized
            float norm = 0;
            for (float v : embedding) {
                norm += v * v;
            }
            norm = (float) Math.sqrt(norm);
            assertEquals(1.0f, norm, 0.0001f);
        }
    }
    
    @Test
    void testMultipleEmbeddingsConsistency() throws Exception {
        try (EmbeddingModel model = EmbeddingModelFactory.createModel("mock")) {
            String content1 = "public class Test1 { }";
            String content2 = "public class Test2 { }";
            String content3 = "public class Test1 { }"; // Same as content1
            
            float[] emb1 = model.generateEmbedding(content1);
            float[] emb2 = model.generateEmbedding(content2);
            float[] emb3 = model.generateEmbedding(content3);
            
            // Same content should produce same embeddings
            assertArrayEquals(emb1, emb3, "Same content should produce same embeddings");
            
            // Different content should produce different embeddings
            boolean different = false;
            for (int i = 0; i < emb1.length; i++) {
                if (Math.abs(emb1[i] - emb2[i]) > 0.0001f) {
                    different = true;
                    break;
                }
            }
            assertTrue(different, "Different content should produce different embeddings");
        }
    }
    
    @Test
    void testBatchProcessing() throws Exception {
        // Simulate batch processing like in SemiBuildCommand
        try (EmbeddingModel model = EmbeddingModelFactory.createModel("mock")) {
            String[] contents = {
                "public class A { }",
                "public class B { }",
                "public class C { }",
                "public class D { }"
            };
            
            float[][] embeddings = new float[contents.length][];
            
            for (int i = 0; i < contents.length; i++) {
                embeddings[i] = model.generateEmbedding(contents[i]);
                assertNotNull(embeddings[i]);
                assertEquals(768, embeddings[i].length);
            }
            
            // Verify all embeddings are different
            for (int i = 0; i < embeddings.length; i++) {
                for (int j = i + 1; j < embeddings.length; j++) {
                    boolean different = false;
                    for (int k = 0; k < embeddings[i].length; k++) {
                        if (Math.abs(embeddings[i][k] - embeddings[j][k]) > 0.0001f) {
                            different = true;
                            break;
                        }
                    }
                    assertTrue(different, 
                        String.format("Embeddings %d and %d should be different", i, j));
                }
            }
        }
    }
}
