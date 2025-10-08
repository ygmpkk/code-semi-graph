package com.ygmpkk.codesearch.analyzer;

import java.util.Objects;

/**
 * Represents a method parameter in a declaration.
 */
public record MethodParameter(String type, String name) {

    public MethodParameter {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
    }

    public String signatureFragment() {
        return type + " " + name;
    }
}
