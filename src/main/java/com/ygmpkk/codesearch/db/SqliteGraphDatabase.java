package com.ygmpkk.codesearch.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;
import java.util.*;

/**
 * SQLite implementation of GraphDatabase
 * Stores code relationships and performs graph traversals
 */
public class SqliteGraphDatabase implements GraphDatabase {
    private static final Logger logger = LogManager.getLogger(SqliteGraphDatabase.class);
    private final Connection connection;
    
    public SqliteGraphDatabase(String dbPath) throws SQLException {
        String url = "jdbc:sqlite:" + dbPath;
        this.connection = DriverManager.getConnection(url);
        logger.debug("Connected to SQLite graph database: {}", dbPath);
    }
    
    @Override
    public void initialize() throws Exception {
        // Create nodes table
        String createNodesTable = """
            CREATE TABLE IF NOT EXISTS nodes (
                node_id TEXT PRIMARY KEY,
                node_type TEXT NOT NULL,
                name TEXT NOT NULL,
                file_path TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
        
        // Create edges table
        String createEdgesTable = """
            CREATE TABLE IF NOT EXISTS edges (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                from_node_id TEXT NOT NULL,
                to_node_id TEXT NOT NULL,
                relationship_type TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (from_node_id) REFERENCES nodes(node_id),
                FOREIGN KEY (to_node_id) REFERENCES nodes(node_id),
                UNIQUE(from_node_id, to_node_id, relationship_type)
            )
            """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createNodesTable);
            stmt.execute(createEdgesTable);
            
            // Create indexes for faster lookups
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_node_name ON nodes(name)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_node_type ON nodes(node_type)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_edge_from ON edges(from_node_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_edge_to ON edges(to_node_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_edge_relationship ON edges(relationship_type)");
            
            logger.debug("Graph database schema initialized");
        }
    }
    
    @Override
    public void addNode(String nodeId, String nodeType, String name, String filePath) throws Exception {
        String sql = """
            INSERT OR REPLACE INTO nodes (node_id, node_type, name, file_path)
            VALUES (?, ?, ?, ?)
            """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, nodeId);
            pstmt.setString(2, nodeType);
            pstmt.setString(3, name);
            pstmt.setString(4, filePath);
            pstmt.executeUpdate();
            
            logger.debug("Added node: {} ({})", name, nodeType);
        }
    }
    
    @Override
    public void addEdge(String fromNodeId, String toNodeId, String relationshipType) throws Exception {
        String sql = """
            INSERT OR IGNORE INTO edges (from_node_id, to_node_id, relationship_type)
            VALUES (?, ?, ?)
            """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, fromNodeId);
            pstmt.setString(2, toNodeId);
            pstmt.setString(3, relationshipType);
            pstmt.executeUpdate();
            
            logger.debug("Added edge: {} -[{}]-> {}", fromNodeId, relationshipType, toNodeId);
        }
    }
    
    @Override
    public Node findNodeByName(String name) throws Exception {
        String sql = "SELECT node_id, node_type, name, file_path FROM nodes WHERE name = ? LIMIT 1";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, name);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Node(
                        rs.getString("node_id"),
                        rs.getString("node_type"),
                        rs.getString("name"),
                        rs.getString("file_path")
                    );
                }
            }
        }
        
        return null;
    }
    
    @Override
    public List<Node> getConnectedNodes(String nodeId, String relationshipType, String direction) throws Exception {
        List<Node> nodes = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        
        if ("OUTGOING".equals(direction)) {
            sql.append("SELECT DISTINCT n.node_id, n.node_type, n.name, n.file_path ")
               .append("FROM nodes n ")
               .append("JOIN edges e ON n.node_id = e.to_node_id ")
               .append("WHERE e.from_node_id = ?");
            
            if (relationshipType != null) {
                sql.append(" AND e.relationship_type = ?");
            }
        } else if ("INCOMING".equals(direction)) {
            sql.append("SELECT DISTINCT n.node_id, n.node_type, n.name, n.file_path ")
               .append("FROM nodes n ")
               .append("JOIN edges e ON n.node_id = e.from_node_id ")
               .append("WHERE e.to_node_id = ?");
            
            if (relationshipType != null) {
                sql.append(" AND e.relationship_type = ?");
            }
        } else { // BOTH
            sql.append("SELECT DISTINCT n.node_id, n.node_type, n.name, n.file_path ")
               .append("FROM nodes n ")
               .append("WHERE n.node_id IN (")
               .append("  SELECT e.to_node_id FROM edges e WHERE e.from_node_id = ?")
               .append("  UNION ")
               .append("  SELECT e.from_node_id FROM edges e WHERE e.to_node_id = ?")
               .append(")");
            
            if (relationshipType != null) {
                sql.append(" AND EXISTS (")
                   .append("  SELECT 1 FROM edges e2 ")
                   .append("  WHERE (e2.from_node_id = ? AND e2.to_node_id = n.node_id OR ")
                   .append("         e2.to_node_id = ? AND e2.from_node_id = n.node_id)")
                   .append("  AND e2.relationship_type = ?)");
            }
        }
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            if ("BOTH".equals(direction)) {
                pstmt.setString(1, nodeId);
                pstmt.setString(2, nodeId);
                if (relationshipType != null) {
                    pstmt.setString(3, nodeId);
                    pstmt.setString(4, nodeId);
                    pstmt.setString(5, relationshipType);
                }
            } else {
                pstmt.setString(1, nodeId);
                if (relationshipType != null) {
                    pstmt.setString(2, relationshipType);
                }
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    nodes.add(new Node(
                        rs.getString("node_id"),
                        rs.getString("node_type"),
                        rs.getString("name"),
                        rs.getString("file_path")
                    ));
                }
            }
        }
        
        return nodes;
    }
    
    @Override
    public List<Node> traverseBFS(String startNodeId, int maxDepth, Set<String> relationshipTypes) throws Exception {
        List<Node> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<NodeWithDepth> queue = new LinkedList<>();
        
        // Get starting node
        Node startNode = getNodeById(startNodeId);
        if (startNode == null) {
            return result;
        }
        
        queue.offer(new NodeWithDepth(startNode, 0));
        visited.add(startNodeId);
        
        while (!queue.isEmpty()) {
            NodeWithDepth current = queue.poll();
            result.add(current.node);
            
            if (current.depth < maxDepth) {
                // Get all connected nodes
                for (Node neighbor : getConnectedNodes(current.node.getNodeId(), null, "OUTGOING")) {
                    if (!visited.contains(neighbor.getNodeId())) {
                        // Check relationship type filter
                        if (relationshipTypes == null || relationshipTypes.isEmpty() ||
                            hasRelationshipType(current.node.getNodeId(), neighbor.getNodeId(), relationshipTypes)) {
                            visited.add(neighbor.getNodeId());
                            queue.offer(new NodeWithDepth(neighbor, current.depth + 1));
                        }
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
        
        // Get starting node
        Node startNode = getNodeById(startNodeId);
        if (startNode == null) {
            return result;
        }
        
        traverseDFSRecursive(startNode, 0, maxDepth, relationshipTypes, visited, result);
        
        return result;
    }
    
    private void traverseDFSRecursive(Node node, int depth, int maxDepth, Set<String> relationshipTypes,
                                      Set<String> visited, List<Node> result) throws Exception {
        if (visited.contains(node.getNodeId()) || depth > maxDepth) {
            return;
        }
        
        visited.add(node.getNodeId());
        result.add(node);
        
        if (depth < maxDepth) {
            for (Node neighbor : getConnectedNodes(node.getNodeId(), null, "OUTGOING")) {
                if (!visited.contains(neighbor.getNodeId())) {
                    if (relationshipTypes == null || relationshipTypes.isEmpty() ||
                        hasRelationshipType(node.getNodeId(), neighbor.getNodeId(), relationshipTypes)) {
                        traverseDFSRecursive(neighbor, depth + 1, maxDepth, relationshipTypes, visited, result);
                    }
                }
            }
        }
    }
    
    @Override
    public int getNodeCount() throws Exception {
        String sql = "SELECT COUNT(*) FROM nodes";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        
        return 0;
    }
    
    @Override
    public int getEdgeCount() throws Exception {
        String sql = "SELECT COUNT(*) FROM edges";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        
        return 0;
    }
    
    @Override
    public void close() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            logger.debug("Graph database connection closed");
        }
    }
    
    /**
     * Get a node by its ID
     */
    private Node getNodeById(String nodeId) throws Exception {
        String sql = "SELECT node_id, node_type, name, file_path FROM nodes WHERE node_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, nodeId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Node(
                        rs.getString("node_id"),
                        rs.getString("node_type"),
                        rs.getString("name"),
                        rs.getString("file_path")
                    );
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if an edge exists with one of the specified relationship types
     */
    private boolean hasRelationshipType(String fromNodeId, String toNodeId, Set<String> relationshipTypes) throws Exception {
        StringBuilder sql = new StringBuilder("SELECT 1 FROM edges WHERE from_node_id = ? AND to_node_id = ? AND relationship_type IN (");
        
        for (int i = 0; i < relationshipTypes.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        sql.append(")");
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            pstmt.setString(1, fromNodeId);
            pstmt.setString(2, toNodeId);
            
            int index = 3;
            for (String type : relationshipTypes) {
                pstmt.setString(index++, type);
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    /**
     * Helper class for BFS traversal
     */
    private static class NodeWithDepth {
        final Node node;
        final int depth;
        
        NodeWithDepth(Node node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }
}
