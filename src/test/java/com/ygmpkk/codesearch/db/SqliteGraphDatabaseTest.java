package com.ygmpkk.codesearch.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SqliteGraphDatabaseTest {
    
    @TempDir
    Path tempDir;
    
    private GraphDatabase graphDb;
    
    @BeforeEach
    void setUp() throws Exception {
        String dbPath = tempDir.resolve("test-graph.db").toString();
        graphDb = new SqliteGraphDatabase(dbPath);
        graphDb.initialize();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (graphDb != null) {
            graphDb.close();
        }
    }
    
    @Test
    void testAddNode() throws Exception {
        graphDb.addNode("node1", "class", "MyClass", "/path/to/file.java");
        
        assertEquals(1, graphDb.getNodeCount());
    }
    
    @Test
    void testAddEdge() throws Exception {
        graphDb.addNode("node1", "class", "MyClass", "/path/to/file1.java");
        graphDb.addNode("node2", "class", "BaseClass", "/path/to/file2.java");
        
        graphDb.addEdge("node1", "node2", "extends");
        
        assertEquals(1, graphDb.getEdgeCount());
    }
    
    @Test
    void testFindNodeByName() throws Exception {
        graphDb.addNode("node1", "class", "MyClass", "/path/to/file.java");
        
        GraphDatabase.Node node = graphDb.findNodeByName("MyClass");
        
        assertNotNull(node);
        assertEquals("node1", node.getNodeId());
        assertEquals("class", node.getNodeType());
        assertEquals("MyClass", node.getName());
    }
    
    @Test
    void testGetConnectedNodes() throws Exception {
        graphDb.addNode("node1", "class", "MyClass", "/file1.java");
        graphDb.addNode("node2", "class", "BaseClass", "/file2.java");
        graphDb.addNode("node3", "method", "doSomething", "/file1.java");
        
        graphDb.addEdge("node1", "node2", "extends");
        graphDb.addEdge("node1", "node3", "contains");
        
        List<GraphDatabase.Node> outgoing = graphDb.getConnectedNodes("node1", null, "OUTGOING");
        
        assertEquals(2, outgoing.size());
    }
    
    @Test
    void testTraverseBFS() throws Exception {
        // Create a simple graph: A -> B -> C
        //                         A -> D
        graphDb.addNode("A", "class", "ClassA", "/A.java");
        graphDb.addNode("B", "class", "ClassB", "/B.java");
        graphDb.addNode("C", "class", "ClassC", "/C.java");
        graphDb.addNode("D", "class", "ClassD", "/D.java");
        
        graphDb.addEdge("A", "B", "calls");
        graphDb.addEdge("B", "C", "calls");
        graphDb.addEdge("A", "D", "calls");
        
        List<GraphDatabase.Node> result = graphDb.traverseBFS("A", 2, null);
        
        // Should get all 4 nodes in BFS order
        assertEquals(4, result.size());
        assertEquals("ClassA", result.get(0).getName());
    }
    
    @Test
    void testTraverseDFS() throws Exception {
        // Create a simple graph
        graphDb.addNode("A", "class", "ClassA", "/A.java");
        graphDb.addNode("B", "class", "ClassB", "/B.java");
        graphDb.addNode("C", "class", "ClassC", "/C.java");
        
        graphDb.addEdge("A", "B", "calls");
        graphDb.addEdge("B", "C", "calls");
        
        List<GraphDatabase.Node> result = graphDb.traverseDFS("A", 2, null);
        
        // Should get all 3 nodes
        assertEquals(3, result.size());
        assertEquals("ClassA", result.get(0).getName());
    }
    
    @Test
    void testTraversalWithRelationshipFilter() throws Exception {
        graphDb.addNode("A", "class", "ClassA", "/A.java");
        graphDb.addNode("B", "class", "ClassB", "/B.java");
        graphDb.addNode("C", "class", "ClassC", "/C.java");
        
        graphDb.addEdge("A", "B", "extends");
        graphDb.addEdge("A", "C", "calls");
        
        Set<String> filter = new HashSet<>();
        filter.add("extends");
        
        List<GraphDatabase.Node> result = graphDb.traverseBFS("A", 2, filter);
        
        // Should only traverse through "extends" relationships
        assertEquals(2, result.size());
        assertEquals("ClassA", result.get(0).getName());
        assertEquals("ClassB", result.get(1).getName());
    }
    
    @Test
    void testReplaceNode() throws Exception {
        graphDb.addNode("node1", "class", "MyClass", "/file1.java");
        assertEquals(1, graphDb.getNodeCount());
        
        // Replace with same ID
        graphDb.addNode("node1", "class", "MyClass", "/file2.java");
        assertEquals(1, graphDb.getNodeCount());
        
        GraphDatabase.Node node = graphDb.findNodeByName("MyClass");
        assertEquals("/file2.java", node.getFilePath());
    }
}
