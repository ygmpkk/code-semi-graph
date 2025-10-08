package com.ygmpkk.codesearch.analyzer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.treesitter.Language;
import org.bytedeco.treesitter.Node;
import org.bytedeco.treesitter.Parser;
import org.bytedeco.treesitter.Tree;
import org.bytedeco.treesitter.java.TreesitterJavaLibrary;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Performs static analysis of source files using tree-sitter.
 */
public class TreeSitterCodeAnalyzer {
    private static final Logger logger = LogManager.getLogger(TreeSitterCodeAnalyzer.class);

    public Optional<FileAnalysisResult> analyze(Path filePath, String content) {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(content, "content");

        LanguageSupport language = LanguageSupport.fromFile(filePath);
        if (language == null) {
            logger.debug("No tree-sitter language support for file: {}", filePath);
            return Optional.empty();
        }

        byte[] sourceBytes = content.getBytes(StandardCharsets.UTF_8);

        try (Parser parser = new Parser()) {
            parser.setLanguage(language.language());
            try (BytePointer pointer = new BytePointer(sourceBytes);
                 Tree tree = parser.parseString(null, pointer)) {
                if (tree == null || tree.isNull()) {
                    logger.warn("tree-sitter returned null tree for file: {}", filePath);
                    return Optional.empty();
                }

                Node root = tree.getRootNode();
                String packageName = null;
                List<ClassInfo> classes = new ArrayList<>();

                int namedChildCount = root.getNamedChildCount();
                for (int i = 0; i < namedChildCount; i++) {
                    Node child = root.getNamedChild(i);
                    if (child == null || child.isNull()) {
                        continue;
                    }
                    String type = child.getType();
                    if ("package_declaration".equals(type)) {
                        Node nameNode = child.getChildByFieldName("name");
                        packageName = slice(nameNode, sourceBytes);
                    } else if (language.isClassLike(type)) {
                        classes.add(parseClass(language, child, sourceBytes));
                    }
                }

                return Optional.of(new FileAnalysisResult(filePath, language.id(), packageName, classes));
            }
        } catch (Exception e) {
            logger.warn("Failed to analyze {} with tree-sitter: {}", filePath, e.getMessage());
            logger.debug("Stack trace:", e);
            return Optional.empty();
        }
    }

    private ClassInfo parseClass(LanguageSupport language, Node classNode, byte[] sourceBytes) {
        String className = slice(classNode.getChildByFieldName("name"), sourceBytes);
        Node bodyNode = classNode.getChildByFieldName("body");

        List<String> fields = new ArrayList<>();
        List<MethodInfo> methods = new ArrayList<>();

        if (bodyNode != null && !bodyNode.isNull()) {
            int bodyChildren = bodyNode.getNamedChildCount();
            for (int i = 0; i < bodyChildren; i++) {
                Node member = bodyNode.getNamedChild(i);
                if (member == null || member.isNull()) {
                    continue;
                }
                String type = member.getType();
                if (language.isFieldDeclaration(type)) {
                    fields.addAll(parseFields(member, sourceBytes));
                } else if (language.isMethodDeclaration(type)) {
                    methods.add(parseMethod(member, sourceBytes));
                }
            }
        }

        return new ClassInfo(className != null ? className : "<anonymous>", fields, methods);
    }

