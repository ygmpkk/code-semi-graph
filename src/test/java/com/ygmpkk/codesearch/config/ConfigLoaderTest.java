package com.ygmpkk.codesearch.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadCreatesDefaultConfigurationWhenMissing() throws Exception {
        Path homeDir = tempDir.resolve("home");

        AppConfig config = ConfigLoader.load(homeDir);

        assertNotNull(config);
        assertEquals(homeDir.toAbsolutePath().normalize(), config.getHomeDirectory());
        assertEquals("mock", config.getEmbedding().getModel());
        assertEquals(32, config.getEmbedding().getBatchSize());
        assertEquals(homeDir.resolve("index").toAbsolutePath().normalize(), config.getIndex().getDirectory());
        assertEquals(5, config.getSearch().getTopK());

        assertTrue(Files.exists(ConfigLoader.resolveConfigPath(homeDir)));
    }

    @Test
    void loadReadsCustomConfiguration() throws Exception {
        Path homeDir = tempDir.resolve("custom-home");
        Path configPath = homeDir.resolve("config.yaml");
        Files.createDirectories(homeDir);

        String yaml = """
                embedding:
                  model: https://api.example.com/v1/embeddings
                  modelName: remote-model
                  embeddingDimension: 2048
                  modelPath: ./models
                  apiKey: secret-key
                  batchSize: 10
                index:
                  directory: ./indexes/main
                search:
                  topK: 7
                """;
        Files.writeString(configPath, yaml);

        AppConfig config = ConfigLoader.load(homeDir);

        assertEquals("https://api.example.com/v1/embeddings", config.getEmbedding().getModel());
        assertEquals("remote-model", config.getEmbedding().getModelName());
        assertEquals(2048, config.getEmbedding().getEmbeddingDimension());
        assertEquals(homeDir.resolve("models").toAbsolutePath().normalize(), config.getEmbedding().getModelPath());
        assertEquals("secret-key", config.getEmbedding().getApiKey());
        assertEquals(10, config.getEmbedding().getBatchSize());
        assertEquals(homeDir.resolve("indexes/main").toAbsolutePath().normalize(), config.getIndex().getDirectory());
        assertEquals(7, config.getSearch().getTopK());
    }
}
