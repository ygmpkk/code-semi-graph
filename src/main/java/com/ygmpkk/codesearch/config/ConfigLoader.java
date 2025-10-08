package com.ygmpkk.codesearch.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Loads the {@code config.yaml} configuration file for the application.
 */
public final class ConfigLoader {
    private static final Logger logger = LogManager.getLogger(ConfigLoader.class);
    private static final String CONFIG_FILE_NAME = "config.yaml";
    private static final Yaml YAML = new Yaml();

    private ConfigLoader() {
    }

    /**
     * Load the configuration from the given home directory.
     *
     * @param homeDirectory the home directory containing {@code config.yaml}
     * @return the loaded configuration, or defaults if no file is found
     */
    public static AppConfig load(Path homeDirectory) {
        return load(homeDirectory, null);
    }

    /**
     * Load the configuration using an optional explicit config path.
     *
     * @param homeDirectory the resolved home directory
     * @param explicitConfigPath optional override for the config file location
     * @return the loaded configuration
     */
    public static AppConfig load(Path homeDirectory, Path explicitConfigPath) {
        Objects.requireNonNull(homeDirectory, "homeDirectory");
        Path normalizedHome = homeDirectory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(normalizedHome);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create home directory: " + normalizedHome, e);
        }

        Path configPath = explicitConfigPath != null ? explicitConfigPath : normalizedHome.resolve(CONFIG_FILE_NAME);

        if (Files.notExists(configPath)) {
            logger.debug("Configuration file not found at {}. Writing default configuration.", configPath);
            AppConfig defaults = AppConfig.createDefault(normalizedHome);
            writeDefaultConfig(configPath, defaults);
            return defaults;
        }

        try (InputStream input = Files.newInputStream(configPath)) {
            Map<String, Object> root = YAML.load(input);
            if (root == null) {
                logger.warn("Configuration file {} is empty. Using defaults.", configPath);
                return AppConfig.createDefault(normalizedHome);
            }
            return parseConfig(normalizedHome, root);
        } catch (Exception e) {
            logger.warn("Failed to read configuration from {}: {}. Using defaults.", configPath, e.getMessage());
            logger.debug("Stack trace:", e);
            return AppConfig.createDefault(normalizedHome);
        }
    }

    private static AppConfig parseConfig(Path homeDirectory, Map<String, Object> root) {
        AppConfig defaultConfig = AppConfig.createDefault(homeDirectory);

        Map<String, Object> embeddingMap = asSection(root.get("embedding"));
        String model = firstNonBlank(asString(embeddingMap.get("model")), defaultConfig.getEmbedding().getModel());
        String modelName = firstNonBlank(asString(embeddingMap.get("modelName")), defaultConfig.getEmbedding().getModelName());
        Integer embeddingDimension = asInteger(embeddingMap.get("embeddingDimension"));
        Path modelPath = resolvePath(homeDirectory, asString(embeddingMap.get("modelPath")));
        String apiKey = firstNonBlank(asString(embeddingMap.get("apiKey")), defaultConfig.getEmbedding().getApiKey());
        Integer batchSize = asInteger(embeddingMap.get("batchSize"));

        Map<String, Object> indexMap = asSection(root.get("index"));
        Path indexDirectory = resolvePath(homeDirectory, asString(indexMap.get("directory")));
        if (indexDirectory == null) {
            indexDirectory = defaultConfig.getIndex().getDirectory();
        }

        Map<String, Object> searchMap = asSection(root.get("search"));
        Integer topK = asInteger(searchMap.get("topK"));

        return AppConfig.builder()
                .withHomeDirectory(homeDirectory)
                .withEmbedding(AppConfig.EmbeddingConfig.builder()
                        .withModel(model)
                        .withModelName(modelName)
                        .withEmbeddingDimension(embeddingDimension)
                        .withModelPath(modelPath)
                        .withApiKey(apiKey)
                        .withBatchSize(batchSize)
                        .build())
                .withIndex(AppConfig.IndexConfig.builder()
                        .withDirectory(indexDirectory)
                        .build())
                .withSearch(AppConfig.SearchConfig.builder()
                        .withTopK(topK)
                        .build())
                .build();
    }

    private static void writeDefaultConfig(Path configPath, AppConfig defaults) {
        try {
            Files.createDirectories(configPath.getParent());
            Map<String, Object> configMap = new LinkedHashMap<>(defaults.toMap());
            try (Writer writer = new OutputStreamWriter(Files.newOutputStream(configPath), StandardCharsets.UTF_8)) {
                YAML.dump(configMap, writer);
            }
        } catch (IOException e) {
            logger.warn("Failed to write default configuration to {}: {}", configPath, e.getMessage());
            logger.debug("Stack trace:", e);
        }
    }

    private static Map<String, Object> asSection(Object section) {
        if (section instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static String asString(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value).trim();
        return stringValue.isEmpty() ? null : stringValue;
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }

    private static Path resolvePath(Path homeDirectory, String value) {
        if (value == null) {
            return null;
        }
        String expanded = value.startsWith("~")
                ? value.replaceFirst("~", System.getProperty("user.home"))
                : value;
        Path path = Paths.get(expanded);
        if (!path.isAbsolute()) {
            path = homeDirectory.resolve(path);
        }
        return path.normalize();
    }

    /**
     * Resolve the configuration path for a given home directory without loading it.
     */
    public static Path resolveConfigPath(Path homeDirectory) {
        Objects.requireNonNull(homeDirectory, "homeDirectory");
        return homeDirectory.toAbsolutePath().normalize().resolve(CONFIG_FILE_NAME);
    }
}
