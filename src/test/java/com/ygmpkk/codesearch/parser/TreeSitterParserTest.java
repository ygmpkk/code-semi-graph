package com.ygmpkk.codesearch.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

class TreeSitterParserTest {

    /**
     * This test only runs if tree-sitter native libraries are available
     * To enable: run with -Dtreesitter.available=true
     */
    @Test
    @EnabledIfSystemProperty(named = "treesitter.available", matches = "true")
    void testParseSimpleJavaClass() throws Exception {
        String javaCode = """
                package com.example;
                
                public class HelloWorld {
                    private String message;
                
                    public void sayHello() {
                        System.out.println(message);
                    }
                
                    public String getMessage() {
                        return message;
                    }
                }
                """;

        try (TreeSitterParser parser = new TreeSitterParser()) {
            CodeMetadata metadata = parser.parseJavaCode("/test/HelloWorld.java", javaCode);

            assertEquals("/test/HelloWorld.java", metadata.filePath());
            assertEquals("com.example", metadata.packageName());
            assertEquals("HelloWorld", metadata.className());
            assertEquals(1, metadata.properties().size());
            assertTrue(metadata.properties().contains("message"));
            assertEquals(2, metadata.methods().size());

            // Check first method
            CodeMetadata.MethodInfo method1 = metadata.methods().get(0);
            assertEquals("sayHello", method1.name());
            assertEquals("void", method1.returnType());

            // Check second method
            CodeMetadata.MethodInfo method2 = metadata.methods().get(1);
            assertEquals("getMessage", method2.name());
            assertEquals("String", method2.returnType());
        }
    }

    @Test
    void testParserInstantiationWithoutNativeLibs() {
        // This test verifies that parser can be constructed in environments
        // where tree-sitter native libraries are not available
        try {
            TreeSitterParser parser = new TreeSitterParser();
            parser.close();
            // If we get here, native libs are available - that's OK
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            System.out.println("Tree-sitter native libraries not available, as expected." + e.getMessage());
            // Expected in environments without native libraries
            assertTrue(e.getMessage().contains("ch.usi.si.seart.treesitter") ||
                            e.getMessage().contains("Language"),
                    "Error should be related to tree-sitter native library");
        }
    }
}
