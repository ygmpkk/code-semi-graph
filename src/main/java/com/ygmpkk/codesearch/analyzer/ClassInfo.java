package com.ygmpkk.codesearch.analyzer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a class (or interface/enum) discovered in a source file.
 */
public final class ClassInfo {
    private final String name;
    private final List<String> fields;
    private final List<MethodInfo> methods;

    public ClassInfo(String name, List<String> fields, List<MethodInfo> methods) {
        this.name = Objects.requireNonNull(name, "name");
        this.fields = fields != null ? new ArrayList<>(fields) : new ArrayList<>();
        this.methods = methods != null ? new ArrayList<>(methods) : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public List<String> getFields() {
        return Collections.unmodifiableList(fields);
    }

    public List<MethodInfo> getMethods() {
        return Collections.unmodifiableList(methods);
    }
}
