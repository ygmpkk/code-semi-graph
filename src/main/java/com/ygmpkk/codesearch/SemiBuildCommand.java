package com.ygmpkk.codesearch;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * Command for building embedding index for semi code search
 */
@Command(
    name = "build",
    description = "Build embedding index for code search using Qwen3-Embedding-0.6B",
    mixinStandardHelpOptions = true
)
public class SemiBuildCommand implements Callable<Integer> {

    @Option(
        names = {"-p", "--path"},
        description = "Path to build index from (default: current directory)"
    )
    private String path = ".";

    @Option(
        names = {"-o", "--output"},
        description = "Output directory for the index (default: ./.code-index)"
    )
    private String outputDir = "./.code-index";

    @Option(
        names = {"-e", "--extensions"},
        description = "File extensions to index (comma-separated)",
        split = ","
    )
    private String[] extensions;

    @Option(
        names = {"-d", "--depth"},
        description = "Maximum directory depth to traverse (default: unlimited)"
    )
    private Integer maxDepth;

    @Option(
        names = {"-m", "--model"},
        description = "Embedding model to use (default: Qwen/Qwen3-Embedding-0.6B)"
    )
    private String model = "Qwen/Qwen3-Embedding-0.6B";

    @Option(
        names = {"--batch-size"},
        description = "Batch size for processing files (default: 32)"
    )
    private int batchSize = 32;

    @Override
    public Integer call() {
        System.out.println("Building embedding index...");
        System.out.println("Source path: " + path);
        System.out.println("Output directory: " + outputDir);
        System.out.println("Model: " + model);
        System.out.println("Batch size: " + batchSize);
        
        if (maxDepth != null) {
            System.out.println("Max depth: " + maxDepth);
        }
        
        if (extensions != null && extensions.length > 0) {
            System.out.println("Extensions: " + String.join(", ", extensions));
        }

        try {
            // Create output directory if it doesn't exist
            Path outputPath = Paths.get(outputDir);
            Files.createDirectories(outputPath);
            
            // Collect files to index
            List<Path> filesToIndex = collectFiles();
            System.out.println("\nFound " + filesToIndex.size() + " files to index");
            
            if (filesToIndex.isEmpty()) {
                System.out.println("No files found to index. Exiting.");
                return 0;
            }
            
            // TODO: Initialize embedding model (Qwen3-Embedding-0.6B)
            // For now, we'll just simulate the process
            System.out.println("\nInitializing embedding model: " + model);
            System.out.println("Note: Model loading requires DJL and Hugging Face transformers");
            System.out.println("This is a placeholder implementation.");
            
            // Process files in batches
            System.out.println("\nProcessing files in batches of " + batchSize + "...");
            int totalBatches = (int) Math.ceil((double) filesToIndex.size() / batchSize);
            
            for (int i = 0; i < filesToIndex.size(); i += batchSize) {
                int batchNum = (i / batchSize) + 1;
                int endIdx = Math.min(i + batchSize, filesToIndex.size());
                List<Path> batch = filesToIndex.subList(i, endIdx);
                
                System.out.println("Processing batch " + batchNum + "/" + totalBatches + 
                                 " (" + batch.size() + " files)");
                
                // TODO: Generate embeddings for batch
                // TODO: Store embeddings in index
                
                for (Path file : batch) {
                    System.out.println("  - " + file);
                }
            }
            
            System.out.println("\n✓ Embedding index built successfully!");
            System.out.println("Index location: " + outputPath.toAbsolutePath());
            
        } catch (IOException e) {
            System.err.println("Error building index: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
        
        return 0;
    }

    private List<Path> collectFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        Path sourcePath = Paths.get(path);
        
        if (!Files.exists(sourcePath)) {
            System.err.println("Error: Path does not exist: " + path);
            return files;
        }
        
        if (Files.isRegularFile(sourcePath)) {
            if (shouldIncludeFile(sourcePath)) {
                files.add(sourcePath);
            }
            return files;
        }
        
        // Traverse directory with error handling
        int depth = (maxDepth != null) ? maxDepth : Integer.MAX_VALUE;
        
        try (Stream<Path> pathStream = Files.walk(sourcePath, depth)) {
            pathStream
                .filter(p -> {
                    try {
                        return Files.isRegularFile(p);
                    } catch (Exception e) {
                        // Skip files that can't be accessed
                        return false;
                    }
                })
                .filter(this::shouldIncludeFile)
                .forEach(files::add);
        } catch (Exception e) {
            // If walk fails completely, try to handle gracefully
            System.err.println("Warning: Error traversing directory: " + e.getMessage());
        }
        
        return files;
    }

    private boolean shouldIncludeFile(Path file) {
        String fileName = file.getFileName().toString();
        
        // Skip hidden files and common non-source directories
        if (fileName.startsWith(".") || 
            file.toString().contains("/.git/") ||
            file.toString().contains("/node_modules/") ||
            file.toString().contains("/build/") ||
            file.toString().contains("/target/") ||
            file.toString().contains("/.gradle/")) {
            return false;
        }
        
        // If extensions are specified, check if file matches
        if (extensions != null && extensions.length > 0) {
            for (String ext : extensions) {
                if (fileName.endsWith("." + ext)) {
                    return true;
                }
            }
            return false;
        }
        
        // Default: include common code file extensions
        return fileName.endsWith(".java") ||
               fileName.endsWith(".kt") ||
               fileName.endsWith(".js") ||
               fileName.endsWith(".ts") ||
               fileName.endsWith(".py") ||
               fileName.endsWith(".go") ||
               fileName.endsWith(".rs") ||
               fileName.endsWith(".cpp") ||
               fileName.endsWith(".c") ||
               fileName.endsWith(".h") ||
               fileName.endsWith(".cs") ||
               fileName.endsWith(".rb") ||
               fileName.endsWith(".php") ||
               fileName.endsWith(".swift") ||
               fileName.endsWith(".scala");
    }
}
