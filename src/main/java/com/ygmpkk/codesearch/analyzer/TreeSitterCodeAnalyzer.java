package com.ygmpkk.codesearch.analyzer;

import ch.usi.si.seart.treesitter.Language;
import ch.usi.si.seart.treesitter.Node;
import ch.usi.si.seart.treesitter.Parser;
import ch.usi.si.seart.treesitter.Tree;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
    private static final Method NODE_IS_NULL_METHOD = resolveIsNullMethod();

    public Optional<FileAnalysisResult> analyze(Path filePath, String content) {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(content, "content");

        LanguageSupport language = LanguageSupport.fromFile(filePath);
        if (language == null) {
            logger.debug("No tree-sitter language support for file: {}", filePath);
            return Optional.empty();
        }

        byte[] sourceBytes = content.getBytes(StandardCharsets.UTF_8);

        Language resolvedLanguage;
        try {
            resolvedLanguage = language.language();
        } catch (IllegalStateException e) {
            logger.warn("Failed to load tree-sitter language for {}: {}", filePath, e.getMessage());
            logger.debug("Language loading error", e);
            return Optional.empty();
        }

        Parser parser = null;
        Tree tree = null;
        try {
            parser = new Parser();
            parser.setLanguage(resolvedLanguage);
            tree = parseTree(parser, content, sourceBytes);
            if (tree == null) {
                logger.warn("tree-sitter returned null tree for file: {}", filePath);
                return Optional.empty();
            }

            Node root = tree.getRootNode();
            String packageName = null;
            List<ClassInfo> classes = new ArrayList<>();

            int namedChildCount = root.getNamedChildCount();
            for (int i = 0; i < namedChildCount; i++) {
                Node child = root.getNamedChild(i);
                if (isNull(child)) {
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
        } catch (Exception e) {
            logger.warn("Failed to analyze {} with tree-sitter: {}", filePath, e.getMessage());
            logger.debug("Stack trace:", e);
            return Optional.empty();
        } finally {
            closeQuietly(tree);
            closeQuietly(parser);
        }
    }

    private static Method resolveIsNullMethod() {
        try {
            return Node.class.getMethod("isNull");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private boolean isNull(Node node) {
        if (node == null) {
            return true;
        }
        if (NODE_IS_NULL_METHOD == null) {
            return false;
        }
        try {
            Object result = NODE_IS_NULL_METHOD.invoke(node);
            if (result instanceof Boolean bool) {
                return bool;
            }
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            // ignore and fall through to treat the node as non-null
        }
        return false;
    }

    private Tree parseTree(Parser parser, String content, byte[] sourceBytes) {
        List<Throwable> errors = new ArrayList<>();

        Tree tree = tryParse(parser, "parse", new Class<?>[]{byte[].class}, new Object[]{sourceBytes}, errors);
        if (tree != null) {
            return tree;
        }

        tree = tryParse(parser, "parseBytes", new Class<?>[]{byte[].class}, new Object[]{sourceBytes}, errors);
        if (tree != null) {
            return tree;
        }

        tree = tryParse(parser, "parseString", new Class<?>[]{String.class}, new Object[]{content}, errors);
        if (tree != null) {
            return tree;
        }

        IllegalStateException failure = new IllegalStateException("No compatible parse method found on tree-sitter Parser");
        for (Throwable error : errors) {
            failure.addSuppressed(error);
        }
        throw failure;
    }

    private Tree tryParse(Parser parser,
                          String methodName,
                          Class<?>[] parameterTypes,
                          Object[] arguments,
                          List<Throwable> errors) {
        try {
            Method method = Parser.class.getMethod(methodName, parameterTypes);
            Object result = method.invoke(parser, arguments);
            if (result instanceof Tree tree) {
                return tree;
            }
            if (result != null) {
                errors.add(new IllegalStateException("Unexpected return type from Parser." + methodName));
            }
        } catch (NoSuchMethodException ignored) {
            // method does not exist on this Parser implementation, try next option
        } catch (IllegalAccessException e) {
            errors.add(e);
        } catch (InvocationTargetException e) {
            errors.add(e.getTargetException());
        }
        return null;
    }

    private void closeQuietly(Object closable) {
        if (closable instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                logger.debug("Failed to close tree-sitter resource", e);
            }
        }
    }

    private ClassInfo parseClass(LanguageSupport language, Node classNode, byte[] sourceBytes) {
        String className = slice(classNode.getChildByFieldName("name"), sourceBytes);
        Node bodyNode = classNode.getChildByFieldName("body");

        List<String> fields = new ArrayList<>();
        List<MethodInfo> methods = new ArrayList<>();

        if (!isNull(bodyNode)) {
            int bodyChildren = bodyNode.getNamedChildCount();
            for (int i = 0; i < bodyChildren; i++) {
                Node member = bodyNode.getNamedChild(i);
                if (isNull(member)) {
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
            if (isNull(child)) {
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
        if (!isNull(modifiersNode)) {
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
        if (!isNull(parametersNode)) {
            int paramCount = parametersNode.getNamedChildCount();
            for (int i = 0; i < paramCount; i++) {
                Node param = parametersNode.getNamedChild(i);
                if (isNull(param)) {
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
        if (isNull(node)) {
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
        if (isNull(node)) {
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
        JAVA("java", TreeSitterLanguageLoader::loadJavaLanguage, Set.of(".java"),
                Set.of("class_declaration", "interface_declaration", "enum_declaration"),
                Set.of("field_declaration"),
                Set.of("method_declaration", "constructor_declaration"));

        private final String id;
        private final CheckedSupplier<Language> languageSupplier;
        private final Set<String> extensions;
        private final Set<String> classNodeTypes;
        private final Set<String> fieldNodeTypes;
        private final Set<String> methodNodeTypes;
        private volatile Language resolvedLanguage;

        LanguageSupport(String id,
                        CheckedSupplier<Language> languageSupplier,
                        Set<String> extensions,
                        Set<String> classNodeTypes,
                        Set<String> fieldNodeTypes,
                        Set<String> methodNodeTypes) {
            this.id = id;
            this.languageSupplier = languageSupplier;
            this.extensions = Set.copyOf(extensions);
            this.classNodeTypes = Set.copyOf(classNodeTypes);
            this.fieldNodeTypes = Set.copyOf(fieldNodeTypes);
            this.methodNodeTypes = Set.copyOf(methodNodeTypes);
        }

        public Language language() {
            Language cached = resolvedLanguage;
            if (cached != null) {
                return cached;
            }
            synchronized (this) {
                cached = resolvedLanguage;
                if (cached != null) {
                    return cached;
                }
                try {
                    cached = languageSupplier.get();
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to load language '" + id + "'", e);
                }
                resolvedLanguage = cached;
                return cached;
            }
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

    private static final class TreeSitterLanguageLoader {
        private TreeSitterLanguageLoader() {
        }

        private static Language loadJavaLanguage() throws Exception {
            List<Throwable> errors = new ArrayList<>();

            Language language = tryLanguageRegistry(errors);
            if (language != null) {
                return language;
            }

            language = tryDirectLoad(errors);
            if (language != null) {
                return language;
            }

            IllegalStateException failure = new IllegalStateException("Unable to load tree-sitter Java language");
            for (Throwable error : errors) {
                failure.addSuppressed(error);
            }
            throw failure;
        }

        private static Language tryLanguageRegistry(List<Throwable> errors) {
            List<String> classNames = List.of(
                    "ch.usi.si.seart.treesitter.Languages",
                    "ch.usi.si.seart.treesitter.languages.Languages",
                    "ch.usi.si.seart.treesitter.LanguageLibrary"
            );

            for (String className : classNames) {
                try {
                    Class<?> clazz = Class.forName(className);

                    Language language = tryLanguageFromRegistryClass(clazz);
                    if (language != null) {
                        return language;
                    }
                } catch (ClassNotFoundException notFound) {
                    // Ignore and continue to the next registry candidate
                } catch (ReflectiveOperationException e) {
                    errors.add(e);
                }
            }

            return null;
        }

        private static Language tryLanguageFromRegistryClass(Class<?> clazz)
                throws ReflectiveOperationException {
            for (String fieldName : List.of("JAVA", "JAVA_LANGUAGE", "JAVA_LANG")) {
                try {
                    Object value = clazz.getField(fieldName).get(null);
                    Language language = coerceLanguage(value);
                    if (language != null) {
                        return language;
                    }
                } catch (NoSuchFieldException ignored) {
                    // try next candidate
                }
            }

            for (String methodName : List.of("java", "JAVA", "getJava", "loadJava", "javaLanguage")) {
                try {
                    Method method = clazz.getMethod(methodName);
                    Object result = method.invoke(null);
                    Language language = coerceLanguage(result);
                    if (language != null) {
                        return language;
                    }
                } catch (NoSuchMethodException ignored) {
                    // try next accessor
                }
            }

            for (String methodName : List.of("language", "getLanguage", "load")) {
                try {
                    Method method = clazz.getMethod(methodName, String.class);
                    Object result = method.invoke(null, "java");
                    Language language = coerceLanguage(result);
                    if (language != null) {
                        return language;
                    }
                } catch (NoSuchMethodException ignored) {
                    // continue
                }
            }

            Object instance = clazz.getDeclaredConstructor().newInstance();
            Language language = coerceLanguage(instance);
            if (language != null) {
                return language;
            }

            for (String methodName : List.of("java", "JAVA", "getJava", "loadJava", "javaLanguage")) {
                try {
                    Method method = clazz.getMethod(methodName);
                    Object result = method.invoke(instance);
                    language = coerceLanguage(result);
                    if (language != null) {
                        return language;
                    }
                } catch (NoSuchMethodException ignored) {
                    // continue
                }
            }

            for (String methodName : List.of("language", "getLanguage", "load")) {
                try {
                    Method method = clazz.getMethod(methodName, String.class);
                    Object result = method.invoke(instance, "java");
                    Language languageCandidate = coerceLanguage(result);
                    if (languageCandidate != null) {
                        return languageCandidate;
                    }
                } catch (NoSuchMethodException ignored) {
                    // continue
                }
            }

            return null;
        }

        private static Language tryDirectLoad(List<Throwable> errors) {
            try {
                Method load = Language.class.getMethod("load", String.class, String.class);
                Object result = load.invoke(null, "tree-sitter-java", "tree_sitter_java");
                if (result instanceof Language language) {
                    return language;
                }
            } catch (NoSuchMethodException ignored) {
                // ignore; not supported in this version of the library
            } catch (IllegalAccessException e) {
                errors.add(e);
            } catch (InvocationTargetException e) {
                errors.add(e.getTargetException());
            }

            for (String methodName : List.of("load", "fromName", "forName")) {
                try {
                    Method method = Language.class.getMethod(methodName, String.class);
                    Object result = method.invoke(null, "java");
                    if (result instanceof Language language) {
                        return language;
                    }
                } catch (NoSuchMethodException ignored) {
                    // try next option
                } catch (IllegalAccessException e) {
                    errors.add(e);
                } catch (InvocationTargetException e) {
                    errors.add(e.getTargetException());
                }

                try {
                    Method method = Language.class.getMethod(methodName, String.class);
                    Object result = method.invoke(null, "tree-sitter-java");
                    if (result instanceof Language language) {
                        return language;
                    }
                } catch (NoSuchMethodException ignored) {
                    // already handled above
                } catch (IllegalAccessException e) {
                    errors.add(e);
                } catch (InvocationTargetException e) {
                    errors.add(e.getTargetException());
                }
            }

            for (String methodName : List.of("load", "fromName", "forName")) {
                try {
                    Method method = Language.class.getMethod(methodName, String.class, String.class);
                    Object result = method.invoke(null, "tree-sitter-java", "tree_sitter_java");
                    if (result instanceof Language language) {
                        return language;
                    }
                } catch (NoSuchMethodException ignored) {
                    // continue searching for overloads
                } catch (IllegalAccessException e) {
                    errors.add(e);
                } catch (InvocationTargetException e) {
                    errors.add(e.getTargetException());
                }
            }
            return null;
        }

        private static Language coerceLanguage(Object candidate)
                throws ReflectiveOperationException {
            if (candidate == null) {
                return null;
            }
            if (candidate instanceof Language language) {
                return language;
            }

            Class<?> type = candidate.getClass();
            for (String accessor : List.of("getLanguage", "language")) {
                try {
                    Method method = type.getMethod(accessor);
                    Object result = method.invoke(candidate);
                    if (result instanceof Language language) {
                        return language;
                    }
                } catch (NoSuchMethodException ignored) {
                    // continue to the next accessor
                }
            }

            return null;
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
