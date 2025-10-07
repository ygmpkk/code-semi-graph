package com.ygmpkk.codesearch;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * Main CLI application for code-semi-graph
 * A command line tool for semi or graph code search
 */
@Command(
    name = "code-search",
    mixinStandardHelpOptions = true,
    version = "code-search 1.0.0",
    description = "A command line for semi or graph code search",
    subcommands = {
        SemiSearchCommand.class,
        GraphSearchCommand.class
    }
)
public class CodeSearchCLI implements Callable<Integer> {

    @Option(names = {"-v", "--verbose"}, description = "Verbose mode")
    private boolean verbose;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CodeSearchCLI()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        System.out.println("Use 'code-search --help' to see available commands");
        System.out.println("Available commands:");
        System.out.println("  semi  - Perform semi code search");
        System.out.println("  graph - Perform graph code search");
        return 0;
    }

    public boolean isVerbose() {
        return verbose;
    }
}
