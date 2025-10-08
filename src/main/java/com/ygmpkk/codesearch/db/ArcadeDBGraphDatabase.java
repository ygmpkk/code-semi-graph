package com.ygmpkk.codesearch.db;

import com.arcadedb.database.Database;
import com.arcadedb.database.DatabaseFactory;
import com.arcadedb.graph.MutableVertex;
import com.arcadedb.graph.Vertex;
import com.arcadedb.query.sql.executor.Result;
import com.arcadedb.query.sql.executor.ResultSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * ArcadeDB implementation of GraphDatabase
 * Stores code relationships and performs graph traversals using native graph model
 */
public class ArcadeDBGraphDatabase implements GraphDatabase {
    private static final Logger logger = LogManager.getLogger(ArcadeDBGraphDatabase.class);
    private final Database database;
    private static final String NODE_TYPE = "CodeNode";
    
    public ArcadeDBGraphDatabase(String dbPath) {
        DatabaseFactory factory = new DatabaseFactory(dbPath);
        if (factory.exists()) {
            this.database = factory.open();
            logger.debug("Opened existing ArcadeDB graph database: {}", dbPath);
        } else {
            this.database = factory.create();
            logger.debug("Created new ArcadeDB graph database: {}", dbPath);
        }
    }
    
    @Override
    public void initialize() throws Exception {
        database.transaction(() -> {
            // Create vertex type for nodes if it doesn't exist
            if (!database.getSchema().existsType(NODE_TYPE)) {
                database.getSchema().createVertexType(NODE_TYPE);
                
                // Create properties
                database.getSchema().getType(NODE_TYPE).createProperty("nodeId", String.class);
                database.getSchema().getType(NODE_TYPE).createProperty("nodeType", String.class);
                database.getSchema().getType(NODE_TYPE).createProperty("name", String.class);
                database.getSchema().getType(NODE_TYPE).createProperty("filePath", String.class);
                
                // Create indexes using ArcadeDB schema API
                database.getSchema().createTypeIndex(
                    com.arcadedb.schema.Schema.INDEX_TYPE.LSM_TREE, 
                    true, 
                    NODE_TYPE, 
                    "nodeId"
                );
                database.getSchema().createTypeIndex(
                    com.arcadedb.schema.Schema.INDEX_TYPE.LSM_TREE, 
                    false, 
                    NODE_TYPE, 
                    "name"
                );
                database.getSchema().createTypeIndex(
                    com.arcadedb.schema.Schema.INDEX_TYPE.LSM_TREE, 
                    false, 
                    NODE_TYPE, 
                    "nodeType"
                );
                
                logger.debug("Created {} vertex type with schema", NODE_TYPE);
            }
        });
    }
    
    @Override
    public void addNode(String nodeId, String nodeType, String name, String filePath) throws Exception {
        database.transaction(() -> {
            // Check if vertex already exists
            ResultSet result = database.query("sql", 
                "SELECT FROM " + NODE_TYPE + " WHERE nodeId = ?", nodeId);
            
            if (result.hasNext()) {
                // Update existing vertex
                MutableVertex vertex = result.next().getVertex().get().modify();
                vertex.set("nodeType", nodeType);
                vertex.set("name", name);
                vertex.set("filePath", filePath);
                vertex.save();
            } else {
                // Create new vertex
                MutableVertex vertex = database.newVertex(NODE_TYPE);
                vertex.set("nodeId", nodeId);
                vertex.set("nodeType", nodeType);
                vertex.set("name", name);
                vertex.set("filePath", filePath);
                vertex.save();
            }
            
            logger.debug("Added node: {} ({})", name, nodeType);
        });
    }
    
