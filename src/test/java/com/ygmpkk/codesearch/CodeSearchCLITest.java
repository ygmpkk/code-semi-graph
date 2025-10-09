package com.ygmpkk.codesearch;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CodeSearchCLI
 */
public class CodeSearchCLITest {

    @Test
    public void testMainCommandWithoutArgs() {
        CodeSearchCLI app = new CodeSearchCLI();
        CommandLine cmd = new CommandLine(app);
        
        int exitCode = cmd.execute();
        assertEquals(0, exitCode, "Main command should return exit code 0");
    }

    @Test
    public void testHelpOption() {
        CodeSearchCLI app = new CodeSearchCLI();
        CommandLine cmd = new CommandLine(app);
        
        int exitCode = cmd.execute("--help");
        assertEquals(0, exitCode, "Help option should return exit code 0");
    }

    @Test
    public void testVersionOption() {
        CodeSearchCLI app = new CodeSearchCLI();
        CommandLine cmd = new CommandLine(app);
        
        int exitCode = cmd.execute("--version");
        assertEquals(0, exitCode, "Version option should return exit code 0");
    }

    @Test
    public void testSemiSearchCommand() {
        CodeSearchCLI app = new CodeSearchCLI();
        CommandLine cmd = new CommandLine(app);
        
        int exitCode = cmd.execute("semi", "test-query");
        assertEquals(0, exitCode, "Semi search command should return exit code 0");
    }

    @Test
    public void testSemiSearchCommandWithOptions() {
        CodeSearchCLI app = new CodeSearchCLI();
        CommandLine cmd = new CommandLine(app);
        
        int exitCode = cmd.execute("semi", "test-query", "--path", "/tmp", "--depth", "3", "--extensions", "java,kt");
        assertEquals(0, exitCode, "Semi search with options should return exit code 0");
    }

    @Test
    public void testGraphSearchCommand() {
        CodeSearchCLI app = new CodeSearchCLI();
        CommandLine cmd = new CommandLine(app);
        
        int exitCode = cmd.execute("graph", "test-node");
        // Should return 1 because database doesn't exist
        assertEquals(1, exitCode, "Graph search command should return exit code 1 when database doesn't exist");
    }

    @Test
    public void testGraphSearchCommandWithOptions() {
        CodeSearchCLI app = new CodeSearchCLI();
        CommandLine cmd = new CommandLine(app);
        
        int exitCode = cmd.execute("graph", "test-node", "--path", "/tmp", "--traversal", "DFS", "--max-nodes", "100");
        // Should return 1 because database doesn't exist
        assertEquals(1, exitCode, "Graph search with options should return exit code 1 when database doesn't exist");
    }

    @Test
    public void testSemiBuildCommand() {
        CodeSearchCLI app = new CodeSearchCLI();
        CommandLine cmd = new CommandLine(app);
        
        int exitCode = cmd.execute("semi", "build", "--path", "/tmp");
        assertEquals(0, exitCode, "Semi build command should return exit code 0");
    }

    @Test
    public void testSemiBuildCommandWithOptions() {
        CodeSearchCLI app = new CodeSearchCLI();
        CommandLine cmd = new CommandLine(app);
        
        int exitCode = cmd.execute("semi", "build", "--path", "/tmp", "--output", "/tmp/test-index", "--extensions", "java,kt", "--batch-size", "16");
        assertEquals(0, exitCode, "Semi build with options should return exit code 0");
    }
}
