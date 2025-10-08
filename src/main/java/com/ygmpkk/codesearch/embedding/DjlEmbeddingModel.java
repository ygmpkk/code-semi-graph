package com.ygmpkk.codesearch.embedding;

import ai.djl.ModelException;
import ai.djl.engine.Engine;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.translator.TextEmbeddingTranslator;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DJL-based embedding model implementation
 * Loads and runs transformer models like Qwen3-Embedding-0.6B using Deep Java Library
 */
public class DjlEmbeddingModel extends EmbeddingModel {
    private static final Logger logger = LogManager.getLogger(DjlEmbeddingModel.class);

    private final String modelPath;
    private final Path modelDirectory;
    private ZooModel<String, float[]> model;
    private Predictor<String, float[]> predictor;
    private HuggingFaceTokenizer tokenizer;
    
    /**
     * Constructor for DJL embedding model
     * @param modelName Name of the model (e.g., "Qwen/Qwen3-Embedding-0.6B")
     * @param modelPath Local path to the model files
     * @param embeddingDimension Dimension of the embedding vectors
     */
    public DjlEmbeddingModel(String modelName, String modelPath, int embeddingDimension) {
        super(modelName, HuggingFaceSupport.inferEmbeddingDimension(modelPath, embeddingDimension));
        this.modelPath = modelPath;
        this.modelDirectory = HuggingFaceSupport.resolveModelDirectory(modelPath);
    }
    
    /**
     * Constructor with default embedding dimension for Qwen3-Embedding-0.6B
     * @param modelName Name of the model
     * @param modelPath Local path to the model files
     */
    public DjlEmbeddingModel(String modelName, String modelPath) {
        this(modelName, modelPath, 768);
    }
    
