package com.ygmpkk.codesearch.embedding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DjlEmbeddingModelTest {

    @TempDir
    Path tempDir;

    @Test
    void inferEmbeddingDimensionFromConfigJson() throws IOException {
        Path config = tempDir.resolve("config.json");
        Files.writeString(config, "{\"hidden_size\": 1024}");

        Integer dimension = DjlEmbeddingModel.HuggingFaceSupport.inferEmbeddingDimension(tempDir);

        assertNotNull(dimension);
        assertEquals(1024, dimension);
    }

    @Test
    void inferEmbeddingDimensionFromSentenceTransformersConfig() throws IOException {
        Path stConfig = tempDir.resolve("sentence_transformers_config.json");
        Files.writeString(stConfig, "{\"sentence_embedding_dimension\": 1536}");

        Integer dimension = DjlEmbeddingModel.HuggingFaceSupport.inferEmbeddingDimension(tempDir);

        assertNotNull(dimension);
        assertEquals(1536, dimension);
    }

    @Test
    void loadTranslatorArgumentsReadsPoolingConfig() throws IOException {
        Path stConfig = tempDir.resolve("sentence_transformers_config.json");
        Files.writeString(stConfig, "{" +
                "\"pooling_config\": {" +
                "\"pooling_mode_cls_token\": true," +
                "\"normalize_embeddings\": false" +
                "}" +
                "}");

        Map<String, Object> arguments = DjlEmbeddingModel.HuggingFaceSupport.loadTranslatorArguments(tempDir);

        assertEquals("cls", arguments.get("pooling"));
        assertEquals(Boolean.FALSE, arguments.get("normalize"));
    }

    @Test
    void shouldIncludeTokenTypesWhenTypeVocabSizeGreaterThanOne() throws IOException {
        Path config = tempDir.resolve("config.json");
        Files.writeString(config, "{\"type_vocab_size\": 2}");

        assertTrue(DjlEmbeddingModel.HuggingFaceSupport.shouldIncludeTokenTypes(tempDir));
    }

    @Test
    void resolvePreferredEnginePrefersSystemProperty() {
        assertEquals("TensorFlow", DjlEmbeddingModel.resolvePreferredEngine("TensorFlow", "OnnxRuntime"));
    }

    @Test
    void resolvePreferredEngineFallsBackToEnvironment() {
        assertEquals("OnnxRuntime", DjlEmbeddingModel.resolvePreferredEngine(null, " OnnxRuntime "));
    }

    @Test
    void resolvePreferredEngineReturnsNullWhenValuesBlank() {
        assertNull(DjlEmbeddingModel.resolvePreferredEngine("  ", ""));
    }

    @Test
    void patchQwen3ConfigToQwen2() throws Exception {
        // Create a qwen3 config.json with null sliding_window
        Path config = tempDir.resolve("config.json");
        String qwen3Config = """
                {
                  "model_type": "qwen3",
                  "hidden_size": 1024,
                  "num_attention_heads": 16,
                  "sliding_window": null
                }
                """;
        Files.writeString(config, qwen3Config);

        // Create a DjlEmbeddingModel instance
        DjlEmbeddingModel model = new DjlEmbeddingModel("test-qwen3", tempDir.toString(), 1024);

        // Try to initialize - this should patch the config
        try {
            model.initialize();
        } catch (Exception e) {
            // Expected to fail due to missing model files, but config should be patched
            // and then restored
        }

        // Verify the config is restored to original qwen3 with null sliding_window
        String restoredContent = Files.readString(config);
        assertTrue(restoredContent.contains("\"model_type\": \"qwen3\"") ||
                   restoredContent.contains("\"model_type\":\"qwen3\""),
                "Config should be restored to qwen3 after initialization");
        assertTrue(restoredContent.contains("\"sliding_window\": null") ||
                   restoredContent.contains("\"sliding_window\":null"),
                "Config should be restored with null sliding_window");

        // Verify backup file was removed
        Path backup = tempDir.resolve("config.json.backup");
        assertFalse(Files.exists(backup), "Backup file should be cleaned up");
    }
}
