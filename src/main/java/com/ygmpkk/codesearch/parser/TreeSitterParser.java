package com.ygmpkk.codesearch.parser;

import ch.usi.si.seart.treesitter.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for source code using tree-sitter
 * Extracts package, class, method, and call chain information
 */
public class TreeSitterParser implements AutoCloseable {
    private static final Logger logger = LogManager.getLogger(TreeSitterParser.class);

    private final Language language;
    private final Parser parser;

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
        // Initialize tree-sitter for Java (default for backward compatibility)
        this(Language.JAVA);
    }
    
    /**
     * Create a parser for a specific language
     * 
     * @param language the tree-sitter language to use for parsing
     */
    public TreeSitterParser(Language language) {
        this.language = language;
        this.parser = Parser.getFor(language);
    }

    /**
     * Parse a Java source file and extract metadata
     * @deprecated Use {@link #parseFile(Path)} instead
     */
    @Deprecated
    public CodeMetadata parseJavaFile(Path filePath) throws Exception {
        String content = Files.readString(filePath);
        return parseJavaCode(filePath.toString(), content);
    }
    
    /**
     * Parse a source file and extract metadata
     * Works with any language supported by tree-sitter
     */
    public CodeMetadata parseFile(Path filePath) throws Exception {
        String content = Files.readString(filePath);
        return parseCode(filePath.toString(), content);
    }

    /**
     * Parse Java source code and extract metadata
     * @deprecated Use {@link #parseCode(String, String)} instead
     */
    @Deprecated
    public CodeMetadata parseJavaCode(String filePath, String content) throws Exception {
        return parseCode(filePath, content);
    }
    
    /**
     * Parse source code and extract metadata
     * Automatically adapts to the language specified in the constructor
     */
    public CodeMetadata parseCode(String filePath, String content) throws Exception {
        try (Tree tree = parser.parse(content)) {
            Node root = tree.getRootNode();

            String packageName = extractPackageName(root, content);
            String className = extractClassName(root, content);
            List<String> properties = extractProperties(root, content);
            List<CodeMetadata.MethodInfo> methods = extractMethods(root, content);

            return new CodeMetadata(filePath, packageName, className, properties, methods);
        }
    }

    private String extractPackageName(Node root, String content) {
        // Java, Kotlin
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
        
        // Python - module name could be extracted from imports but typically not in AST
        // Go - package declaration
        packageNode = findFirstChild(root, "package_clause");
        if (packageNode != null) {
            Node identifier = findFirstChild(packageNode, "package_identifier");
            if (identifier != null) {
                return getNodeText(identifier, content);
            }
        }
        
        return "";
    }

    private String extractClassName(Node root, String content) {
        // Java, Kotlin - class_declaration, interface_declaration, enum_declaration
        Node classNode = findFirstChild(root, "class_declaration");
        if (classNode == null) {
            classNode = findFirstChild(root, "interface_declaration");
        }
        if (classNode == null) {
            classNode = findFirstChild(root, "enum_declaration");
        }
        if (classNode == null) {
            classNode = findFirstChild(root, "object_declaration"); // Kotlin
        }
        
        // Python - class_definition
        if (classNode == null) {
            classNode = findFirstChild(root, "class_definition");
        }
        
        // TypeScript/JavaScript - class_declaration
        if (classNode == null) {
            classNode = findFirstChild(root, "class_declaration");
        }
        
        // Go - type_declaration with type_spec
        if (classNode == null) {
            Node typeDecl = findFirstChild(root, "type_declaration");
            if (typeDecl != null) {
                classNode = findFirstChild(typeDecl, "type_spec");
            }
        }
        
        // C++/C# - class_specifier
        if (classNode == null) {
            classNode = findFirstChild(root, "class_specifier");
        }
        
        // Rust - struct_item, enum_item, trait_item
        if (classNode == null) {
            classNode = findFirstChild(root, "struct_item");
        }
        if (classNode == null) {
            classNode = findFirstChild(root, "enum_item");
        }
        if (classNode == null) {
            classNode = findFirstChild(root, "trait_item");
        }

        if (classNode != null) {
            Node nameNode = findFirstChild(classNode, "identifier");
            if (nameNode == null) {
                nameNode = findFirstChild(classNode, "type_identifier");
            }
            if (nameNode != null) {
                return getNodeText(nameNode, content);
            }
        }
        return "";
    }

    private List<String> extractProperties(Node root, String content) {
        List<String> properties = new ArrayList<>();

        // Find class/struct body depending on language
        Node classNode = findFirstChild(root, "class_declaration");
        if (classNode == null) {
            classNode = findFirstChild(root, "interface_declaration");
        }
        if (classNode == null) {
            classNode = findFirstChild(root, "class_definition"); // Python
        }
        if (classNode == null) {
            classNode = findFirstChild(root, "struct_item"); // Rust
        }
        if (classNode == null) {
            classNode = findFirstChild(root, "class_specifier"); // C++
        }

        if (classNode != null) {
            // Java/Kotlin/TypeScript - class_body
            Node classBody = findFirstChild(classNode, "class_body");
            if (classBody == null) {
                classBody = findFirstChild(classNode, "declaration_list"); // Python
            }
            if (classBody == null) {
                classBody = findFirstChild(classNode, "field_declaration_list"); // Rust
            }
            if (classBody == null) {
                classBody = findFirstChild(classNode, "field_declaration_list"); // C++
            }
            
            if (classBody != null) {
                for (int i = 0; i < classBody.getChildCount(); i++) {
                    Node child = classBody.getChild(i);
                    String childType = child.getType();
                    
                    // Java/Kotlin
                    if ("field_declaration".equals(childType)) {
                        Node declarator = findFirstChild(child, "variable_declarator");
                        if (declarator != null) {
                            Node nameNode = findFirstChild(declarator, "identifier");
                            if (nameNode != null) {
                                properties.add(getNodeText(nameNode, content));
                            }
                        }
                    }
                    // Python - assignment in class
                    else if ("expression_statement".equals(childType)) {
                        Node assignment = findFirstChild(child, "assignment");
                        if (assignment != null) {
                            Node left = findFirstChild(assignment, "identifier");
                            if (left != null) {
                                properties.add(getNodeText(left, content));
                            }
                        }
                    }
                    // Rust - field_declaration
                    else if ("field_declaration".equals(childType)) {
                        Node nameNode = findFirstChild(child, "field_identifier");
                        if (nameNode != null) {
                            properties.add(getNodeText(nameNode, content));
                        }
                    }
                }
            }
        }

        return properties;
    }

    private List<CodeMetadata.MethodInfo> extractMethods(Node root, String content) {
        List<CodeMetadata.MethodInfo> methods = new ArrayList<>();

        // Find class body depending on language
        Node classNode = findFirstChild(root, "class_declaration");
        if (classNode == null) {
            classNode = findFirstChild(root, "interface_declaration");
        }
        if (classNode == null) {
            classNode = findFirstChild(root, "class_definition"); // Python
        }
        if (classNode == null) {
            classNode = findFirstChild(root, "impl_item"); // Rust
        }

        if (classNode != null) {
            Node classBody = findFirstChild(classNode, "class_body");
            if (classBody == null) {
                classBody = findFirstChild(classNode, "declaration_list"); // Python, Rust
            }
            if (classBody == null) {
                classBody = classNode; // For impl_item in Rust
            }
            
            if (classBody != null) {
                for (int i = 0; i < classBody.getChildCount(); i++) {
                    Node child = classBody.getChild(i);
                    String childType = child.getType();
                    
                    // Java/Kotlin/TypeScript
                    if ("method_declaration".equals(childType) ||
                            "constructor_declaration".equals(childType)) {
                        CodeMetadata.MethodInfo methodInfo = extractMethodInfo(child, content);
                        if (methodInfo != null) {
                            methods.add(methodInfo);
                        }
                    }
                    // Python - function_definition
                    else if ("function_definition".equals(childType)) {
                        CodeMetadata.MethodInfo methodInfo = extractMethodInfo(child, content);
                        if (methodInfo != null) {
                            methods.add(methodInfo);
                        }
                    }
                    // Rust - function_item
                    else if ("function_item".equals(childType)) {
                        CodeMetadata.MethodInfo methodInfo = extractMethodInfo(child, content);
                        if (methodInfo != null) {
                            methods.add(methodInfo);
                        }
                    }
                    // Go - function_declaration, method_declaration
                    else if ("function_declaration".equals(childType)) {
                        CodeMetadata.MethodInfo methodInfo = extractMethodInfo(child, content);
                        if (methodInfo != null) {
                            methods.add(methodInfo);
                        }
                    }
                }
            }
        } else {
            // For languages without explicit class structure (like Go, Rust modules)
            // Extract top-level functions
            for (int i = 0; i < root.getChildCount(); i++) {
                Node child = root.getChild(i);
                String childType = child.getType();
                
                if ("function_declaration".equals(childType) || // Go
                        "function_item".equals(childType) || // Rust
                        "function_definition".equals(childType)) { // Python
                    CodeMetadata.MethodInfo methodInfo = extractMethodInfo(child, content);
                    if (methodInfo != null) {
                        methods.add(methodInfo);
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

        // Extract method name - works for most languages
        Node nameNode = findFirstChild(methodNode, "identifier");
        if (nameNode == null) {
            nameNode = findFirstChild(methodNode, "field_identifier"); // Rust
        }
        if (nameNode != null) {
            methodName = getNodeText(nameNode, content);
        }

        // Extract return type (language-specific)
        // Java/Kotlin/TypeScript
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
        // Python - type annotation
        if (typeNode == null) {
            Node typeAnnotation = findFirstChild(methodNode, "type");
            if (typeAnnotation != null) {
                typeNode = typeAnnotation;
            }
        }
        // Rust - return type
        if (typeNode == null) {
            Node returnTypeNode = findFirstChild(methodNode, "primitive_type");
            if (returnTypeNode != null) {
                typeNode = returnTypeNode;
            }
        }
        if (typeNode != null) {
            returnType = getNodeText(typeNode, content);
        }

        // Extract parameters
        // Java/Kotlin/TypeScript - formal_parameters
        Node formalParams = findFirstChild(methodNode, "formal_parameters");
        // Python - parameters
        if (formalParams == null) {
            formalParams = findFirstChild(methodNode, "parameters");
        }
        // Rust - parameters
        if (formalParams == null) {
            formalParams = findFirstChild(methodNode, "parameters");
        }
        
        if (formalParams != null) {
            for (int i = 0; i < formalParams.getChildCount(); i++) {
                Node child = formalParams.getChild(i);
                String childType = child.getType();
                
                // Java/Kotlin/TypeScript
                if ("formal_parameter".equals(childType)) {
                    Node paramName = findFirstChild(child, "identifier");
                    if (paramName != null) {
                        parameters.add(getNodeText(paramName, content));
                    }
                }
                // Python - identifier
                else if ("identifier".equals(childType)) {
                    parameters.add(getNodeText(child, content));
                }
                // Rust - parameter
                else if ("parameter".equals(childType)) {
                    Node paramName = findFirstChild(child, "identifier");
                    if (paramName != null) {
                        parameters.add(getNodeText(paramName, content));
                    }
                }
            }
        }

        // Extract method body and find method calls
        Node body = findFirstChild(methodNode, "block");
        if (body == null) {
            body = findFirstChild(methodNode, "body"); // Alternative for some languages
        }
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

        String nodeType = node.getType();
        
        // Java/Kotlin/TypeScript - method_invocation
        if ("method_invocation".equals(nodeType)) {
            Node nameNode = findFirstChild(node, "identifier");
            if (nameNode != null) {
                String methodName = getNodeText(nameNode, content);
                calls.add(methodName);
            }
        }
        // Python - call
        else if ("call".equals(nodeType)) {
            Node function = findFirstChild(node, "identifier");
            if (function == null) {
                function = findFirstChild(node, "attribute");
            }
            if (function != null) {
                calls.add(getNodeText(function, content));
            }
        }
        // Rust - call_expression
        else if ("call_expression".equals(nodeType)) {
            Node function = findFirstChild(node, "identifier");
            if (function == null) {
                function = findFirstChild(node, "field_expression");
            }
            if (function != null) {
                calls.add(getNodeText(function, content));
            }
        }
        // Go - call_expression
        else if ("call_expression".equals(nodeType)) {
            Node function = findFirstChild(node, "identifier");
            if (function == null) {
                function = findFirstChild(node, "selector_expression");
            }
            if (function != null) {
                calls.add(getNodeText(function, content));
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
        if (parser != null) {
            parser.close();
        }
    }
}