    @Override
    public void initialize() throws Exception {
        logger.info("Initializing DJL embedding model: {}", modelName);
        logger.info("Model path: {}", modelPath);

        try {
            if (modelDirectory == null) {
                throw new IllegalArgumentException("Model path is required for DJL embedding models");
            }

            if (!Files.exists(modelDirectory)) {
                throw new IOException("Model path does not exist: " + modelDirectory);
            }

            // Patch qwen3 to qwen2 for compatibility with current DJL version
            Path configPath = modelDirectory.resolve("config.json");
            boolean configPatched = patchQwen3Config(configPath);

            try {
                Map<String, Object> translatorArguments = new HashMap<>(HuggingFaceSupport.loadTranslatorArguments(modelDirectory));
                boolean includeTokenTypes = HuggingFaceSupport.shouldIncludeTokenTypes(modelDirectory);

                tokenizer = HuggingFaceTokenizer.builder()
                        .optTokenizerPath(modelDirectory)
                        .build();

                TextEmbeddingTranslator.Builder translatorBuilder;
                if (translatorArguments.isEmpty()) {
                    translatorBuilder = TextEmbeddingTranslator.builder(tokenizer);
                } else {
                    translatorBuilder = TextEmbeddingTranslator.builder(tokenizer, translatorArguments);
                }

                if (includeTokenTypes && !translatorArguments.containsKey("includeTokenTypes")) {
                    translatorBuilder.optIncludeTokenTypes(true);
                }

                TextEmbeddingTranslator translator = translatorBuilder.build();

                String preferredEngine = resolvePreferredEngine();
                model = loadModelWithFallback(translator, preferredEngine);
                predictor = model.newPredictor();
                Engine engine = model.getNDManager().getEngine();
                logger.info("DJL embedding model initialized successfully with engine '{}' and dimension {}", engine.getEngineName(), embeddingDimension);
            } finally {
                // Restore original config if it was patched
                if (configPatched) {
                    restoreOriginalConfig(configPath);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to initialize DJL embedding model: {}", e.getMessage(), e);
            closeQuietly();
            throw new Exception("Failed to initialize DJL model: " + e.getMessage(), e);
        }
    }
    
    @Override
    public float[] generateEmbedding(String content) throws Exception {
        if (predictor == null) {
            throw new IllegalStateException("Model not initialized. Call initialize() first.");
        }
        
        try {
            // Use the predictor to generate embeddings
            float[] embedding = predictor.predict(content);
            
            // Normalize the embedding
            float norm = 0;
            for (float v : embedding) {
                norm += v * v;
            }
            norm = (float) Math.sqrt(norm);
            
            if (norm > 0) {
                for (int i = 0; i < embedding.length; i++) {
                    embedding[i] /= norm;
                }
            }
            
            return embedding;
        } catch (TranslateException e) {
            logger.error("Failed to generate embedding: {}", e.getMessage());
            throw new Exception("Failed to generate embedding: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void close() throws Exception {
        logger.info("Closing DJL embedding model");
        
        closeQuietly();
    }

    private void closeQuietly() {
        if (predictor != null) {
            try {
                predictor.close();
            } finally {
                predictor = null;
            }
        }

        if (model != null) {
            try {
                model.close();
            } finally {
                model = null;
            }
        }

        if (tokenizer != null) {
            try {
                tokenizer.close();
            } finally {
                tokenizer = null;
            }
        }
    }

    private Criteria<String, float[]> buildCriteria(TextEmbeddingTranslator translator, String engineName) {
        Criteria.Builder<String, float[]> builder = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelPath(modelDirectory)
                .optModelName(modelName)
                .optTranslator(translator);

        if (engineName != null && !engineName.isBlank()) {
            builder.optEngine(engineName);
            if ("pytorch".equalsIgnoreCase(engineName)) {
                builder.optOption("mapLocation", "true");
            }
        }

        return builder.build();
    }

    private ZooModel<String, float[]> loadModelWithFallback(TextEmbeddingTranslator translator, String preferredEngine)
            throws IOException, ModelException {
        Exception preferredEngineFailure = null;

        if (preferredEngine != null && !preferredEngine.isBlank()) {
            try {
                logger.info("Attempting to load DJL model using configured engine '{}'", preferredEngine);
                return buildCriteria(translator, preferredEngine).loadModel();
            } catch (ModelException | IOException e) {
                if (isUnsupportedEngineError(e)) {
                    logger.warn("Engine '{}' is unavailable for model '{}': {}. Falling back to default engine.",
                            preferredEngine, modelName, e.getMessage());
                    preferredEngineFailure = e;
                } else {
                    throw e;
                }
            }
        }

        logger.info("Loading DJL model using default engine resolution");
        try {
            return buildCriteria(translator, null).loadModel();
        } catch (ModelException | IOException e) {
            if (preferredEngineFailure != null) {
                e.addSuppressed(preferredEngineFailure);
            }
            throw e;
        }
    }

    private boolean isUnsupportedEngineError(Throwable throwable) {
        while (throwable != null) {
            String message = throwable.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("doesn't support specified engine")
                        || normalized.contains("no pytorch engine")
                        || normalized.contains("engine pytorch is not available")
                        || normalized.contains("no matching engine")) {
                    return true;
                }
            }
            throwable = throwable.getCause();
        }
        return false;
    }

    /**
     * Patches qwen3 model_type to qwen2 for compatibility with DJL 0.34.0.
     * Qwen3 is architecturally similar to Qwen2, so this substitution works for embedding models.
     * Also handles sliding_window field which can't be null in DJL.
     *
     * @param configPath Path to the config.json file
     * @return true if the config was patched, false otherwise
     */
    private boolean patchQwen3Config(Path configPath) {
        if (!Files.exists(configPath)) {
            return false;
        }

        try {
            String content = Files.readString(configPath);
            JsonObject config = JsonParser.parseString(content).getAsJsonObject();

            boolean needsPatch = false;

            // Patch model_type from qwen3 to qwen2
            if (config.has("model_type")) {
                String modelType = config.get("model_type").getAsString();
                if ("qwen3".equals(modelType)) {
                    logger.info("Detected qwen3 model_type, patching to qwen2 for DJL compatibility");
                    needsPatch = true;
                }
            }

            // Check if sliding_window is null and needs patching
            if (config.has("sliding_window") && config.get("sliding_window").isJsonNull()) {
                logger.info("Detected null sliding_window, patching for DJL compatibility");
                needsPatch = true;
            }

            if (needsPatch) {
                // Backup original config
                Path backupPath = configPath.resolveSibling("config.json.backup");
                Files.copy(configPath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                // Apply patches
                if (config.has("model_type") && "qwen3".equals(config.get("model_type").getAsString())) {
                    config.addProperty("model_type", "qwen2");
                }

                // Remove sliding_window field if it's null (DJL will use default)
                if (config.has("sliding_window") && config.get("sliding_window").isJsonNull()) {
                    config.remove("sliding_window");
                }

                // Write patched config
                String patchedContent = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(config);
                Files.writeString(configPath, patchedContent);

                logger.debug("Config patched successfully, backup saved to {}", backupPath);
                return true;
            }
        } catch (IOException | JsonSyntaxException e) {
            logger.warn("Failed to patch config.json: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Restores the original config.json from backup
     *
     * @param configPath Path to the config.json file
     */
    private void restoreOriginalConfig(Path configPath) {
        Path backupPath = configPath.resolveSibling("config.json.backup");
        if (Files.exists(backupPath)) {
            try {
                Files.move(backupPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                logger.debug("Original config restored from backup");
            } catch (IOException e) {
                logger.warn("Failed to restore original config from backup: {}", e.getMessage());
            }
        }
    }

    static String resolvePreferredEngine() {
        return resolvePreferredEngine(
                System.getProperty("codesearch.djl.engine"),
                System.getenv("CODESEARCH_DJL_ENGINE"));
    }

    static String resolvePreferredEngine(String propertyValue, String environmentValue) {
        String candidate = normalizeEngineValue(propertyValue);
        if (candidate != null) {
            return candidate;
        }
        return normalizeEngineValue(environmentValue);
    }

    private static String normalizeEngineValue(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Utility helpers for working with HuggingFace model formats.
     */
    static final class HuggingFaceSupport {
        private static final Logger supportLogger = LogManager.getLogger(HuggingFaceSupport.class);
        private static final int DEFAULT_DIMENSION = 768;
        private static final String CONFIG_JSON = "config.json";
        private static final String SENTENCE_TRANSFORMERS_CONFIG = "sentence_transformers_config.json";
        private static final List<String> DIMENSION_KEYS = List.of(
                "embedding_size",
                "embedding_dim",
                "hidden_size",
                "word_embed_proj_dim",
                "d_model",
                "model_dim",
                "projection_dim",
                "sentence_embedding_dimension",
                "pooling_output_dimension"
        );

        private HuggingFaceSupport() {
        }

        static Path resolveModelDirectory(String modelPath) {
            if (modelPath == null || modelPath.isEmpty()) {
                return null;
            }

            try {
                return Paths.get(modelPath);
            } catch (InvalidPathException e) {
                supportLogger.warn("Invalid model path provided: {}", modelPath, e);
                return null;
            }
        }

        static int inferEmbeddingDimension(String modelPath, int fallbackDimension) {
            if (modelPath != null && !modelPath.isEmpty()) {
                Path path = resolveModelDirectory(modelPath);
                Integer dimension = inferEmbeddingDimension(path);
                if (dimension != null) {
                    return dimension;
                }
            }

            if (fallbackDimension > 0) {
                return fallbackDimension;
            }

            return DEFAULT_DIMENSION;
        }

        static Integer inferEmbeddingDimension(Path modelPath) {
            if (modelPath == null) {
                return null;
            }

            // Allow passing the config file directly or the containing directory
            Path configPath = Files.isRegularFile(modelPath) && CONFIG_JSON.equals(modelPath.getFileName().toString())
                    ? modelPath
                    : modelPath.resolve(CONFIG_JSON);

            Integer dimension = readDimensionFromConfig(configPath);
            if (dimension != null) {
                return dimension;
            }

            if (Files.isDirectory(modelPath)) {
                // Check sentence transformers config for embedding size information
                Path sentenceTransformersConfig = modelPath.resolve(SENTENCE_TRANSFORMERS_CONFIG);
                dimension = readDimensionFromConfig(sentenceTransformersConfig);
                if (dimension != null) {
                    return dimension;
                }

                // Some sentence-transformer models store pooling metadata under subdirectories
                dimension = readDimensionFromPoolingModules(modelPath);
                if (dimension != null) {
                    return dimension;
                }
            }

            return null;
        }

        static Map<String, Object> loadTranslatorArguments(Path modelDirectory) {
            if (modelDirectory == null || !Files.isDirectory(modelDirectory)) {
                return Collections.emptyMap();
            }

            JsonObject sentenceTransformersConfig = readJson(modelDirectory.resolve(SENTENCE_TRANSFORMERS_CONFIG));
            if (sentenceTransformersConfig == null) {
                return Collections.emptyMap();
            }

            Map<String, Object> arguments = new HashMap<>();
            JsonObject poolingConfig = sentenceTransformersConfig.getAsJsonObject("pooling_config");
            if (poolingConfig != null) {
                String poolingMode = resolvePoolingMode(poolingConfig);
                if (poolingMode != null) {
                    arguments.put("pooling", poolingMode);
                }

                if (poolingConfig.has("normalize_embeddings")) {
                    arguments.put("normalize", poolingConfig.get("normalize_embeddings").getAsBoolean());
                }
            }

            return arguments;
        }

        static boolean shouldIncludeTokenTypes(Path modelDirectory) {
            if (modelDirectory == null) {
                return false;
            }

            JsonObject config = readJson(resolveConfigPath(modelDirectory));
            if (config != null && config.has("type_vocab_size")) {
                try {
                    return config.get("type_vocab_size").getAsInt() > 1;
                } catch (NumberFormatException | ClassCastException e) {
                    supportLogger.debug("Failed to parse type_vocab_size from config: {}", e.getMessage());
                }
            }

            return false;
        }

        private static Integer readDimensionFromConfig(Path configPath) {
            JsonObject config = readJson(configPath);
            if (config == null) {
                return null;
            }

            Integer dimension = extractDimension(config);
            if (dimension != null) {
                return dimension;
            }

            if (config.has("pooling_config") && config.get("pooling_config").isJsonObject()) {
                dimension = extractDimension(config.getAsJsonObject("pooling_config"));
                if (dimension != null) {
                    return dimension;
                }
            }

            return null;
        }

        private static Integer readDimensionFromPoolingModules(Path modelDirectory) {
            try {
                List<Path> poolingConfigs = new ArrayList<>();
                try (var paths = Files.list(modelDirectory)) {
                    paths.filter(Files::isDirectory)
                            .filter(path -> path.getFileName().toString().toLowerCase().contains("pooling"))
                            .forEach(path -> poolingConfigs.add(path.resolve(CONFIG_JSON)));
                }

                for (Path poolingConfig : poolingConfigs) {
                    Integer dimension = readDimensionFromConfig(poolingConfig);
                    if (dimension != null) {
                        return dimension;
                    }
                }
            } catch (IOException e) {
                supportLogger.debug("Failed to inspect pooling modules: {}", e.getMessage());
            }

            return null;
        }

        private static Integer extractDimension(JsonObject config) {
            for (String key : DIMENSION_KEYS) {
                if (config.has(key) && config.get(key).isJsonPrimitive()) {
                    try {
                        return config.get(key).getAsInt();
                    } catch (NumberFormatException e) {
                        try {
                            double value = config.get(key).getAsDouble();
                            return (int) Math.round(value);
                        } catch (NumberFormatException ignored) {
                            supportLogger.debug("Unable to parse numeric value for key '{}'", key);
                        }
                    }
                }
            }

            return null;
        }

        private static String resolvePoolingMode(JsonObject poolingConfig) {
            Map<String, String> poolingOptions = Map.of(
                    "pooling_mode_mean_tokens", "mean",
                    "pooling_mode_max_tokens", "max",
                    "pooling_mode_cls_token", "cls",
                    "pooling_mode_mean_sqrt_len_tokens", "mean_sqrt_len",
                    "pooling_mode_weightedmean_tokens", "weightedmean"
            );

            for (Map.Entry<String, String> entry : poolingOptions.entrySet()) {
                if (poolingConfig.has(entry.getKey()) && poolingConfig.get(entry.getKey()).getAsBoolean()) {
                    return entry.getValue();
                }
            }

            return null;
        }

        private static Path resolveConfigPath(Path modelDirectory) {
            if (modelDirectory == null) {
                return null;
            }

            if (Files.isRegularFile(modelDirectory) && CONFIG_JSON.equals(modelDirectory.getFileName().toString())) {
                return modelDirectory;
            }

            return modelDirectory.resolve(CONFIG_JSON);
        }

        private static JsonObject readJson(Path path) {
            if (path == null || !Files.exists(path)) {
                return null;
            }

            try (Reader reader = Files.newBufferedReader(path)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element != null && element.isJsonObject()) {
                    return element.getAsJsonObject();
                }
            } catch (IOException e) {
                supportLogger.debug("Failed to read JSON file {}: {}", path, e.getMessage());
            } catch (JsonSyntaxException e) {
                supportLogger.warn("Invalid JSON content in {}: {}", path, e.getMessage());
            }

            return null;
        }
    }
}
