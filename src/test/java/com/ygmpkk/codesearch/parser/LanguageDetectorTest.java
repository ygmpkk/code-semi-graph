package com.ygmpkk.codesearch.parser;

import ch.usi.si.seart.treesitter.Language;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LanguageDetectorTest {

    @Test
    void testDetectJava() {
        Optional<Language> result = LanguageDetector.detectLanguage(Path.of("Test.java"));
        if (result.isPresent()) {
            assertEquals(Language.JAVA, result.get());
        } else {
            // Tree-sitter not available
            assertTrue(LanguageDetector.getSupportedExtensions().isEmpty());
        }
    }

    @Test
    void testDetectPython() {
        Optional<Language> result = LanguageDetector.detectLanguage(Path.of("script.py"));
        if (result.isPresent()) {
            assertEquals(Language.PYTHON, result.get());
        } else {
            // Tree-sitter not available
            assertTrue(LanguageDetector.getSupportedExtensions().isEmpty());
        }
    }

    @Test
    void testDetectJavaScript() {
        Optional<Language> result = LanguageDetector.detectLanguage(Path.of("app.js"));
        if (result.isPresent()) {
            assertEquals(Language.JAVASCRIPT, result.get());
        } else {
            // Tree-sitter not available
            assertTrue(LanguageDetector.getSupportedExtensions().isEmpty());
        }
    }

    @Test
    void testDetectTypeScript() {
        Optional<Language> result = LanguageDetector.detectLanguage(Path.of("component.ts"));
        if (result.isPresent()) {
            assertEquals(Language.TYPESCRIPT, result.get());
        } else {
            // Tree-sitter not available
            assertTrue(LanguageDetector.getSupportedExtensions().isEmpty());
        }
    }

    @Test
    void testDetectGo() {
        Optional<Language> result = LanguageDetector.detectLanguage(Path.of("main.go"));
        if (result.isPresent()) {
            assertEquals(Language.GO, result.get());
        } else {
            // Tree-sitter not available
            assertTrue(LanguageDetector.getSupportedExtensions().isEmpty());
        }
    }

    @Test
    void testDetectRust() {
        Optional<Language> result = LanguageDetector.detectLanguage(Path.of("lib.rs"));
        if (result.isPresent()) {
            assertEquals(Language.RUST, result.get());
        } else {
            // Tree-sitter not available
            assertTrue(LanguageDetector.getSupportedExtensions().isEmpty());
        }
    }

    @Test
    void testDetectKotlin() {
        Optional<Language> result = LanguageDetector.detectLanguage(Path.of("Main.kt"));
        if (result.isPresent()) {
            assertEquals(Language.KOTLIN, result.get());
        } else {
            // Tree-sitter not available
            assertTrue(LanguageDetector.getSupportedExtensions().isEmpty());
        }
    }

    @Test
    void testUnsupportedExtension() {
        Optional<Language> result = LanguageDetector.detectLanguage(Path.of("file.txt"));
        assertFalse(result.isPresent());
    }

    @Test
    void testNoExtension() {
        Optional<Language> result = LanguageDetector.detectLanguage(Path.of("README"));
        assertFalse(result.isPresent());
    }

    @Test
    void testDotFile() {
        Optional<Language> result = LanguageDetector.detectLanguage(Path.of(".gitignore"));
        assertFalse(result.isPresent());
    }

    @Test
    void testIsSupported() {
        boolean javaSupported = LanguageDetector.isSupported(Path.of("Test.java"));
        boolean txtSupported = LanguageDetector.isSupported(Path.of("file.txt"));
        
        if (!LanguageDetector.getSupportedExtensions().isEmpty()) {
            assertTrue(javaSupported);
            assertFalse(txtSupported);
        } else {
            // Tree-sitter not available
            assertFalse(javaSupported);
            assertFalse(txtSupported);
        }
    }

    @Test
    void testGetSupportedExtensions() {
        Set<String> extensions = LanguageDetector.getSupportedExtensions();
        assertNotNull(extensions);
        
        if (!extensions.isEmpty()) {
            // If tree-sitter is available, check for common extensions
            assertTrue(extensions.contains("java"));
            assertTrue(extensions.contains("py"));
            assertTrue(extensions.contains("js"));
            assertTrue(extensions.contains("go"));
            assertTrue(extensions.contains("rs"));
        }
    }
}
