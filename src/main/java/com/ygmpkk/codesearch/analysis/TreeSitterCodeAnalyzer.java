package com.ygmpkk.codesearch.analysis;

import ch.usi.si.seart.treesitter.Language;
import ch.usi.si.seart.treesitter.Languages;
import ch.usi.si.seart.treesitter.Node;
import ch.usi.si.seart.treesitter.Parser;
import ch.usi.si.seart.treesitter.Tree;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Performs source code analysis using tree-sitter to produce structured chunks
 * for vector indexing and call graph information for the graph database.
 */
public final class TreeSitterCodeAnalyzer {
    private static final int MAX_TOKENS_PER_CHUNK = 32_768;
    private static final String RELATION_CALLS = "calls";

    private final Parser parser;
    private final Map<String, Language> supportedLanguages;

    public TreeSitterCodeAnalyzer() {
        this.parser = new Parser();
        this.supportedLanguages = Map.of(
                "java", Languages.JAVA()
        );
    }

    /**
     * Analyse a file and produce code chunks and call graph relationships.
     */
    public CodeFileAnalysis analyse(Path file, String source) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(source, "source");

        String extension = getExtension(file);
        Language language = supportedLanguages.get(extension);
        if (language == null) {
            return fallbackAnalysis(file, source);
        }

        parser.setLanguage(language);

        Tree tree = parser.parse(source.getBytes(StandardCharsets.UTF_8));
        Node rootNode = tree.getRootNode();

        String packageName = extractPackageName(rootNode, source);

        Map<String, GraphNode> nodes = new HashMap<>();
        Set<GraphEdge> edges = new LinkedHashSet<>();
        List<CodeChunk> chunks = new ArrayList<>();

        analyseRoot(rootNode, file, source, packageName, nodes, edges, chunks);

        if (chunks.isEmpty()) {
            // Fall back to chunking the entire file to ensure coverage.
            return fallbackAnalysis(file, source);
        }

