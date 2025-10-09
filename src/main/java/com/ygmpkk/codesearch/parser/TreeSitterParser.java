package com.ygmpkk.codesearch.parser;

import ch.usi.si.seart.treesitter.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parser for source code using tree-sitter
 * Extracts package, class, method, and call chain information
 */
public class TreeSitterParser implements AutoCloseable {
    private static final Logger logger = LogManager.getLogger(TreeSitterParser.class);

    private static final String UNKNOWN_LANGUAGE = "unknown";
    private static final List<Language> STRUCTURED_METADATA_LANGUAGES = List.of(Language.JAVA);

    private final Map<Language, Parser> parserCache = new EnumMap<>(Language.class);

    static {
        loadNativeLibrary();
    }

    /**
     * 加载 tree-sitter 原生库
     * 优先级：
     * 1. 系统 java.library.path (通过 -Djava.library.path 设置)
     * 2. JAR 包中的 native 目录
     * 3. tree-sitter 默认加载机制
     */
    private static void loadNativeLibrary() {
        String osName = System.getProperty("os.name").toLowerCase();
        String libraryName;

        if (osName.contains("mac")) {
            libraryName = "libjava-tree-sitter.dylib";
        } else if (osName.contains("linux")) {
            libraryName = "libjava-tree-sitter.so";
        } else if (osName.contains("windows")) {
            libraryName = "java-tree-sitter.dll";
        } else {
            libraryName = "libjava-tree-sitter.so"; // fallback
        }

        // 1️⃣ 尝试从 java.library.path 加载
        String libraryPath = System.getProperty("java.library.path");
        if (libraryPath != null && !libraryPath.isEmpty()) {
            logger.info("Attempting to load Tree-sitter library from java.library.path: {}", libraryPath);
            String[] paths = libraryPath.split(System.getProperty("path.separator"));
            for (String path : paths) {
                try {
                    Path libFile = Path.of(path, libraryName);
                    if (Files.exists(libFile)) {
                        logger.info("Found library at: {}", libFile);
                        System.load(libFile.toAbsolutePath().toString());
                        logger.info("✅ Tree-sitter native library loaded successfully from: {}", libFile);
                        return;
                    }
                } catch (UnsatisfiedLinkError e) {
                    logger.warn("Failed to load from {}: {}", path, e.getMessage());
                }
            }
        }

        // 2️⃣ 从项目 build/libjava-tree-sitter/ 加载
        try {
            Path projectDir = Path.of(System.getProperty("user.dir"));
            Path buildDir = projectDir.resolve("build").resolve("libjava-tree-sitter").resolve(libraryName);

            logger.info("Attempting to load Tree-sitter library from project build directory: {}", buildDir);

            if (Files.exists(buildDir)) {
                System.load(buildDir.toAbsolutePath().toString());
                logger.info("✅ Tree-sitter native library loaded successfully from build directory: {}", buildDir);
                return;
            } else {
                logger.warn("Tree-sitter library not found in build directory: {}", buildDir);
            }
        } catch (Exception e) {
            logger.error("Failed to load Tree-sitter library from build directory: {}", e.getMessage());
        }

        // 3️⃣ fallback: 从 classpath 内部 (JAR/native/) 尝试提取
        try (InputStream in = TreeSitterParser.class.getResourceAsStream("/native/" + libraryName)) {
            if (in != null) {
                Path tempLib = Files.createTempFile("libjava-tree-sitter", libraryName);
                Files.copy(in, tempLib, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                System.load(tempLib.toAbsolutePath().toString());
                logger.info("✅ Tree-sitter native library loaded from classpath resource (extracted to temp): {}", tempLib);
                return;
            }
        } catch (Exception e) {
            logger.error("Failed to load Tree-sitter library from classpath resource: {}", e.getMessage());
        }

        // ❌ 全部失败
        throw new UnsatisfiedLinkError("Unable to locate or load the Tree-sitter native library (" + libraryName + ")");
    }

    public TreeSitterParser() {
        // Parsers are initialised lazily per language when needed
    }

    /**
     * Parse a source file and extract metadata based on the detected language.
     */
    public CodeMetadata parseFile(Path filePath) throws Exception {
        String content = Files.readString(filePath);
        return parseCode(filePath.toString(), content);
    }

    /**
     * Parse source code and extract metadata based on the detected language from the file name.
     */
    public CodeMetadata parseCode(String filePath, String content) throws Exception {
        Language language = detectLanguage(filePath);
        if (language == null) {
            logger.debug("No associated tree-sitter language for {}. Returning empty metadata.", filePath);
            return emptyMetadata(filePath, UNKNOWN_LANGUAGE);
        }
        return parseCode(filePath, content, language);
    }

    /**
     * Parse a Java source file and extract metadata
     */
    public CodeMetadata parseJavaFile(Path filePath) throws Exception {
        String content = Files.readString(filePath);
        return parseCode(filePath.toString(), content, Language.JAVA);
    }

    /**
     * Parse Java source code and extract metadata
     */
    public CodeMetadata parseJavaCode(String filePath, String content) throws Exception {
        return parseCode(filePath, content, Language.JAVA);
    }

    private CodeMetadata parseCode(String filePath, String content, Language language) throws Exception {
        Parser parser = getParser(language);
        try (Tree tree = parser.parse(content)) {
            Node root = tree.getRootNode();

            if (language == Language.JAVA) {
                return buildJavaMetadata(filePath, content, root, language);
            }

            logger.debug("Tree-sitter parsed {} using language {} but no structured extractor is available.",
                    filePath, language);
            return emptyMetadata(filePath, language);
        }
    }

    private Parser getParser(Language language) {
        synchronized (parserCache) {
            return parserCache.computeIfAbsent(language, Parser::getFor);
        }
    }

    private Language detectLanguage(String filePath) {
        Path candidatePath;
        try {
            candidatePath = Path.of(filePath);
        } catch (InvalidPathException e) {
            logger.debug("Invalid path {} for language detection: {}", filePath, e.getMessage());
            return null;
        }

        Collection<Language> associated = Language.associatedWith(candidatePath);
        if (associated.isEmpty()) {
            return null;
        }

        for (Language preferred : STRUCTURED_METADATA_LANGUAGES) {
            if (associated.contains(preferred)) {
                return preferred;
            }
        }

        Language selected = associated.stream()
                .sorted(Comparator.comparingInt(Language::ordinal))
                .findFirst()
                .orElse(null);

        if (selected != null && associated.size() > 1) {
            logger.debug("Multiple languages {} associated with {}. Selected {}.", associated, filePath, selected);
        }

        return selected;
    }

    private CodeMetadata buildJavaMetadata(String filePath, String content, Node root, Language language) {
        String packageName = extractPackageName(root, content);
        String className = extractClassName(root, content);
        List<String> properties = extractProperties(root, content);
        List<CodeMetadata.MethodInfo> methods = extractMethods(root, content);

        return new CodeMetadata(filePath, toLanguageName(language), packageName, className, properties, methods);
    }

    private CodeMetadata emptyMetadata(String filePath, Language language) {
        return emptyMetadata(filePath, toLanguageName(language));
    }

    private CodeMetadata emptyMetadata(String filePath, String language) {
        return new CodeMetadata(filePath, language, "", "", List.of(), List.of());
    }

    private String toLanguageName(Language language) {
        return language == null ? UNKNOWN_LANGUAGE : language.name().toLowerCase(Locale.ROOT);
    }

    private String extractPackageName(Node root, String content) {
        Node packageNode = findFirstChild(root, "package_declaration");
        if (packageNode != null) {
            Node scopedIdentifier = findFirstChild(packageNode, "scoped_identifier");
            if (scopedIdentifier != null) {
                return getNodeText(scopedIdentifier, content);
            }
            // Try identifier as fallback
            Node identifier = findFirstChild(packageNode, "identifier");
            if (identifier != null) {
                return getNodeText(identifier, content);
            }
        }
        return "";
    }

    private String extractClassName(Node root, String content) {
        // Look for class_declaration, interface_declaration, or enum_declaration
        Node classNode = findFirstChild(root, "class_declaration");
        if (classNode == null) {
            classNode = findFirstChild(root, "interface_declaration");
        }
        if (classNode == null) {
            classNode = findFirstChild(root, "enum_declaration");
        }

        if (classNode != null) {
            Node nameNode = findFirstChild(classNode, "identifier");
            if (nameNode != null) {
                return getNodeText(nameNode, content);
            }
        }
        return "";
    }

    private List<String> extractProperties(Node root, String content) {
        List<String> properties = new ArrayList<>();

        // Find class body
        Node classNode = findFirstChild(root, "class_declaration");
        if (classNode == null) {
            classNode = findFirstChild(root, "interface_declaration");
        }

        if (classNode != null) {
            Node classBody = findFirstChild(classNode, "class_body");
            if (classBody != null) {
                for (int i = 0; i < classBody.getChildCount(); i++) {
                    Node child = classBody.getChild(i);
                    if ("field_declaration".equals(child.getType())) {
                        Node declarator = findFirstChild(child, "variable_declarator");
                        if (declarator != null) {
                            Node nameNode = findFirstChild(declarator, "identifier");
                            if (nameNode != null) {
                                properties.add(getNodeText(nameNode, content));
                            }
                        }
                    }
                }
            }
        }

        return properties;
    }

    private List<CodeMetadata.MethodInfo> extractMethods(Node root, String content) {
        List<CodeMetadata.MethodInfo> methods = new ArrayList<>();

        // Find class body
        Node classNode = findFirstChild(root, "class_declaration");
        if (classNode == null) {
            classNode = findFirstChild(root, "interface_declaration");
        }

        if (classNode != null) {
            Node classBody = findFirstChild(classNode, "class_body");
            if (classBody != null) {
                for (int i = 0; i < classBody.getChildCount(); i++) {
                    Node child = classBody.getChild(i);
                    if ("method_declaration".equals(child.getType()) ||
                            "constructor_declaration".equals(child.getType())) {
                        CodeMetadata.MethodInfo methodInfo = extractMethodInfo(child, content);
                        if (methodInfo != null) {
                            methods.add(methodInfo);
                        }
                    }
                }
            }
        }

        return methods;
    }

    private CodeMetadata.MethodInfo extractMethodInfo(Node methodNode, String content) {
        String methodName = "";
        String returnType = "";
        List<String> parameters = new ArrayList<>();
        List<String> callees = new ArrayList<>();

        // Extract method name
        Node nameNode = findFirstChild(methodNode, "identifier");
        if (nameNode != null) {
            methodName = getNodeText(nameNode, content);
        }

        // Extract return type
        Node typeNode = findFirstChild(methodNode, "type_identifier");
        if (typeNode == null) {
            typeNode = findFirstChild(methodNode, "void_type");
        }
        if (typeNode == null) {
            Node genericType = findFirstChild(methodNode, "generic_type");
            if (genericType != null) {
                typeNode = genericType;
            }
        }
        if (typeNode != null) {
            returnType = getNodeText(typeNode, content);
        }

        // Extract parameters
        Node formalParams = findFirstChild(methodNode, "formal_parameters");
        if (formalParams != null) {
            for (int i = 0; i < formalParams.getChildCount(); i++) {
                Node child = formalParams.getChild(i);
                if ("formal_parameter".equals(child.getType())) {
                    Node paramName = findFirstChild(child, "identifier");
                    if (paramName != null) {
                        parameters.add(getNodeText(paramName, content));
                    }
                }
            }
        }

        // Extract method body and find method calls
        Node body = findFirstChild(methodNode, "block");
        String methodBody = "";
        if (body != null) {
            methodBody = getNodeText(body, content);
            callees = extractMethodCalls(body, content);
        }

        int startLine = methodNode.getStartPoint().getRow() + 1;
        int endLine = methodNode.getEndPoint().getRow() + 1;

        return new CodeMetadata.MethodInfo(
                methodName,
                returnType,
                parameters,
                methodBody,
                startLine,
                endLine,
                callees
        );
    }

    private List<String> extractMethodCalls(Node node, String content) {
        List<String> calls = new ArrayList<>();
        extractMethodCallsRecursive(node, content, calls);
        return calls;
    }

    private void extractMethodCallsRecursive(Node node, String content, List<String> calls) {
        if (node == null) {
            return;
        }

        // Look for method_invocation nodes
        if ("method_invocation".equals(node.getType())) {
            Node nameNode = findFirstChild(node, "identifier");
            if (nameNode != null) {
                String methodName = getNodeText(nameNode, content);
                calls.add(methodName);
            }
        }

        // Recursively check all children
        for (int i = 0; i < node.getChildCount(); i++) {
            extractMethodCallsRecursive(node.getChild(i), content, calls);
        }
    }

    private Node findFirstChild(Node parent, String type) {
        if (parent == null) {
            return null;
        }

        for (int i = 0; i < parent.getChildCount(); i++) {
            Node child = parent.getChild(i);
            if (type.equals(child.getType())) {
                return child;
            }
        }

        // Search recursively in children
        for (int i = 0; i < parent.getChildCount(); i++) {
            Node found = findFirstChild(parent.getChild(i), type);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private String getNodeText(Node node, String content) {
        int startByte = node.getStartByte();
        int endByte = node.getEndByte();
        return content.substring(startByte, endByte);
    }

    @Override
    public void close() {
        synchronized (parserCache) {
            parserCache.values().forEach(Parser::close);
            parserCache.clear();
        }
    }
}
