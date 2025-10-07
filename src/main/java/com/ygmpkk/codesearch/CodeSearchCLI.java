package com.ygmpkk.codesearch;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.ParseResult;

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
    private static Logger logger = LogManager.getLogger(CodeSearchCLI.class);

    @Mixin
    public LoggingMixin loggingMixin;

    public static void main(String[] args) {
        CodeSearchCLI app = new CodeSearchCLI();
        int exitCode = new CommandLine(app)
                .setExecutionStrategy(app::executionStrategy)
                .execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        logger.info("Use 'code-search --help' to see available commands");
        logger.info("Available commands:");
        logger.info("  semi  - Perform semi code search operations");
        logger.info("          - semi <query> [options]  : Search code");
        logger.info("          - semi build [options]    : Build embedding index");
        logger.info("  graph - Perform graph code search");
        return 0;
    }

    private Level calcLogLevel() {
        switch (loggingMixin.verbosity.length) {
            case 0:  return Level.WARN;
            case 1:  return Level.INFO;
            case 2:  return Level.DEBUG;
            default: return Level.TRACE;
        }
    }

    // A reference to this method can be used as a custom execution strategy
    // that first configures Log4j based on the specified verbosity level,
    // and then delegates to the default execution strategy.
    private int executionStrategy(ParseResult parseResult) {
        Configurator.setRootLevel(calcLogLevel());
        return new CommandLine.RunLast().execute(parseResult);
    }
}
