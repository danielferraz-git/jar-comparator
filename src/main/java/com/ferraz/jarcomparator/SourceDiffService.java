package com.ferraz.jarcomparator;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@ApplicationScoped
public class SourceDiffService {

    @Inject
    MavenCentralDownloader downloader;

    public Optional<String> diff(MavenCoordinates oldC, MavenCoordinates newC, String fqn) {
        Optional<Path> oldSrc, newSrc;
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            var oldF = CompletableFuture.supplyAsync(() -> downloader.downloadSources(oldC), exec);
            var newF = CompletableFuture.supplyAsync(() -> downloader.downloadSources(newC), exec);
            oldSrc = oldF.join();
            newSrc = newF.join();
        }
        if (oldSrc.isEmpty() || newSrc.isEmpty()) return Optional.empty();

        String srcPath = fqnToSourcePath(fqn);
        Optional<String> oldCode = extractEntry(oldSrc.get(), srcPath);
        Optional<String> newCode = extractEntry(newSrc.get(), srcPath);
        if (oldCode.isEmpty() || newCode.isEmpty()) return Optional.empty();

        return Optional.of(unifiedDiff(fqn, oldC, newC, oldCode.get(), newCode.get()));
    }

    // Converts FQN to source path, handling inner classes.
    // e.g. com.example.Outer.Inner → com/example/Outer.java
    private String fqnToSourcePath(String fqn) {
        String[] parts = fqn.split("\\.");
        int i = 0;
        while (i < parts.length && !Character.isUpperCase(parts[i].charAt(0))) i++;
        return String.join("/", Arrays.copyOfRange(parts, 0, i + 1)) + ".java";
    }

    private Optional<String> extractEntry(Path jar, String entryPath) {
        try (var zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(entryPath);
            if (entry == null) return Optional.empty();
            try (var is = zip.getInputStream(entry)) {
                return Optional.of(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private String unifiedDiff(String fqn, MavenCoordinates oldC, MavenCoordinates newC,
                                String oldSrc, String newSrc) {
        List<String> oldLines = List.of(oldSrc.split("\n", -1));
        List<String> newLines = List.of(newSrc.split("\n", -1));
        Patch<String> patch = DiffUtils.diff(oldLines, newLines);
        List<String> unified = UnifiedDiffUtils.generateUnifiedDiff(
            fqn + "  (" + oldC + ")",
            fqn + "  (" + newC + ")",
            oldLines, patch, 3);
        return String.join("\n", unified);
    }
}
