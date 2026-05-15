package com.ferraz.jarcomparator;

import io.quarkus.picocli.runtime.annotations.TopCommand;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import japicmp.model.*;
import picocli.CommandLine.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

@Command(
    name = "jar-comparator",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Downloads two versions of a Maven artifact and compares their public APIs using japicmp."
)
@TopCommand
@Dependent
public class JarComparatorCommand implements Callable<Integer> {

    private static final Predicate<JApiClass> IS_INCOMPATIBLE =
        c -> !c.isBinaryCompatible() || !c.isSourceCompatible();

    @Option(names = {"--old-version"}, required = true,
            description = "Old artifact coordinates: groupId:artifactId:version",
            paramLabel = "COORD")
    String oldCoord;

    @Option(names = {"--new-version"}, required = true,
            description = "New artifact coordinates: groupId:artifactId:version",
            paramLabel = "COORD")
    String newCoord;

    @Option(names = {"--access-modifier"}, defaultValue = "PUBLIC",
            description = "Minimum access level: PUBLIC, PROTECTED, PACKAGE, PRIVATE (default: PUBLIC)",
            paramLabel = "LEVEL",
            converter = AccessModifierConverter.class)
    AccessModifier accessModifier;

    @Option(names = {"--ignore-missing-classes"},
            description = "Ignore classes missing from the classpath")
    boolean ignoreMissingClasses;

    @Option(names = {"--only-incompatible"},
            description = "Show only binary or source incompatible changes")
    boolean onlyIncompatible;

    @Option(names = {"--output-html"},
            description = "Write an HTML report to this file",
            paramLabel = "FILE")
    Path htmlOutput;

    @Inject MavenCentralDownloader downloader;
    @Inject JarComparisonService comparisonService;
    @Inject HtmlReportGenerator htmlReportGenerator;

    @Override
    public Integer call() {
        MavenCoordinates oldC = MavenCoordinates.parse(oldCoord);
        MavenCoordinates newC = MavenCoordinates.parse(newCoord);

        VersionedJar oldV, newV;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var oldFuture = CompletableFuture.supplyAsync(() -> downloader.download(oldC), executor);
            var newFuture = CompletableFuture.supplyAsync(() -> downloader.download(newC), executor);
            oldV = new VersionedJar(oldC, oldFuture.join());
            newV = new VersionedJar(newC, newFuture.join());
        }

        List<JApiClass> results = comparisonService.compare(oldV, newV, accessModifier, ignoreMissingClasses);

        if (onlyIncompatible) {
            results = results.stream().filter(IS_INCOMPATIBLE).toList();
        }

        printTextReport(results, oldC, newC);

        if (htmlOutput != null) {
            Path parent = htmlOutput.toAbsolutePath().getParent();
            if (parent != null) {
                try {
                    Files.createDirectories(parent);
                } catch (IOException e) {
                    throw new JarComparatorException("Cannot create output directory: " + parent, e);
                }
            }
            htmlReportGenerator.generate(results, oldC, newC, htmlOutput);
            System.out.println("\nHTML report written to: " + htmlOutput.toAbsolutePath());
        }

        return results.stream().anyMatch(IS_INCOMPATIBLE) ? 1 : 0;
    }

    private void printTextReport(List<JApiClass> classes, MavenCoordinates oldC, MavenCoordinates newC) {
        String header = "Comparing: %s  →  %s  [access: %s]"
            .formatted(oldC, newC, accessModifier.name());
        String line = "─".repeat(Math.min(header.length(), 80));

        System.out.println(header);
        System.out.println(line);

        if (classes.isEmpty()) {
            System.out.println("No changes found.");
        } else {
            for (JApiClass cls : classes) {
                char symbol = statusSymbol(cls.getChangeStatus());
                String compatTag = IS_INCOMPATIBLE.test(cls) ? "  (INCOMPATIBLE)" : "";
                System.out.printf("%c %s  [%s]%s%n",
                    symbol, cls.getFullyQualifiedName(), cls.getChangeStatus(), compatTag);
                printMemberChanges(cls);
            }
        }

        System.out.println(line);
        printSummary(classes);
    }

    private void printMemberChanges(JApiClass cls) {
        cls.getMethods().stream()
            .filter(m -> m.getChangeStatus() != JApiChangeStatus.UNCHANGED)
            .forEach(m -> System.out.printf("    %c method: %s%n",
                statusSymbol(m.getChangeStatus()), m.getName()));
        cls.getConstructors().stream()
            .filter(c -> c.getChangeStatus() != JApiChangeStatus.UNCHANGED)
            .forEach(c -> System.out.printf("    %c constructor: %s%n",
                statusSymbol(c.getChangeStatus()), c.getName()));
        cls.getFields().stream()
            .filter(f -> f.getChangeStatus() != JApiChangeStatus.UNCHANGED)
            .forEach(f -> System.out.printf("    %c field: %s%n",
                statusSymbol(f.getChangeStatus()), f.getName()));
    }

    private void printSummary(List<JApiClass> classes) {
        long added = 0, removed = 0, modified = 0, unchanged = 0, incompatible = 0;
        for (var c : classes) {
            switch (c.getChangeStatus()) {
                case NEW      -> added++;
                case REMOVED  -> removed++;
                case MODIFIED -> modified++;
                default       -> unchanged++;
            }
            if (IS_INCOMPATIBLE.test(c)) incompatible++;
        }
        System.out.printf(
            "Summary: %d total  (+%d added  -%d removed  ~%d modified  =%d unchanged)  |  %d incompatible%n",
            classes.size(), added, removed, modified, unchanged, incompatible);
    }

    private char statusSymbol(JApiChangeStatus status) {
        return switch (status) {
            case NEW     -> '+';
            case REMOVED -> '-';
            case MODIFIED -> '~';
            default      -> '=';
        };
    }

    static class AccessModifierConverter implements ITypeConverter<AccessModifier> {
        @Override
        public AccessModifier convert(String value) throws Exception {
            return switch (value.toUpperCase()) {
                case "PUBLIC"    -> AccessModifier.PUBLIC;
                case "PROTECTED" -> AccessModifier.PROTECTED;
                case "PACKAGE"   -> AccessModifier.PACKAGE_PROTECTED;
                case "PRIVATE"   -> AccessModifier.PRIVATE;
                default -> throw new TypeConversionException(
                    "Invalid access modifier: \"" + value + "\". Use PUBLIC, PROTECTED, PACKAGE, or PRIVATE.");
            };
        }
    }
}
