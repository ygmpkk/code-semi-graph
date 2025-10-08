package com.ygmpkk.codesearch.analyzer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregated analysis for a single source file.
 */
public final class FileAnalysisResult {
    private final Path filePath;
    private final String languageId;
    private final String packageName;
    private final List<ClassInfo> classes;

    public FileAnalysisResult(Path filePath, String languageId, String packageName, List<ClassInfo> classes) {
        this.filePath = Objects.requireNonNull(filePath, "filePath");
        this.languageId = languageId;
        this.packageName = packageName;
        this.classes = classes != null ? new ArrayList<>(classes) : new ArrayList<>();
    }

    public Path getFilePath() {
        return filePath;
    }

    public String getLanguageId() {
        return languageId;
    }

    public String getPackageName() {
        return packageName;
    }

    public List<ClassInfo> getClasses() {
        return Collections.unmodifiableList(classes);
    }
}
