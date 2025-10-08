package com.ygmpkk.codesearch.analysis;

/**
 * Lightweight description of a code element (class, interface, method, etc.)
 * that should be persisted to the graph database.
 */
public record GraphNode(String nodeId, String nodeType, String name, String filePath) {
}

