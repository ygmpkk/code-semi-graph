package com.ygmpkk.codesearch.parser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunks code into manageable pieces with context
 * Maximum chunk size is 32K tokens (approximately 8K characters per token estimation)
 */
public class CodeChunker {
    private static final Logger logger = LogManager.getLogger(CodeChunker.class);
    
    // Conservative estimate: 1 token ≈ 4 characters for code
    private static final int MAX_TOKENS = 32000;
    private static final int CHARS_PER_TOKEN = 4;
    private static final int MAX_CHUNK_CHARS = MAX_TOKENS * CHARS_PER_TOKEN;
    
    /**
     * Represents a chunk of code with metadata
     */
    public record CodeChunk(
            String filePath,
            String language,
            String packageName,
            String className,
            String methodName,
            String content,
            int startLine,
            int endLine,
            List<String> properties,
            List<String> callees
    ) {
        public String getFullContext() {
            StringBuilder context = new StringBuilder();
            if (language != null && !language.isEmpty()) {
                context.append("Language: ").append(language).append("\n");
            }
            if (packageName != null && !packageName.isEmpty()) {
                context.append("Package: ").append(packageName).append("\n");
            }
            if (className != null && !className.isEmpty()) {
                context.append("Class: ").append(className).append("\n");
            }
            if (methodName != null && !methodName.isEmpty()) {
                context.append("Method: ").append(methodName).append("\n");
            }
            if (properties != null && !properties.isEmpty()) {
                context.append("Properties: ").append(String.join(", ", properties)).append("\n");
            }
            context.append("\n").append(content);
            return context.toString();
        }
        
        public int estimateTokens() {
            return getFullContext().length() / CHARS_PER_TOKEN;
        }
    }
    
    /**
     * Chunk code metadata into manageable pieces
     * Each method becomes a separate chunk with full context
     */
    public List<CodeChunk> chunkCode(CodeMetadata metadata) {
        List<CodeChunk> chunks = new ArrayList<>();
        
        if (metadata.methods().isEmpty()) {
            // If no methods, create a single chunk for the whole file
            String content = "// No methods found in file";
            CodeChunk chunk = new CodeChunk(
                    metadata.filePath(),
                    metadata.language(),
                    metadata.packageName(),
                    metadata.className(),
                    "",
                    content,
                    1,
                    1,
                    metadata.properties(),
                    List.of()
            );
            
            if (chunk.estimateTokens() <= MAX_TOKENS) {
                chunks.add(chunk);
            } else {
                logger.warn("File {} is too large even without methods", metadata.filePath());
            }
            
            return chunks;
        }
        
        // Create a chunk for each method
        for (CodeMetadata.MethodInfo method : metadata.methods()) {
            CodeChunk chunk = new CodeChunk(
                    metadata.filePath(),
                    metadata.language(),
                    metadata.packageName(),
                    metadata.className(),
                    method.name(),
                    method.body(),
                    method.startLine(),
                    method.endLine(),
                    metadata.properties(),
                    method.callees()
            );
            
            // Check if chunk is within size limit
            if (chunk.estimateTokens() > MAX_TOKENS) {
                logger.warn("Method {} in {} exceeds max token limit (estimated: {} tokens). Truncating.",
                        method.name(), metadata.filePath(), chunk.estimateTokens());
                
                // Truncate the method body
                String truncatedBody = truncateToMaxChars(method.body(), MAX_CHUNK_CHARS);
                chunk = new CodeChunk(
                        metadata.filePath(),
                        metadata.language(),
                        metadata.packageName(),
                        metadata.className(),
                        method.name(),
                        truncatedBody,
                        method.startLine(),
                        method.endLine(),
                        metadata.properties(),
                        method.callees()
                );
            }
            
            chunks.add(chunk);
        }
        
        logger.debug("Created {} chunks from file {}", chunks.size(), metadata.filePath());
        return chunks;
    }
    
    private String truncateToMaxChars(String content, int maxChars) {
        if (content.length() <= maxChars) {
            return content;
        }
        
        return content.substring(0, maxChars - 50) + "\n// ... (truncated)";
    }
}
