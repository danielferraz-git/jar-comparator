package com.ferraz.jarcomparator;

import io.quarkus.runtime.Quarkus;
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
        if (args.length == 0) {
            System.out.println("Web server running at http://localhost:8080");
            Quarkus.waitForExit();
            return 0;
        }
        return commandLine.execute(args);
    }
}
