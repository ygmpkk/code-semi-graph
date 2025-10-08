package com.ygmpkk.codesearch.analysis;

import java.util.List;

/**
 * Aggregated result of analysing a single source file using tree-sitter. The
 * analysis produces code chunks for vector indexing as well as graph nodes and
 * edges describing structural relationships such as method invocations.
 */
public record CodeFileAnalysis(
        List<CodeChunk> chunks,
        List<GraphNode> nodes,
        List<GraphEdge> edges
) {
}

