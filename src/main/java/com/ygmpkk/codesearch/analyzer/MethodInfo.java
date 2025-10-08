package com.ygmpkk.codesearch.analyzer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Metadata about a method declaration including its body and outbound calls.
 */
public final class MethodInfo {
    private final String name;
    private final String returnType;
    private final List<String> modifiers;
    private final List<MethodParameter> parameters;
    private final String source;
    private final List<MethodCall> methodCalls;

    public MethodInfo(String name,
                      String returnType,
                      List<String> modifiers,
                      List<MethodParameter> parameters,
                      String source,
                      List<MethodCall> methodCalls) {
        this.name = Objects.requireNonNull(name, "name");
        this.returnType = returnType;
        this.modifiers = modifiers != null ? List.copyOf(modifiers) : List.of();
        this.parameters = parameters != null ? List.copyOf(parameters) : List.of();
        this.source = Objects.requireNonNull(source, "source");
        this.methodCalls = methodCalls != null ? new ArrayList<>(methodCalls) : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public String getReturnType() {
        return returnType;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public List<MethodParameter> getParameters() {
        return parameters;
    }

    public String getSource() {
        return source;
    }

    public List<MethodCall> getMethodCalls() {
        return Collections.unmodifiableList(methodCalls);
    }

    public String describeSignature() {
        String modifiersPart = modifiers.isEmpty() ? "" : String.join(" ", modifiers) + " ";
        String returnPart = returnType != null ? returnType + " " : "";
        String params = parameters.stream()
                .map(MethodParameter::signatureFragment)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return modifiersPart + returnPart + name + "(" + params + ")";
    }
}
