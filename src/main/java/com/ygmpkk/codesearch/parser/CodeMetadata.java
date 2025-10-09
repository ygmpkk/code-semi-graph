package com.ygmpkk.codesearch.parser;

import java.util.List;

/**
 * Metadata extracted from parsed code
 * Contains file information, package, class, methods, and properties
 */
public record CodeMetadata(
        String filePath,
        String language,
        String packageName,
        String className,
        List<String> properties,
        List<MethodInfo> methods
) {
    /**
     * Information about a method
     */
    public record MethodInfo(
            String name,
            String returnType,
            List<String> parameters,
            String body,
            int startLine,
            int endLine,
            List<String> callees // Methods this method calls
    ) {}
}