    @Override
    public void addEdge(String fromNodeId, String toNodeId, String relationshipType) throws Exception {
        database.transaction(() -> {
            // Create edge type if it doesn't exist
            if (!database.getSchema().existsType(relationshipType)) {
                database.getSchema().createEdgeType(relationshipType);
            }
            
            // Find the vertices
            ResultSet fromResult = database.query("sql", 
                "SELECT FROM " + NODE_TYPE + " WHERE nodeId = ?", fromNodeId);
            ResultSet toResult = database.query("sql", 
                "SELECT FROM " + NODE_TYPE + " WHERE nodeId = ?", toNodeId);
            
            if (!fromResult.hasNext() || !toResult.hasNext()) {
                logger.warn("Cannot create edge: one or both nodes not found");
                return;
            }
            
            Vertex fromVertex = fromResult.next().getVertex().get();
            Vertex toVertex = toResult.next().getVertex().get();
            
            // Check if edge already exists
            boolean edgeExists = false;
            for (com.arcadedb.graph.Edge edge : fromVertex.getEdges(Vertex.DIRECTION.OUT, relationshipType)) {
                if (edge.getInVertex().equals(toVertex)) {
                    edgeExists = true;
                    break;
                }
            }
            
            if (!edgeExists) {
                fromVertex.newEdge(relationshipType, toVertex, true);
                logger.debug("Added edge: {} -[{}]-> {}", fromNodeId, relationshipType, toNodeId);
            }
        });
    }
    
    @Override
    public Node findNodeByName(String name) throws Exception {
        ResultSet result = database.query("sql", 
            "SELECT FROM " + NODE_TYPE + " WHERE name = ? LIMIT 1", name);
        
        if (result.hasNext()) {
            Vertex vertex = result.next().getVertex().get();
            return vertexToNode(vertex);
        }
        
        return null;
    }
    
    @Override
    public List<Node> getConnectedNodes(String nodeId, String relationshipType, String direction) throws Exception {
        List<Node> nodes = new ArrayList<>();
        
        // Find the starting vertex
        ResultSet result = database.query("sql", 
            "SELECT FROM " + NODE_TYPE + " WHERE nodeId = ?", nodeId);
        
        if (!result.hasNext()) {
            return nodes;
        }
        
        Vertex startVertex = result.next().getVertex().get();
        Set<String> visitedIds = new HashSet<>();
        
        // Get connected vertices based on direction
        if ("OUTGOING".equals(direction)) {
            Iterable<com.arcadedb.graph.Edge> edges = relationshipType != null 
                ? startVertex.getEdges(Vertex.DIRECTION.OUT, relationshipType)
                : startVertex.getEdges(Vertex.DIRECTION.OUT);
            
            for (com.arcadedb.graph.Edge edge : edges) {
                Vertex connectedVertex = edge.getInVertex();
                String connectedId = connectedVertex.getString("nodeId");
                if (!visitedIds.contains(connectedId)) {
                    visitedIds.add(connectedId);
                    nodes.add(vertexToNode(connectedVertex));
                }
            }
        } else if ("INCOMING".equals(direction)) {
            Iterable<com.arcadedb.graph.Edge> edges = relationshipType != null
                ? startVertex.getEdges(Vertex.DIRECTION.IN, relationshipType)
                : startVertex.getEdges(Vertex.DIRECTION.IN);
            
            for (com.arcadedb.graph.Edge edge : edges) {
                Vertex connectedVertex = edge.getOutVertex();
                String connectedId = connectedVertex.getString("nodeId");
                if (!visitedIds.contains(connectedId)) {
                    visitedIds.add(connectedId);
                    nodes.add(vertexToNode(connectedVertex));
                }
            }
        } else { // BOTH
            Iterable<com.arcadedb.graph.Edge> edges = relationshipType != null
                ? startVertex.getEdges(Vertex.DIRECTION.BOTH, relationshipType)
                : startVertex.getEdges(Vertex.DIRECTION.BOTH);
            
            for (com.arcadedb.graph.Edge edge : edges) {
                Vertex connectedVertex = edge.getOutVertex().equals(startVertex) ? edge.getInVertex() : edge.getOutVertex();
                String connectedId = connectedVertex.getString("nodeId");
                if (!visitedIds.contains(connectedId)) {
                    visitedIds.add(connectedId);
                    nodes.add(vertexToNode(connectedVertex));
                }
            }
        }
        
        return nodes;
    }
    
