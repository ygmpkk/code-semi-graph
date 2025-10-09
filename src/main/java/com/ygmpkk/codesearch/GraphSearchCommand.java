package com.ygmpkk.codesearch;

import com.ygmpkk.codesearch.db.ArcadeDBGraphDatabase;
import com.ygmpkk.codesearch.db.GraphDatabase;
import com.ygmpkk.codesearch.parser.CodeMetadata;
import com.ygmpkk.codesearch.parser.TreeSitterParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * Command for performing graph code search
 */
@Command(
    name = "graph",
    description = "Perform graph code search",
    mixinStandardHelpOptions = true
)
public class GraphSearchCommand implements Callable<Integer> {
    private static Logger logger = LogManager.getLogger(GraphSearchCommand.class);

    @Mixin
    LoggingMixin loggingMixin;

    @Parameters(
        index = "0",
        description = "Search query or node identifier"
    )
    private String query;

    @Option(
        names = {"-p", "--path"},
        description = "Path to search in (default: current directory)"
    )
    private String path = ".";

    @Option(
        names = {"-t", "--traversal"},
        description = "Graph traversal type: BFS or DFS (default: BFS)"
    )
    private String traversalType = "BFS";

    @Option(
        names = {"-m", "--max-nodes"},
        description = "Maximum number of nodes to visit"
    )
    private Integer maxNodes;

    @Option(
        names = {"-r", "--relationship"},
        description = "Filter by relationship type",
        split = ","
    )
    private String[] relationships;
    
    @Option(
        names = {"--db-path"},
        description = "Path to graph database (default: ./.code-index/arcadedb-graph)"
    )
    private String dbPath = "./.code-index/arcadedb-graph";

    @Override
    public Integer call() {
        logger.info("Performing graph code search...");
        logger.info("Query: {}", query);
        logger.info("Path: {}", path);
        logger.info("Traversal type: {}", traversalType);
        logger.info("Database path: {}", dbPath);
        
        if (maxNodes != null) {
            logger.info("Max nodes: {}", maxNodes);
        }
        
        if (relationships != null && relationships.length > 0) {
            logger.info("Relationships: {}", String.join(", ", relationships));
        }
        
        try {
            Path sourceRoot = Paths.get(path).toAbsolutePath().normalize();
            if (!Files.exists(sourceRoot)) {
                logger.error("Source path does not exist: {}", sourceRoot);
                return 1;
            }

            Path graphDbPath = Paths.get(dbPath);

            try (GraphDatabase graphDb = new ArcadeDBGraphDatabase(dbPath)) {
                graphDb.initialize();

                logger.info("");
                if (Files.exists(graphDbPath)) {
                    logger.info("Updating graph database with call chains from: {}", sourceRoot);
                } else {
                    logger.info("Creating graph database at {} from source: {}", graphDbPath, sourceRoot);
                }

                buildGraphFromSource(graphDb, sourceRoot);

                // Find the starting node
                GraphDatabase.Node startNode = graphDb.findNodeByName(query);

                if (startNode == null) {
                    logger.info("");
                    logger.info("Node not found: {}", query);
                    logger.info("Ensure the symbol exists in the parsed sources under {}", sourceRoot);
                    return 0;
                }

                logger.info("");
                logger.info("Found starting node: {} ({})", startNode.getName(), startNode.getNodeType());
                logger.info("File: {}", startNode.getFilePath());

                // Set up relationship type filter
                Set<String> relationshipFilter = null;
                if (relationships != null && relationships.length > 0) {
                    relationshipFilter = new HashSet<>(Arrays.asList(relationships));
                }

                // Perform traversal
                int depth = maxNodes != null ? maxNodes : 10;
                List<GraphDatabase.Node> traversedNodes;

                logger.info("");
                logger.info("Traversing graph using {}...", traversalType);

                if ("DFS".equalsIgnoreCase(traversalType)) {
                    traversedNodes = graphDb.traverseDFS(startNode.getNodeId(), depth, relationshipFilter);
                } else {
                    traversedNodes = graphDb.traverseBFS(startNode.getNodeId(), depth, relationshipFilter);
                }

                // Display results
                logger.info("");
                logger.info("Graph traversal results ({} nodes):", traversedNodes.size());
                logger.info("");

                for (int i = 0; i < traversedNodes.size(); i++) {
                    GraphDatabase.Node node = traversedNodes.get(i);
                    logger.info("  {}. {} ({}) - {}",
                        i + 1,
                        node.getName(),
                        node.getNodeType(),
                        node.getFilePath());
                }

                // Display graph statistics
                logger.info("");
                logger.info("Graph statistics:");
                logger.info("  Total nodes: {}", graphDb.getNodeCount());
                logger.info("  Total edges: {}", graphDb.getEdgeCount());
            }

            logger.info("");
            logger.info("✓ Graph search completed successfully");

        } catch (IllegalStateException e) {
            logger.error("Tree-sitter parsing unavailable: {}", e.getMessage());
            logger.debug("Stack trace:", e);
            return 1;
        } catch (Exception e) {
            logger.error("Error performing graph search: {}", e.getMessage());
            logger.debug("Stack trace:", e);
            return 1;
        }

        return 0;
    }

