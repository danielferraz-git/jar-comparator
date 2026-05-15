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
        return cache.computeIfAbsent(coords.toString(), k -> doDownload(coords));
    }

    private Path doDownload(MavenCoordinates coords) {
        String url = buildUrl(coords);
        System.out.printf("Downloading %s ...%n", coords);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMinutes(5))
            .GET()
            .build();

        try {
            Path tempFile = Files.createTempFile("japicmp-" + coords.artifactId() + "-", ".jar");
            tempFile.toFile().deleteOnExit();

            HttpResponse<Path> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofFile(tempFile));

            if (response.statusCode() == 404) {
                Files.deleteIfExists(tempFile);
                throw new RuntimeException(
                    "Artifact not found on Maven Central: " + coords + "\n  URL: " + url);
            }
            if (response.statusCode() != 200) {
                Files.deleteIfExists(tempFile);
                throw new RuntimeException(
                    "Failed to download " + coords + " (HTTP " + response.statusCode() + ")\n  URL: " + url);
            }

            return tempFile;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Error downloading " + coords + ": " + e.getMessage(), e);
        }
    }

    private String buildUrl(MavenCoordinates c) {
        String groupPath = c.groupId().replace('.', '/');
        return "%s/%s/%s/%s/%s-%s.jar".formatted(
            CENTRAL_BASE, groupPath, c.artifactId(), c.version(),
            c.artifactId(), c.version());
    }
}
