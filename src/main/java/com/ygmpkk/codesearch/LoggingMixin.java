package com.ygmpkk.codesearch;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import static picocli.CommandLine.Spec.Target.MIXEE;

/**
 * Logging mixin for configuring verbosity across all commands
 */
public class LoggingMixin {
    private @Spec(MIXEE) CommandSpec mixee;

    boolean[] verbosity = new boolean[0];

    /**
     * Sets the specified verbosity on the LoggingMixin of the top-level command.
     * @param verbosity the new verbosity value
     */
    @Option(names = {"-v", "--verbose"}, description = {
            "Specify multiple -v options to increase verbosity.",
            "For example, `-v -v -v` or `-vvv`"})
    public void setVerbose(boolean[] verbosity) {
        // Each subcommand that mixes in the LoggingMixin has its own instance
        // of this class, so there may be many LoggingMixin instances.
        // We want to store the verbosity value in a single, central place,
        // so we find the top-level command,
        // and store the verbosity level on our top-level command's LoggingMixin.
        ((CodeSearchCLI) mixee.root().userObject()).loggingMixin.verbosity = verbosity;
    }
}
