package com.ygmpkk.codesearch;

import com.ygmpkk.codesearch.analyzer.ClassInfo;
import com.ygmpkk.codesearch.analyzer.CodeChunk;
import com.ygmpkk.codesearch.analyzer.FileAnalysisResult;
import com.ygmpkk.codesearch.analyzer.MethodCall;
import com.ygmpkk.codesearch.analyzer.MethodInfo;
import com.ygmpkk.codesearch.analyzer.TokenAwareChunker;
import com.ygmpkk.codesearch.analyzer.TreeSitterCodeAnalyzer;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TreeSitterCodeAnalyzerTest {

    @Test
    void analyzesJavaClassAndFindsMethodCalls() {
        String source = "package demo;\n"
                + "public class Sample {\n"
                + "  private int value;\n"
                + "  public void foo() {\n"
                + "    bar();\n"
                + "  }\n"
                + "  private void bar() {\n"
                + "    System.out.println(\"hi\");\n"
                + "  }\n"
                + "}\n";

        TreeSitterCodeAnalyzer analyzer = new TreeSitterCodeAnalyzer();
        Optional<FileAnalysisResult> result = analyzer.analyze(Paths.get("Sample.java"), source);

        assertTrue(result.isPresent(), "analysis should succeed for Java source");

        FileAnalysisResult analysis = result.get();
        assertEquals("demo", analysis.getPackageName());
        assertEquals(1, analysis.getClasses().size());

        ClassInfo classInfo = analysis.getClasses().get(0);
        assertEquals("Sample", classInfo.getName());
        assertTrue(classInfo.getFields().contains("value"));
        assertEquals(2, classInfo.getMethods().size());

        MethodInfo fooMethod = classInfo.getMethods().stream()
                .filter(method -> method.getName().equals("foo"))
                .findFirst()
                .orElseThrow();

        List<MethodCall> calls = fooMethod.getMethodCalls();
        assertEquals(1, calls.size());
        assertEquals("bar", calls.get(0).name());
    }

    @Test
    void chunkerBuildsMetadataForMethod() {
        String source = "package demo;\n"
                + "public class Sample {\n"
                + "  private int value;\n"
                + "  public void foo() { bar(); }\n"
                + "  private void bar() { value++; }\n"
                + "}\n";

        TreeSitterCodeAnalyzer analyzer = new TreeSitterCodeAnalyzer();
        Optional<FileAnalysisResult> result = analyzer.analyze(Paths.get("Sample.java"), source);
        assertTrue(result.isPresent());

        ClassInfo classInfo = result.get().getClasses().get(0);
        MethodInfo fooMethod = classInfo.getMethods().stream()
                .filter(method -> method.getName().equals("foo"))
                .findFirst()
                .orElseThrow();

        TokenAwareChunker chunker = new TokenAwareChunker();
        List<CodeChunk> chunks = chunker.chunkMethod(Paths.get("Sample.java"), result.get().getPackageName(), classInfo, fooMethod);

        assertFalse(chunks.isEmpty());
        CodeChunk chunk = chunks.get(0);
        String embeddingText = chunk.toEmbeddingText();

        assertTrue(embeddingText.contains("Package: demo"));
        assertTrue(embeddingText.contains("Class: Sample"));
        assertTrue(embeddingText.contains("Method: foo"));
        assertTrue(embeddingText.contains("bar"));
    }
}
