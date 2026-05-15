package com.ferraz.jarcomparator;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import picocli.CommandLine;

@QuarkusMain
public class Main implements QuarkusApplication {

    @Inject
    CommandLine commandLine;

    @Override
    public int run(String... args) {
        return commandLine.execute(args);
    }
}
