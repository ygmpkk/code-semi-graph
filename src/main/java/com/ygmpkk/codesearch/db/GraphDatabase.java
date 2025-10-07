package com.ygmpkk.codesearch.db;

import java.util.List;
import java.util.Set;

/**
 * Interface for graph database operations
 * Used to store and query code relationships (e.g., class inheritance, method calls, imports)
 */
public interface GraphDatabase extends AutoCloseable {
    /**
     * Initialize the database schema
     */
    void initialize() throws Exception;
    
    /**
     * Add a node to the graph
     * @param nodeId Unique identifier for the node
     * @param nodeType Type of the node (e.g., "class", "method", "function")
     * @param name Name of the node
     * @param filePath Path to the file containing this node
     */
    void addNode(String nodeId, String nodeType, String name, String filePath) throws Exception;
    
    /**
     * Add an edge (relationship) between two nodes
     * @param fromNodeId Source node ID
     * @param toNodeId Target node ID
     * @param relationshipType Type of relationship (e.g., "calls", "extends", "imports")
     */
    void addEdge(String fromNodeId, String toNodeId, String relationshipType) throws Exception;
    
    /**
     * Find a node by name
     * @param name Name to search for
     * @return Node if found, null otherwise
     */
    Node findNodeByName(String name) throws Exception;
    
    /**
     * Get all nodes connected to a given node
     * @param nodeId Node to start from
     * @param relationshipType Optional filter by relationship type (null for all)
     * @param direction Direction to traverse ("OUTGOING", "INCOMING", or "BOTH")
     * @return List of connected nodes
     */
    List<Node> getConnectedNodes(String nodeId, String relationshipType, String direction) throws Exception;
    
    /**
     * Traverse the graph using BFS
     * @param startNodeId Starting node
     * @param maxDepth Maximum depth to traverse
     * @param relationshipTypes Optional filter by relationship types
     * @return List of nodes in BFS order
     */
    List<Node> traverseBFS(String startNodeId, int maxDepth, Set<String> relationshipTypes) throws Exception;
    
    /**
     * Traverse the graph using DFS
     * @param startNodeId Starting node
     * @param maxDepth Maximum depth to traverse
     * @param relationshipTypes Optional filter by relationship types
     * @return List of nodes in DFS order
     */
    List<Node> traverseDFS(String startNodeId, int maxDepth, Set<String> relationshipTypes) throws Exception;
    
    /**
     * Get the total number of nodes in the graph
     * @return Count of nodes
     */
    int getNodeCount() throws Exception;
    
    /**
     * Get the total number of edges in the graph
     * @return Count of edges
     */
    int getEdgeCount() throws Exception;
    
    /**
     * Represents a node in the graph
     */
    class Node {
        private final String nodeId;
        private final String nodeType;
        private final String name;
        private final String filePath;
        
        public Node(String nodeId, String nodeType, String name, String filePath) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.name = name;
            this.filePath = filePath;
        }
        
        public String getNodeId() {
            return nodeId;
        }
        
        public String getNodeType() {
            return nodeType;
        }
        
        public String getName() {
            return name;
        }
        
        public String getFilePath() {
            return filePath;
        }
    }
    
    /**
     * Represents an edge in the graph
     */
    class Edge {
        private final String fromNodeId;
        private final String toNodeId;
        private final String relationshipType;
        
        public Edge(String fromNodeId, String toNodeId, String relationshipType) {
            this.fromNodeId = fromNodeId;
            this.toNodeId = toNodeId;
            this.relationshipType = relationshipType;
        }
        
        public String getFromNodeId() {
            return fromNodeId;
        }
        
        public String getToNodeId() {
            return toNodeId;
        }
        
        public String getRelationshipType() {
            return relationshipType;
        }
    }
}