    private void buildGraphFromSource(GraphDatabase graphDb, Path sourceRoot) throws Exception {
        List<Path> javaFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.filter(Files::isRegularFile)
                .filter(file -> file.toString().endsWith(".java"))
                .forEach(javaFiles::add);
        }

        if (javaFiles.isEmpty()) {
            logger.warn("No Java source files found under {}. Skipping call graph construction.", sourceRoot);
            return;
        }

        int classCount = 0;
        int methodCount = 0;
        int callCount = 0;

        try (TreeSitterParser parser = new TreeSitterParser()) {
            for (Path file : javaFiles) {
                CodeMetadata metadata = parser.parseJavaFile(file);

                if (metadata.className() == null || metadata.className().isBlank()) {
                    continue;
                }

                String filePath = relativizePath(sourceRoot, file);
                String classNodeId = filePath + ":" + metadata.className();
                graphDb.addNode(classNodeId, "class", metadata.className(), filePath);
                classCount++;

                List<CodeMetadata.MethodInfo> methods = metadata.methods();
                if (methods == null) {
                    continue;
                }

                for (CodeMetadata.MethodInfo method : methods) {
                    if (method == null || method.name() == null || method.name().isBlank()) {
                        continue;
                    }

                    String methodNodeId = classNodeId + ":" + method.name();
                    graphDb.addNode(methodNodeId, "method", method.name(), filePath);
                    graphDb.addEdge(classNodeId, methodNodeId, "contains");
                    methodCount++;

                    List<String> callees = method.callees();
                    if (callees == null) {
                        continue;
                    }

                    for (String callee : callees) {
                        if (callee == null || callee.isBlank()) {
                            continue;
                        }

                        String calleeNodeId = classNodeId + ":" + callee;
                        graphDb.addNode(calleeNodeId, "method", callee, filePath);
                        graphDb.addEdge(methodNodeId, calleeNodeId, "calls");
                        callCount++;
                    }
                }
            }
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            throw new IllegalStateException("Tree-sitter native libraries are not available", e);
        }

        logger.info("Parsed {} classes, {} methods, and {} call relationships from {}", classCount, methodCount, callCount, sourceRoot);
        logger.info("Graph now contains {} nodes and {} edges", graphDb.getNodeCount(), graphDb.getEdgeCount());
    }

    private String relativizePath(Path root, Path file) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedFile = file.toAbsolutePath().normalize();

        if (normalizedFile.startsWith(normalizedRoot)) {
            return normalizedRoot.relativize(normalizedFile).toString();
        }

        return normalizedFile.toString();
    }
}
