package com.ygmpkk.codesearch.analyzer;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents an embedding chunk created from a method body.
 */
public record CodeChunk(
        String chunkId,
        Path filePath,
        String packageName,
        String className,
        List<String> fields,
        String methodName,
        String methodSignature,
        int chunkIndex,
        int chunkCount,
        String code,
        List<MethodCall> methodCalls) {

    public CodeChunk {
        Objects.requireNonNull(chunkId, "chunkId");
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(code, "code");
        fields = fields != null ? List.copyOf(fields) : List.of();
        methodCalls = methodCalls != null ? List.copyOf(methodCalls) : List.of();
    }

    /**
     * Create a textual representation that is used for embedding generation.
     */
    public String toEmbeddingText() {
        String fieldsLine = fields.isEmpty()
                ? "(none)"
                : fields.stream().collect(Collectors.joining(", "));
        String callsLine = methodCalls.isEmpty()
                ? "(none)"
                : methodCalls.stream()
                .map(call -> call.displayName() + call.argumentsOrDefault())
                .collect(Collectors.joining(", "));

        String packageLine = packageName != null && !packageName.isBlank()
                ? packageName
                : "(default)";

        return "File: " + filePath + "\n"
                + "Package: " + packageLine + "\n"
                + "Class: " + className + "\n"
                + "Fields: " + fieldsLine + "\n"
                + "Method: " + methodName + "\n"
                + "Signature: " + methodSignature + "\n"
                + "Chunk: " + chunkIndex + "/" + chunkCount + "\n"
                + "Calls: " + callsLine + "\n\n"
                + code;
    }
}
