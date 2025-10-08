package com.ygmpkk.codesearch.analysis;

/**
 * Lightweight representation of a relationship between two code nodes that is
 * persisted to the graph database. Currently used for method invocation call
 * chains.
 */
public record GraphEdge(String fromNodeId, String toNodeId, String relationshipType) {
}