    @Override
    public List<Node> traverseBFS(String startNodeId, int maxDepth, Set<String> relationshipTypes) throws Exception {
        List<Node> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<VertexWithDepth> queue = new LinkedList<>();
        
        // Find starting vertex
        ResultSet queryResult = database.query("sql", 
            "SELECT FROM " + NODE_TYPE + " WHERE nodeId = ?", startNodeId);
        
        if (!queryResult.hasNext()) {
            return result;
        }
        
        Vertex startVertex = queryResult.next().getVertex().get();
        queue.offer(new VertexWithDepth(startVertex, 0));
        visited.add(startNodeId);
        
        while (!queue.isEmpty()) {
            VertexWithDepth current = queue.poll();
            result.add(vertexToNode(current.vertex));
            
            if (current.depth < maxDepth) {
                // Get all outgoing edges
                Iterable<com.arcadedb.graph.Edge> edges = current.vertex.getEdges(Vertex.DIRECTION.OUT);
                
                for (com.arcadedb.graph.Edge edge : edges) {
                    // Check relationship type filter
                    if (relationshipTypes != null && !relationshipTypes.isEmpty() 
                        && !relationshipTypes.contains(edge.getTypeName())) {
                        continue;
                    }
                    
                    Vertex neighbor = edge.getInVertex();
                    String neighborId = neighbor.getString("nodeId");
                    
                    if (!visited.contains(neighborId)) {
                        visited.add(neighborId);
                        queue.offer(new VertexWithDepth(neighbor, current.depth + 1));
                    }
                }
            }
        }
        
        return result;
    }
    
    @Override
    public List<Node> traverseDFS(String startNodeId, int maxDepth, Set<String> relationshipTypes) throws Exception {
        List<Node> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        // Find starting vertex
        ResultSet queryResult = database.query("sql", 
            "SELECT FROM " + NODE_TYPE + " WHERE nodeId = ?", startNodeId);
        
        if (!queryResult.hasNext()) {
            return result;
        }
        
        Vertex startVertex = queryResult.next().getVertex().get();
        traverseDFSRecursive(startVertex, 0, maxDepth, relationshipTypes, visited, result);
        
        return result;
    }
    
    private void traverseDFSRecursive(Vertex vertex, int depth, int maxDepth, Set<String> relationshipTypes,
                                      Set<String> visited, List<Node> result) {
        String nodeId = vertex.getString("nodeId");
        if (visited.contains(nodeId) || depth > maxDepth) {
            return;
        }
        
        visited.add(nodeId);
        result.add(vertexToNode(vertex));
        
        if (depth < maxDepth) {
            Iterable<com.arcadedb.graph.Edge> edges = vertex.getEdges(Vertex.DIRECTION.OUT);
            
            for (com.arcadedb.graph.Edge edge : edges) {
                // Check relationship type filter
                if (relationshipTypes != null && !relationshipTypes.isEmpty() 
                    && !relationshipTypes.contains(edge.getTypeName())) {
                    continue;
                }
                
                Vertex neighbor = edge.getInVertex();
                String neighborId = neighbor.getString("nodeId");
                
                if (!visited.contains(neighborId)) {
                    traverseDFSRecursive(neighbor, depth + 1, maxDepth, relationshipTypes, visited, result);
                }
            }
        }
    }
    
    @Override
    public int getNodeCount() throws Exception {
        ResultSet result = database.query("sql", "SELECT count(*) as count FROM " + NODE_TYPE);
        if (result.hasNext()) {
            return ((Number) result.next().getProperty("count")).intValue();
        }
        return 0;
    }
    
    @Override
    public int getEdgeCount() throws Exception {
        // Count all edges from all vertices
        int count = 0;
        ResultSet result = database.query("sql", "SELECT FROM " + NODE_TYPE);
        while (result.hasNext()) {
            Vertex vertex = result.next().getVertex().get();
            for (com.arcadedb.graph.Edge edge : vertex.getEdges(Vertex.DIRECTION.OUT)) {
                count++;
            }
        }
        return count;
    }
    
    @Override
    public void close() throws Exception {
        if (database != null) {
            database.close();
            logger.debug("ArcadeDB graph database closed");
        }
    }
    
    /**
     * Convert a Vertex to a Node
     */
    private Node vertexToNode(Vertex vertex) {
        return new Node(
            vertex.getString("nodeId"),
            vertex.getString("nodeType"),
            vertex.getString("name"),
            vertex.getString("filePath")
        );
    }
    
    /**
     * Helper class for BFS traversal
     */
    private static class VertexWithDepth {
        final Vertex vertex;
        final int depth;
        
        VertexWithDepth(Vertex vertex, int depth) {
            this.vertex = vertex;
            this.depth = depth;
        }
    }
}
