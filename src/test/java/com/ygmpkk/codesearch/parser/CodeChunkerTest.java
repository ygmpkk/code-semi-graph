package com.ygmpkk.codesearch.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CodeChunkerTest {

    @Test
    void testChunkingWithMethods() {
        // Create sample metadata with methods
        CodeMetadata.MethodInfo method1 = new CodeMetadata.MethodInfo(
                "testMethod",
                "void",
                List.of("param1", "param2"),
                "{ System.out.println(\"test\"); }",
                10,
                15,
                List.of("println")
        );

        CodeMetadata.MethodInfo method2 = new CodeMetadata.MethodInfo(
                "anotherMethod",
                "String",
                List.of(),
                "{ return \"test\"; }",
                20,
                25,
                List.of()
        );

        CodeMetadata metadata = new CodeMetadata(
                "/test/File.java",
                "java",
                "com.test",
                "TestClass",
                List.of("field1", "field2"),
                List.of(method1, method2)
        );

        CodeChunker chunker = new CodeChunker();
        List<CodeChunker.CodeChunk> chunks = chunker.chunkCode(metadata);

        // Should create one chunk per method
        assertEquals(2, chunks.size());

        // Verify first chunk
        CodeChunker.CodeChunk chunk1 = chunks.get(0);
        assertEquals("/test/File.java", chunk1.filePath());
        assertEquals("java", chunk1.language());
        assertEquals("com.test", chunk1.packageName());
        assertEquals("TestClass", chunk1.className());
        assertEquals("testMethod", chunk1.methodName());
        assertEquals("{ System.out.println(\"test\"); }", chunk1.content());
        assertEquals(10, chunk1.startLine());
        assertEquals(15, chunk1.endLine());
        assertEquals(List.of("field1", "field2"), chunk1.properties());
        assertEquals(List.of("println"), chunk1.callees());

        // Verify context includes metadata
        String context1 = chunk1.getFullContext();
        assertTrue(context1.contains("Language: java"));
        assertTrue(context1.contains("Package: com.test"));
        assertTrue(context1.contains("Class: TestClass"));
        assertTrue(context1.contains("Method: testMethod"));
        assertTrue(context1.contains("Properties: field1, field2"));
    }

    @Test
    void testChunkingWithNoMethods() {
        CodeMetadata metadata = new CodeMetadata(
                "/test/Empty.java",
                "java",
                "com.test",
                "EmptyClass",
                List.of(),
                List.of()
        );

        CodeChunker chunker = new CodeChunker();
        List<CodeChunker.CodeChunk> chunks = chunker.chunkCode(metadata);

        // Should create one chunk even with no methods
        assertEquals(1, chunks.size());
        assertEquals("", chunks.get(0).methodName());
    }

    @Test
    void testTokenEstimation() {
        CodeMetadata.MethodInfo method = new CodeMetadata.MethodInfo(
                "largeMethod",
                "void",
                List.of(),
                "a".repeat(1000), // 1000 character method body
                1,
                10,
                List.of()
        );

        CodeMetadata metadata = new CodeMetadata(
                "/test/Large.java",
                "java",
                "com.test",
                "LargeClass",
                List.of(),
                List.of(method)
        );

        CodeChunker chunker = new CodeChunker();
        List<CodeChunker.CodeChunk> chunks = chunker.chunkCode(metadata);

        assertEquals(1, chunks.size());

        // Verify token estimation (should be reasonable)
        int estimatedTokens = chunks.get(0).estimateTokens();
        assertTrue(estimatedTokens > 0);
        assertTrue(estimatedTokens < 1000); // Less than max tokens
    }
}