        return new CodeFileAnalysis(
                List.copyOf(chunks),
                new ArrayList<>(nodes.values()),
                List.copyOf(edges)
        );
    }

    private void analyseRoot(Node rootNode,
                              Path file,
                              String source,
                              String packageName,
                              Map<String, GraphNode> nodes,
                              Set<GraphEdge> edges,
                              List<CodeChunk> chunks) {
        int namedChildCount = rootNode.getNamedChildCount();
        for (int i = 0; i < namedChildCount; i++) {
            Node child = rootNode.getNamedChild(i);
            switch (child.getType()) {
                case "class_declaration":
                case "interface_declaration":
                case "enum_declaration":
                case "record_declaration":
                    analyseTypeDeclaration(child, file, source, packageName, nodes, edges, chunks);
                    break;
                default:
                    break;
            }
        }
    }

    private void analyseTypeDeclaration(Node typeNode,
                                        Path file,
                                        String source,
                                        String packageName,
                                        Map<String, GraphNode> nodes,
                                        Set<GraphEdge> edges,
                                        List<CodeChunk> chunks) {
        Node nameNode = typeNode.getChildByFieldName("name");
        String className = nameNode != null ? slice(source, nameNode) : "";
        String qualifiedClassName = buildQualifiedName(packageName, className);

        String classNodeId = "class:" + qualifiedClassName;
        nodes.putIfAbsent(classNodeId, new GraphNode(classNodeId, "class", qualifiedClassName, file.toString()));

        Node bodyNode = findBodyNode(typeNode);
        if (bodyNode == null) {
            return;
        }

        List<String> properties = collectFieldNames(bodyNode, source);

        int bodyChildren = bodyNode.getNamedChildCount();
        for (int i = 0; i < bodyChildren; i++) {
            Node member = bodyNode.getNamedChild(i);
            switch (member.getType()) {
                case "method_declaration":
                case "constructor_declaration":
                    analyseMethod(member, file, source, packageName, className, qualifiedClassName,
                            properties, nodes, edges, chunks, classNodeId);
                    break;
                case "class_declaration":
                case "interface_declaration":
                case "enum_declaration":
                case "record_declaration":
                    analyseTypeDeclaration(member, file, source, packageName, nodes, edges, chunks);
                    break;
                default:
                    break;
            }
        }
    }

    private void analyseMethod(Node methodNode,
                               Path file,
                               String source,
                               String packageName,
                               String className,
                               String qualifiedClassName,
                               List<String> properties,
                               Map<String, GraphNode> nodes,
                               Set<GraphEdge> edges,
                               List<CodeChunk> chunks,
                               String classNodeId) {
        Node nameNode = methodNode.getChildByFieldName("name");
        if (nameNode == null) {
            return;
        }

        Node parametersNode = methodNode.getChildByFieldName("parameters");
        Node bodyNode = methodNode.getChildByFieldName("body");
        if (bodyNode == null) {
            return;
        }

        String methodName = slice(source, nameNode);
        String parameterText = parametersNode != null ? slice(source, parametersNode) : "";
        int parameterCount = estimateParameterCount(parameterText);
        String methodId = "method:" + buildQualifiedName(packageName, className) + "#" + methodName + "/" + parameterCount;

        nodes.putIfAbsent(methodId, new GraphNode(methodId, "method", methodName, file.toString()));
        edges.add(new GraphEdge(classNodeId, methodId, "contains"));

        String bodyText = slice(source, bodyNode);
        List<String> chunkContents = chunkByTokenLimit(bodyText);

        for (int i = 0; i < chunkContents.size(); i++) {
            String chunkText = chunkContents.get(i);
            int tokenCount = countTokens(chunkText);
            String chunkId = methodId + ":" + (i + 1);
            chunks.add(new CodeChunk(
                    chunkId,
                    file,
                    packageName,
                    className,
                    qualifiedClassName,
                    properties,
                    methodName,
                    methodName + parameterText,
                    chunkText,
                    tokenCount
            ));
        }

        collectMethodCallEdges(bodyNode, source, methodId, nodes, edges);
    }

    private void collectMethodCallEdges(Node bodyNode,
                                        String source,
                                        String callerId,
                                        Map<String, GraphNode> nodes,
                                        Set<GraphEdge> edges) {
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(bodyNode);

        while (!stack.isEmpty()) {
            Node current = stack.pop();
            if ("method_invocation".equals(current.getType()) || "super_method_invocation".equals(current.getType())) {
                Node calleeNode = current.getChildByFieldName("name");
                if (calleeNode == null && "method_invocation".equals(current.getType())) {
                    calleeNode = findIdentifierChild(current);
                }
                if (calleeNode != null) {
                    String calleeName = slice(source, calleeNode);
                    if (!calleeName.isBlank()) {
                        String calleeId = "method:" + calleeName;
                        nodes.putIfAbsent(calleeId, new GraphNode(calleeId, "method", calleeName, ""));
                        edges.add(new GraphEdge(callerId, calleeId, RELATION_CALLS));
                    }
                }
            }

            int namedChildren = current.getNamedChildCount();
            for (int i = 0; i < namedChildren; i++) {
                stack.push(current.getNamedChild(i));
            }
        }
    }

    private Node findIdentifierChild(Node node) {
        int namedChildren = node.getNamedChildCount();
        for (int i = 0; i < namedChildren; i++) {
            Node child = node.getNamedChild(i);
            if ("identifier".equals(child.getType())) {
                return child;
            }
        }
        return null;
    }

    private Node findBodyNode(Node typeNode) {
        int namedChildCount = typeNode.getNamedChildCount();
        for (int i = 0; i < namedChildCount; i++) {
            Node child = typeNode.getNamedChild(i);
            String type = child.getType();
            if (type.endsWith("_body") || "class_body".equals(type)) {
                return child;
            }
        }
        return null;
    }

    private List<String> collectFieldNames(Node bodyNode, String source) {
        Set<String> names = new LinkedHashSet<>();
        int childCount = bodyNode.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            Node member = bodyNode.getNamedChild(i);
            if (!"field_declaration".equals(member.getType())) {
                continue;
            }
            int declarationChildren = member.getNamedChildCount();
            for (int j = 0; j < declarationChildren; j++) {
                Node declChild = member.getNamedChild(j);
                if ("variable_declarator".equals(declChild.getType())) {
                    Node identifierNode = declChild.getChildByFieldName("name");
                    if (identifierNode == null) {
                        identifierNode = findIdentifierChild(declChild);
                    }
                    if (identifierNode != null) {
                        names.add(slice(source, identifierNode));
                    }
                }
            }
        }
        return new ArrayList<>(names);
    }

    private CodeFileAnalysis fallbackAnalysis(Path file, String source) {
        List<String> chunks = chunkByTokenLimit(source);
        List<CodeChunk> codeChunks = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String content = chunks.get(i);
            codeChunks.add(new CodeChunk(
                    file.toString() + ":" + (i + 1),
                    file,
                    "",
                    "",
                    "",
                    Collections.emptyList(),
                    "",
                    "",
                    content,
                    countTokens(content)
            ));
        }
        return new CodeFileAnalysis(codeChunks, List.of(), List.of());
    }

    private int estimateParameterCount(String parameterText) {
        if (parameterText == null || parameterText.isBlank()) {
            return 0;
        }
        String trimmed = parameterText.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        trimmed = trimmed.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }

        int depth = 0;
        int count = 1;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            switch (c) {
                case '<':
                case '(':
                case '[':
                    depth++;
                    break;
                case '>':
                case ')':
                case ']':
                    depth = Math.max(0, depth - 1);
                    break;
                case ',':
                    if (depth == 0) {
                        count++;
                    }
                    break;
                default:
                    break;
            }
        }
        return count;
    }

    private List<String> chunkByTokenLimit(String code) {
        if (code.isBlank()) {
            return List.of(code);
        }

        List<String> chunks = new ArrayList<>();
        int length = code.length();
        int chunkStart = 0;
        int index = 0;
        int tokenCount = 0;

        while (index < length) {
            char current = code.charAt(index);
            if (!Character.isWhitespace(current)) {
                int tokenEnd = index + 1;
                if (Character.isLetterOrDigit(current) || current == '_') {
                    while (tokenEnd < length) {
                        char c = code.charAt(tokenEnd);
                        if (Character.isLetterOrDigit(c) || c == '_') {
                            tokenEnd++;
                        } else {
                            break;
                        }
                    }
                }
                tokenCount++;
                index = tokenEnd;
            } else {
                index++;
            }

            if (tokenCount >= MAX_TOKENS_PER_CHUNK) {
                chunks.add(code.substring(chunkStart, index));
                chunkStart = index;
                tokenCount = 0;
            }
        }

        if (chunkStart < length) {
            chunks.add(code.substring(chunkStart));
        }

        return chunks.isEmpty() ? List.of(code) : chunks;
    }

    private int countTokens(String code) {
        if (code == null || code.isBlank()) {
            return 0;
        }
        int tokens = 0;
        int index = 0;
        while (index < code.length()) {
            char current = code.charAt(index);
            if (!Character.isWhitespace(current)) {
                tokens++;
                if (Character.isLetterOrDigit(current) || current == '_') {
                    while (++index < code.length()) {
                        char c = code.charAt(index);
                        if (!(Character.isLetterOrDigit(c) || c == '_')) {
                            break;
                        }
                    }
                } else {
                    index++;
                }
            } else {
                index++;
            }
        }
        return tokens;
    }

    private String slice(String source, Node node) {
        int start = Math.max(0, (int) node.getStartByte());
        int end = Math.max(start, (int) node.getEndByte());
        if (start >= source.length()) {
            return "";
        }
        end = Math.min(source.length(), end);
        return source.substring(start, end);
    }

    private String extractPackageName(Node rootNode, String source) {
        int childCount = rootNode.getNamedChildCount();
        for (int i = 0; i < childCount; i++) {
            Node child = rootNode.getNamedChild(i);
            if ("package_declaration".equals(child.getType())) {
                Node nameNode = child.getChildByFieldName("name");
                if (nameNode == null) {
                    nameNode = findIdentifierChild(child);
                }
                if (nameNode != null) {
                    return slice(source, nameNode).trim();
                }
            }
        }
        return "";
    }

    private String getExtension(Path file) {
        String name = file.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex == -1) {
            return "";
        }
        return name.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String buildQualifiedName(String packageName, String simpleName) {
        if (packageName == null || packageName.isBlank()) {
            return simpleName != null ? simpleName : "";
        }
        if (simpleName == null || simpleName.isBlank()) {
            return packageName;
        }
        return packageName + "." + simpleName;
    }
}

