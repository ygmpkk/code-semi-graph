package com.ygmpkk.codesearch;

import com.ygmpkk.codesearch.db.GraphDatabase;
import com.ygmpkk.codesearch.db.SqliteGraphDatabase;
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
        description = "Path to graph database (default: ./.code-index/graph.db)"
    )
    private String dbPath = "./.code-index/graph.db";

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
            Path graphDbPath = Paths.get(dbPath);
            
            // Check if database exists, if not create sample data
            boolean dbExists = Files.exists(graphDbPath);
            
            try (GraphDatabase graphDb = new SqliteGraphDatabase(dbPath)) {
                if (!dbExists) {
                    logger.info("");
                    logger.info("Graph database not found. Creating with sample data...");
                    graphDb.initialize();
                    createSampleGraphData(graphDb);
                } else {
                    logger.info("");
                    logger.info("Using existing graph database");
                }
                
                // Find the starting node
                GraphDatabase.Node startNode = graphDb.findNodeByName(query);
                
                if (startNode == null) {
                    logger.info("");
                    logger.info("Node not found: {}", query);
                    logger.info("Try one of the sample nodes: MyClass, MyInterface, processData, or DatabaseHelper");
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
            
        } catch (Exception e) {
            logger.error("Error performing graph search: {}", e.getMessage());
            logger.debug("Stack trace:", e);
            return 1;
        }
        
        return 0;
    }
    
    /**
     * Create sample graph data for demonstration
     */
    private void createSampleGraphData(GraphDatabase graphDb) throws Exception {
        // Add sample nodes
        graphDb.addNode("class:MyClass", "class", "MyClass", "/src/MyClass.java");
        graphDb.addNode("class:BaseClass", "class", "BaseClass", "/src/BaseClass.java");
        graphDb.addNode("interface:MyInterface", "interface", "MyInterface", "/src/MyInterface.java");
        graphDb.addNode("method:processData", "method", "processData", "/src/MyClass.java");
        graphDb.addNode("method:saveData", "method", "saveData", "/src/MyClass.java");
        graphDb.addNode("class:DatabaseHelper", "class", "DatabaseHelper", "/src/util/DatabaseHelper.java");
        graphDb.addNode("method:connect", "method", "connect", "/src/util/DatabaseHelper.java");
        
        // Add sample edges
        graphDb.addEdge("class:MyClass", "class:BaseClass", "extends");
        graphDb.addEdge("class:MyClass", "interface:MyInterface", "implements");
        graphDb.addEdge("class:MyClass", "method:processData", "contains");
        graphDb.addEdge("class:MyClass", "method:saveData", "contains");
        graphDb.addEdge("method:processData", "method:saveData", "calls");
        graphDb.addEdge("method:saveData", "class:DatabaseHelper", "uses");
        graphDb.addEdge("class:DatabaseHelper", "method:connect", "contains");
        graphDb.addEdge("method:saveData", "method:connect", "calls");
        
        logger.info("Sample graph data created with {} nodes and {} edges", 
            graphDb.getNodeCount(), graphDb.getEdgeCount());
    }
}
