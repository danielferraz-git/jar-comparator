package com.ferraz.jarcomparator;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class MavenCentralDownloader {

    private static final String CENTRAL_BASE = "https://repo1.maven.org/maven2";

    private final HttpClient httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    private final Map<String, Path> cache = new ConcurrentHashMap<>();

    public Path download(MavenCoordinates coords) {
        return cache.computeIfAbsent(coords.toString(), k -> {
            System.out.printf("Downloading %s ...%n", coords);
            return fetchFile(buildUrl(coords), coords.artifactId())
                .orElseThrow(() -> new RuntimeException(
                    "Artifact not found on Maven Central: " + coords + "\n  URL: " + buildUrl(coords)));
        });
    }

    public Optional<Path> downloadSources(MavenCoordinates coords) {
        String key = coords + ":sources";
        if (cache.containsKey(key)) return Optional.of(cache.get(key));
        System.out.printf("Downloading sources %s ...%n", coords);
        Optional<Path> result = fetchFile(buildSourcesUrl(coords), coords.artifactId() + "-sources");
        result.ifPresent(p -> cache.put(key, p));
        return result;
    }

    private Optional<Path> fetchFile(String url, String prefix) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMinutes(5))
            .GET()
            .build();

        try {
            Path tempFile = Files.createTempFile("japicmp-" + prefix + "-", ".jar");
            tempFile.toFile().deleteOnExit();

            HttpResponse<Path> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofFile(tempFile));

            if (response.statusCode() == 404) {
                Files.deleteIfExists(tempFile);
                return Optional.empty();
            }
            if (response.statusCode() != 200) {
                Files.deleteIfExists(tempFile);
                throw new RuntimeException(
                    "Failed to download " + url + " (HTTP " + response.statusCode() + ")");
            }

            return Optional.of(tempFile);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new RuntimeException("Error downloading " + url + ": " + e.getMessage(), e);
        }
    }

    private String buildUrl(MavenCoordinates c) {
        String groupPath = c.groupId().replace('.', '/');
        return "%s/%s/%s/%s/%s-%s.jar".formatted(
            CENTRAL_BASE, groupPath, c.artifactId(), c.version(),
            c.artifactId(), c.version());
    }

    private String buildSourcesUrl(MavenCoordinates c) {
        String groupPath = c.groupId().replace('.', '/');
        return "%s/%s/%s/%s/%s-%s-sources.jar".formatted(
            CENTRAL_BASE, groupPath, c.artifactId(), c.version(),
            c.artifactId(), c.version());
    }
}
