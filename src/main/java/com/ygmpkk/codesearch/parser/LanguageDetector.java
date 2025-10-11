package com.ygmpkk.codesearch.parser;

import ch.usi.si.seart.treesitter.Language;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.*;

/**
 * Utility class for detecting programming languages from file extensions
 */
public class LanguageDetector {
    private static final Logger logger = LogManager.getLogger(LanguageDetector.class);
    
    // Map file extensions to tree-sitter languages
    private static final Map<String, Language> EXTENSION_TO_LANGUAGE = new HashMap<>();
    
    static {
        try {
            // Initialize the mapping - only if tree-sitter is available
            EXTENSION_TO_LANGUAGE.put("java", Language.JAVA);
            EXTENSION_TO_LANGUAGE.put("py", Language.PYTHON);
            EXTENSION_TO_LANGUAGE.put("js", Language.JAVASCRIPT);
            EXTENSION_TO_LANGUAGE.put("jsx", Language.JAVASCRIPT);
            EXTENSION_TO_LANGUAGE.put("ts", Language.TYPESCRIPT);
            EXTENSION_TO_LANGUAGE.put("tsx", Language.TSX);
            EXTENSION_TO_LANGUAGE.put("go", Language.GO);
            EXTENSION_TO_LANGUAGE.put("rs", Language.RUST);
            EXTENSION_TO_LANGUAGE.put("c", Language.C);
            EXTENSION_TO_LANGUAGE.put("h", Language.C);
            EXTENSION_TO_LANGUAGE.put("cpp", Language.CPP);
            EXTENSION_TO_LANGUAGE.put("cc", Language.CPP);
            EXTENSION_TO_LANGUAGE.put("cxx", Language.CPP);
            EXTENSION_TO_LANGUAGE.put("hpp", Language.CPP);
            EXTENSION_TO_LANGUAGE.put("cs", Language.C_SHARP);
            EXTENSION_TO_LANGUAGE.put("rb", Language.RUBY);
            EXTENSION_TO_LANGUAGE.put("php", Language.PHP);
            EXTENSION_TO_LANGUAGE.put("swift", Language.SWIFT);
            EXTENSION_TO_LANGUAGE.put("kt", Language.KOTLIN);
            EXTENSION_TO_LANGUAGE.put("kts", Language.KOTLIN);
            EXTENSION_TO_LANGUAGE.put("scala", Language.SCALA);
            EXTENSION_TO_LANGUAGE.put("dart", Language.DART);
            EXTENSION_TO_LANGUAGE.put("lua", Language.LUA);
            EXTENSION_TO_LANGUAGE.put("r", Language.R);
            EXTENSION_TO_LANGUAGE.put("bash", Language.BASH);
            EXTENSION_TO_LANGUAGE.put("sh", Language.BASH);
            logger.debug("Language detector initialized with {} mappings", EXTENSION_TO_LANGUAGE.size());
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            logger.warn("Tree-sitter native libraries not available for language detection: {}", e.getMessage());
        }
    }
    
    /**
     * Detect the programming language from a file path based on its extension
     * 
     * @param filePath the path to the file
     * @return Optional containing the detected Language, or empty if not supported
     */
    public static Optional<Language> detectLanguage(Path filePath) {
        if (EXTENSION_TO_LANGUAGE.isEmpty()) {
            // Tree-sitter not available
            return Optional.empty();
        }
        
        String fileName = filePath.getFileName().toString();
        int lastDotIndex = fileName.lastIndexOf('.');
        
        if (lastDotIndex <= 0 || lastDotIndex == fileName.length() - 1) {
            return Optional.empty();
        }
        
        String extension = fileName.substring(lastDotIndex + 1).toLowerCase();
        Language language = EXTENSION_TO_LANGUAGE.get(extension);
        
        if (language != null) {
            logger.debug("Detected language {} for file {}", language.name(), fileName);
        }
        
        return Optional.ofNullable(language);
    }
    
    /**
     * Check if tree-sitter parsing is supported for the given file
     * 
     * @param filePath the path to the file
     * @return true if the file can be parsed with tree-sitter
     */
    public static boolean isSupported(Path filePath) {
        return detectLanguage(filePath).isPresent();
    }
    
    /**
     * Get all supported file extensions
     * 
     * @return set of supported extensions (without dots)
     */
    public static Set<String> getSupportedExtensions() {
        return Collections.unmodifiableSet(EXTENSION_TO_LANGUAGE.keySet());
    }
}
