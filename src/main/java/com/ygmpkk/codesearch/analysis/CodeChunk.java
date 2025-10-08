package com.ygmpkk.codesearch.analysis;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a chunk of code extracted from a source file that will be indexed
 * in the vector database. Each chunk carries the structural metadata required
 * for semantic search and downstream tooling.
 */
public final class CodeChunk {
    private final String chunkId;
    private final Path filePath;
    private final String packageName;
    private final String className;
    private final String qualifiedClassName;
    private final List<String> properties;
    private final String methodName;
    private final String methodSignature;
    private final String content;
    private final int tokenCount;

    public CodeChunk(
            String chunkId,
            Path filePath,
            String packageName,
            String className,
            String qualifiedClassName,
            List<String> properties,
            String methodName,
            String methodSignature,
            String content,
            int tokenCount
    ) {
        this.chunkId = Objects.requireNonNull(chunkId, "chunkId");
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.packageName = packageName != null ? packageName : "";
        this.className = className != null ? className : "";
        this.qualifiedClassName = qualifiedClassName != null ? qualifiedClassName : this.className;
        this.properties = properties != null ? List.copyOf(properties) : List.of();
        this.methodName = methodName != null ? methodName : "";
        this.methodSignature = methodSignature != null ? methodSignature : this.methodName;
        this.content = Objects.requireNonNull(content, "content");
        this.tokenCount = Math.max(0, tokenCount);
    }

    public String chunkId() {
        return chunkId;
    }

    public Path filePath() {
        return filePath;
    }

    public String fileName() {
        return filePath.getFileName().toString();
    }

    public String packageName() {
        return packageName;
    }

    public String className() {
        return className;
    }

    public String qualifiedClassName() {
        return qualifiedClassName;
    }

    public List<String> properties() {
        return Collections.unmodifiableList(properties);
    }

    public String methodName() {
        return methodName;
    }

    public String methodSignature() {
        return methodSignature;
    }

    public String content() {
        return content;
    }

    public int tokenCount() {
        return tokenCount;
    }
}

