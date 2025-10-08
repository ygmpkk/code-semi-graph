package com.ygmpkk.codesearch.parser;

import ch.usi.si.seart.treesitter.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
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
    
    public TreeSitterParser() {
        // Initialize tree-sitter for Java
        this.language = Language.JAVA;
        this.parser = Parser.getFor(language);
    }
    
    /**
     * Parse a Java source file and extract metadata
     */
    public CodeMetadata parseJavaFile(Path filePath) throws Exception {
        String content = Files.readString(filePath);
        return parseJavaCode(filePath.toString(), content);
    }
    
    /**
     * Parse Java source code and extract metadata
     */
    public CodeMetadata parseJavaCode(String filePath, String content) throws Exception {
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
        if (parser != null) {
            parser.close();
        }
    }
}