    private List<String> parseFields(Node fieldNode, byte[] sourceBytes) {
        List<String> fields = new ArrayList<>();
        int childCount = fieldNode.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            Node child = fieldNode.getNamedChild(i);
            if (child == null || child.isNull()) {
                continue;
            }
            if ("variable_declarator".equals(child.getType())) {
                Node nameNode = child.getChildByFieldName("name");
                String name = slice(nameNode, sourceBytes);
                if (name != null && !name.isBlank()) {
                    fields.add(name.trim());
                }
            } else if (child.getNamedChildCount() > 0) {
                fields.addAll(parseFields(child, sourceBytes));
            }
        }
        return fields;
    }

    private MethodInfo parseMethod(Node methodNode, byte[] sourceBytes) {
        String name = slice(methodNode.getChildByFieldName("name"), sourceBytes);
        String returnType = slice(methodNode.getChildByFieldName("type"), sourceBytes);

        List<String> modifiers = new ArrayList<>();
        Node modifiersNode = methodNode.getChildByFieldName("modifiers");
        if (modifiersNode != null && !modifiersNode.isNull()) {
            int count = modifiersNode.getNamedChildCount();
            for (int i = 0; i < count; i++) {
                Node modifier = modifiersNode.getNamedChild(i);
                String modifierText = slice(modifier, sourceBytes);
                if (modifierText != null && !modifierText.isBlank()) {
                    modifiers.add(modifierText.trim());
                }
            }
        }

        List<MethodParameter> parameters = new ArrayList<>();
        Node parametersNode = methodNode.getChildByFieldName("parameters");
        if (parametersNode != null && !parametersNode.isNull()) {
            int paramCount = parametersNode.getNamedChildCount();
            for (int i = 0; i < paramCount; i++) {
                Node param = parametersNode.getNamedChild(i);
                if (param == null || param.isNull()) {
                    continue;
                }
                if (!"formal_parameter".equals(param.getType()) && !"receiver_parameter".equals(param.getType())) {
                    continue;
                }
                String type = slice(param.getChildByFieldName("type"), sourceBytes);
                String paramName = slice(param.getChildByFieldName("name"), sourceBytes);
                if (type != null && paramName != null) {
                    parameters.add(new MethodParameter(type.trim(), paramName.trim()));
                }
            }
        }

        Node bodyNode = methodNode.getChildByFieldName("body");
        String methodSource = slice(methodNode, sourceBytes);
        if (methodSource == null) {
            methodSource = "";
        }

        List<MethodCall> methodCalls = new ArrayList<>();
        collectMethodCalls(bodyNode, sourceBytes, methodCalls);

        return new MethodInfo(
                name != null ? name.trim() : "<anonymous>",
                returnType != null ? returnType.trim() : null,
                modifiers,
                parameters,
                methodSource,
                methodCalls
        );
    }

    private void collectMethodCalls(Node node, byte[] sourceBytes, List<MethodCall> calls) {
        if (node == null || node.isNull()) {
            return;
        }
        if ("method_invocation".equals(node.getType())) {
            String methodName = slice(node.getChildByFieldName("name"), sourceBytes);
            String qualifier = slice(node.getChildByFieldName("object"), sourceBytes);
            String arguments = slice(node.getChildByFieldName("arguments"), sourceBytes);
            if (methodName != null && !methodName.isBlank()) {
                calls.add(new MethodCall(methodName.trim(), qualifier != null ? qualifier.trim() : null, arguments));
            }
        }

        int childCount = node.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            Node child = node.getNamedChild(i);
            collectMethodCalls(child, sourceBytes, calls);
        }
    }

    private String slice(Node node, byte[] sourceBytes) {
        if (node == null || node.isNull()) {
            return null;
        }
        int start = (int) node.getStartByte();
        int end = (int) node.getEndByte();
        if (start >= end || start < 0 || end > sourceBytes.length) {
            return null;
        }
        return new String(sourceBytes, start, end - start, StandardCharsets.UTF_8);
    }

    private enum LanguageSupport {
        JAVA("java", TreesitterJavaLibrary.ts_language_java(), Set.of(".java"),
                Set.of("class_declaration", "interface_declaration", "enum_declaration"),
                Set.of("field_declaration"),
                Set.of("method_declaration", "constructor_declaration"));

        private final String id;
        private final Language language;
        private final Set<String> extensions;
        private final Set<String> classNodeTypes;
        private final Set<String> fieldNodeTypes;
        private final Set<String> methodNodeTypes;

        LanguageSupport(String id,
                        Language language,
                        Set<String> extensions,
                        Set<String> classNodeTypes,
                        Set<String> fieldNodeTypes,
                        Set<String> methodNodeTypes) {
            this.id = id;
            this.language = language;
            this.extensions = Set.copyOf(extensions);
            this.classNodeTypes = Set.copyOf(classNodeTypes);
            this.fieldNodeTypes = Set.copyOf(fieldNodeTypes);
            this.methodNodeTypes = Set.copyOf(methodNodeTypes);
        }

        public Language language() {
            return language;
        }

        public String id() {
            return id;
        }

        public boolean isClassLike(String type) {
            return classNodeTypes.contains(type);
        }

        public boolean isFieldDeclaration(String type) {
            return fieldNodeTypes.contains(type);
        }

        public boolean isMethodDeclaration(String type) {
            return methodNodeTypes.contains(type);
        }

        public static LanguageSupport fromFile(Path path) {
            String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
            for (LanguageSupport support : values()) {
                for (String ext : support.extensions) {
                    if (fileName.endsWith(ext)) {
                        return support;
                    }
                }
            }
            return null;
        }
    }
}
