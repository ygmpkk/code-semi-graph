package com.ygmpkk.codesearch.config;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Application configuration loaded from {@code config.yaml}.
 */
public final class AppConfig {
    private final Path homeDirectory;
    private final EmbeddingConfig embedding;
    private final IndexConfig index;
    private final SearchConfig search;

    private AppConfig(Builder builder) {
        this.homeDirectory = builder.homeDirectory;
        this.embedding = builder.embedding;
        this.index = builder.index;
        this.search = builder.search;
    }

    /**
     * Create a default configuration for the provided home directory.
     */
    public static AppConfig createDefault(Path homeDirectory) {
        Objects.requireNonNull(homeDirectory, "homeDirectory");
        return builder()
                .withHomeDirectory(homeDirectory)
                .withEmbedding(EmbeddingConfig.builder()
                        .withModel("mock")
                        .withModelName("mock")
                        .withBatchSize(32)
                        .build())
                .withIndex(IndexConfig.builder()
                        .withDirectory(homeDirectory.resolve("index"))
                        .build())
                .withSearch(SearchConfig.builder()
                        .withTopK(5)
                        .build())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Path getHomeDirectory() {
        return homeDirectory;
    }

    public EmbeddingConfig getEmbedding() {
        return embedding;
    }

    public IndexConfig getIndex() {
        return index;
    }

    public SearchConfig getSearch() {
        return search;
    }

    /**
     * Convert the configuration to a serializable map representation.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> embeddingMap = new LinkedHashMap<>();
        embeddingMap.put("model", embedding.getModel());
        embeddingMap.put("modelName", embedding.getModelName());
        if (embedding.getEmbeddingDimension() != null) {
            embeddingMap.put("embeddingDimension", embedding.getEmbeddingDimension());
        }
        if (embedding.getModelPath() != null) {
            embeddingMap.put("modelPath", relativize(embedding.getModelPath()));
        }
        if (embedding.getApiKey() != null) {
            embeddingMap.put("apiKey", embedding.getApiKey());
        }
        embeddingMap.put("batchSize", embedding.getBatchSize());
        root.put("embedding", embeddingMap);

        Map<String, Object> indexMap = new LinkedHashMap<>();
        indexMap.put("directory", relativize(index.getDirectory()));
        root.put("index", indexMap);

        Map<String, Object> searchMap = new LinkedHashMap<>();
        searchMap.put("topK", search.getTopK());
        root.put("search", searchMap);

        return root;
    }

    private String relativize(Path path) {
        Path normalized = path.normalize();
        if (normalized.startsWith(homeDirectory)) {
            return homeDirectory.relativize(normalized).toString();
        }
        return normalized.toString();
    }

    public static final class Builder {
        private Path homeDirectory;
        private EmbeddingConfig embedding;
        private IndexConfig index;
        private SearchConfig search;

        private Builder() {
        }

        public Builder withHomeDirectory(Path homeDirectory) {
            this.homeDirectory = homeDirectory;
            return this;
        }

        public Builder withEmbedding(EmbeddingConfig embedding) {
            this.embedding = embedding;
            return this;
        }

        public Builder withIndex(IndexConfig index) {
            this.index = index;
            return this;
        }

        public Builder withSearch(SearchConfig search) {
            this.search = search;
            return this;
        }

        public AppConfig build() {
            Objects.requireNonNull(homeDirectory, "homeDirectory");
            Objects.requireNonNull(embedding, "embedding");
            Objects.requireNonNull(index, "index");
            Objects.requireNonNull(search, "search");
            return new AppConfig(this);
        }
    }

    /**
     * Embedding configuration section.
     */
    public static final class EmbeddingConfig {
        private final String model;
        private final String modelName;
        private final Integer embeddingDimension;
        private final Path modelPath;
        private final String apiKey;
        private final int batchSize;

        private EmbeddingConfig(EmbeddingBuilder builder) {
            this.model = builder.model;
            this.modelName = builder.modelName;
            this.embeddingDimension = builder.embeddingDimension;
            this.modelPath = builder.modelPath;
            this.apiKey = builder.apiKey;
            this.batchSize = builder.batchSize != null ? Math.max(1, builder.batchSize) : 32;
        }

        public static EmbeddingBuilder builder() {
            return new EmbeddingBuilder();
        }

        public String getModel() {
            return model;
        }

        public String getModelName() {
            return modelName;
        }

        public Integer getEmbeddingDimension() {
            return embeddingDimension;
        }

        public Path getModelPath() {
            return modelPath;
        }

        public String getApiKey() {
            return apiKey;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public static final class EmbeddingBuilder {
            private String model;
            private String modelName;
            private Integer embeddingDimension;
            private Path modelPath;
            private String apiKey;
            private Integer batchSize;

            private EmbeddingBuilder() {
            }

            public EmbeddingBuilder withModel(String model) {
                this.model = model;
                return this;
            }

            public EmbeddingBuilder withModelName(String modelName) {
                this.modelName = modelName;
                return this;
            }

            public EmbeddingBuilder withEmbeddingDimension(Integer embeddingDimension) {
                this.embeddingDimension = embeddingDimension;
                return this;
            }

            public EmbeddingBuilder withModelPath(Path modelPath) {
                this.modelPath = modelPath;
                return this;
            }

            public EmbeddingBuilder withApiKey(String apiKey) {
                this.apiKey = apiKey;
                return this;
            }

            public EmbeddingBuilder withBatchSize(Integer batchSize) {
                this.batchSize = batchSize;
                return this;
            }

            public EmbeddingConfig build() {
                return new EmbeddingConfig(this);
            }
        }
    }

    /**
     * Index configuration section.
     */
    public static final class IndexConfig {
        private final Path directory;

        private IndexConfig(IndexBuilder builder) {
            this.directory = builder.directory;
        }

        public static IndexBuilder builder() {
            return new IndexBuilder();
        }

        public Path getDirectory() {
            return directory;
        }

        public static final class IndexBuilder {
            private Path directory;

            private IndexBuilder() {
            }

            public IndexBuilder withDirectory(Path directory) {
                this.directory = directory;
                return this;
            }

            public IndexConfig build() {
                Objects.requireNonNull(directory, "directory");
                return new IndexConfig(this);
            }
        }
    }

    /**
     * Search configuration section.
     */
    public static final class SearchConfig {
        private final int topK;

        private SearchConfig(SearchBuilder builder) {
            this.topK = builder.topK != null ? Math.max(1, builder.topK) : 5;
        }

        public static SearchBuilder builder() {
            return new SearchBuilder();
        }

        public int getTopK() {
            return topK;
        }

        public static final class SearchBuilder {
            private Integer topK;

            private SearchBuilder() {
            }

            public SearchBuilder withTopK(Integer topK) {
                this.topK = topK;
                return this;
            }

            public SearchConfig build() {
                return new SearchConfig(this);
            }
        }
    }
}
