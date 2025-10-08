package com.ygmpkk.codesearch.analyzer;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Splits method bodies into chunks that respect a maximum token budget.
 */
public final class TokenAwareChunker {
    public static final int MAX_TOKENS = 32_000;
    private final Encoding encoding;

    public TokenAwareChunker() {
        EncodingRegistry registry = Encodings.newLazyEncodingRegistry();
        this.encoding = registry.getEncoding(Encodings.CL100K_BASE);
    }

    public List<CodeChunk> chunkMethod(Path filePath,
                                       String packageName,
                                       ClassInfo classInfo,
                                       MethodInfo methodInfo) {
        List<String> segments = splitByTokens(methodInfo.getSource());
        int total = segments.size();
        AtomicInteger index = new AtomicInteger(1);

        List<CodeChunk> chunks = new ArrayList<>(total);
        for (String segment : segments) {
            int chunkIndex = index.getAndIncrement();
            String chunkId = buildChunkId(filePath, classInfo.getName(), methodInfo.getName(), chunkIndex, total);
            chunks.add(new CodeChunk(
                    chunkId,
                    filePath,
                    packageName,
                    classInfo.getName(),
                    classInfo.getFields(),
                    methodInfo.getName(),
                    methodInfo.describeSignature(),
                    chunkIndex,
                    total,
                    segment,
                    methodInfo.getMethodCalls()
            ));
        }
        return chunks;
    }

    private List<String> splitByTokens(String source) {
        List<String> chunks = new ArrayList<>();
        String[] lines = source.split("\r?\n");
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            String candidate = current.isEmpty() ? line : current + System.lineSeparator() + line;
            if (tokenCount(candidate) > MAX_TOKENS) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString());
                    current.setLength(0);
                    candidate = line;
                    if (tokenCount(candidate) > MAX_TOKENS) {
                        chunks.addAll(splitLongLine(line));
                        continue;
                    }
                } else {
                    chunks.addAll(splitLongLine(line));
                    continue;
                }
            }
            if (!current.isEmpty()) {
                current.append(System.lineSeparator());
            }
            current.append(line);
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }

        if (chunks.isEmpty()) {
            chunks.add(source);
        }

        return chunks;
    }

    private int tokenCount(String text) {
        return encoding.countTokens(text);
    }

    private List<String> splitLongLine(String line) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        int length = line.length();
        while (start < length) {
            int end = Math.min(length, start + Math.max(1, MAX_TOKENS / 4));
            String chunk = line.substring(start, end);
            if (tokenCount(chunk) > MAX_TOKENS && chunk.length() > 1) {
                end = start + chunk.length() / 2;
                chunk = line.substring(start, Math.max(start + 1, end));
            }
            pieces.add(chunk);
            start += chunk.length();
        }
        return pieces;
    }

    private String buildChunkId(Path filePath, String className, String methodName, int index, int total) {
        return (filePath.toString() + "::" + className + "::" + methodName + "::" + index + "/" + total)
                .toLowerCase(Locale.ROOT);
    }
}
