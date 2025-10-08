package com.ygmpkk.codesearch.analyzer;

import java.util.Objects;

/**
 * Represents a method invocation discovered by the syntax analyzer.
 */
public record MethodCall(String name, String qualifier, String argumentsText) {

    public MethodCall {
        Objects.requireNonNull(name, "name");
    }

    /**
     * @return A human readable representation of the call target.
     */
    public String displayName() {
        if (qualifier != null && !qualifier.isBlank()) {
            return qualifier + "." + name;
        }
        return name;
    }

    /**
     * @return Arguments as captured from source or {@code "()"} if unavailable.
     */
    public String argumentsOrDefault() {
        if (argumentsText == null || argumentsText.isBlank()) {
            return "()";
        }
        return argumentsText.trim();
    }
}
