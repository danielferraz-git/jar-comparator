package com.ferraz.jarcomparator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import japicmp.model.JApiClass;
import japicmp.model.AccessModifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Path("/")
@ApplicationScoped
public class JarComparatorResource {

    @Inject MavenCentralDownloader downloader;
    @Inject JarComparisonService comparisonService;
    @Inject HtmlReportGenerator htmlReportGenerator;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String form() {
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>JAR Comparator</title>
              <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
              <style>
                body { background: #f8f9fa; }
                .card { max-width: 600px; margin: 60px auto; }
                #overlay { display:none; position:fixed; inset:0; background:rgba(0,0,0,.5);
                           z-index:9999; align-items:center; justify-content:center; }
                #overlay.show { display:flex; }
              </style>
            </head>
            <body>
              <div id="overlay" role="status" aria-live="polite">
                <div class="text-white text-center">
                  <div class="spinner-border mb-3" style="width:3rem;height:3rem;"></div>
                  <div class="fs-5">Running comparison, please wait…</div>
                </div>
              </div>
              <div class="card shadow-sm p-4">
                <h2 class="mb-4">JAR Comparator</h2>
                <form method="post" action="/compare"
                      onsubmit="document.getElementById('overlay').classList.add('show')">
                  <div class="mb-3">
                    <label class="form-label fw-semibold" for="oldVersion">Old Version</label>
                    <input class="form-control font-monospace" id="oldVersion" name="oldVersion"
                           placeholder="groupId:artifactId:version" required>
                  </div>
                  <div class="mb-3">
                    <label class="form-label fw-semibold" for="newVersion">New Version</label>
                    <input class="form-control font-monospace" id="newVersion" name="newVersion"
                           placeholder="groupId:artifactId:version" required>
                  </div>
                  <div class="mb-3">
                    <label class="form-label fw-semibold" for="accessModifier">Access Modifier</label>
                    <select class="form-select" id="accessModifier" name="accessModifier">
                      <option value="PUBLIC" selected>PUBLIC</option>
                      <option value="PROTECTED">PROTECTED</option>
                      <option value="PACKAGE">PACKAGE</option>
                      <option value="PRIVATE">PRIVATE</option>
                    </select>
                  </div>
                  <div class="mb-2 form-check">
                    <input class="form-check-input" type="checkbox" id="ignoreMissingClasses"
                           name="ignoreMissingClasses" value="true" checked>
                    <label class="form-check-label" for="ignoreMissingClasses">Ignore missing classes</label>
                  </div>
                  <div class="mb-4 form-check">
                    <input class="form-check-input" type="checkbox" id="onlyIncompatible"
                           name="onlyIncompatible" value="true">
                    <label class="form-check-label" for="onlyIncompatible">Only show incompatible changes</label>
                  </div>
                  <button type="submit" class="btn btn-primary w-100">Compare</button>
                </form>
              </div>
            </body>
            </html>
            """;
    }

    @POST
    @Path("/compare")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public String compare(
            @FormParam("oldVersion") String oldVersion,
            @FormParam("newVersion") String newVersion,
            @FormParam("accessModifier") @DefaultValue("PUBLIC") String accessModifierParam,
            @FormParam("ignoreMissingClasses") @DefaultValue("false") String ignoreMissingClassesParam,
            @FormParam("onlyIncompatible") @DefaultValue("false") String onlyIncompatibleParam) {

        try {
            MavenCoordinates oldC = MavenCoordinates.parse(oldVersion);
            MavenCoordinates newC = MavenCoordinates.parse(newVersion);

            AccessModifier accessModifier = parseAccessModifier(accessModifierParam);
            boolean ignoreMissingClasses = "true".equalsIgnoreCase(ignoreMissingClassesParam);
            boolean onlyIncompatible = "true".equalsIgnoreCase(onlyIncompatibleParam);

            VersionedJar oldV, newV;
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var oldFuture = CompletableFuture.supplyAsync(() -> downloader.download(oldC), executor);
                var newFuture = CompletableFuture.supplyAsync(() -> downloader.download(newC), executor);
                oldV = new VersionedJar(oldC, oldFuture.join());
                newV = new VersionedJar(newC, newFuture.join());
            }

            List<JApiClass> results = comparisonService.compare(oldV, newV, accessModifier, ignoreMissingClasses);

            if (onlyIncompatible) {
                results = results.stream()
                    .filter(c -> !c.isBinaryCompatible() || !c.isSourceCompatible())
                    .toList();
            }

            return htmlReportGenerator.generateHtml(results, oldC, newC);

        } catch (Exception e) {
            return errorPage(e.getMessage());
        }
    }

    private AccessModifier parseAccessModifier(String value) {
        return switch (value.toUpperCase()) {
            case "PROTECTED" -> AccessModifier.PROTECTED;
            case "PACKAGE"   -> AccessModifier.PACKAGE_PROTECTED;
            case "PRIVATE"   -> AccessModifier.PRIVATE;
            default          -> AccessModifier.PUBLIC;
        };
    }

    private String errorPage(String message) {
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <title>Error – JAR Comparator</title>
              <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
            </head>
            <body class="p-4">
              <div class="alert alert-danger">
                <h4 class="alert-heading">Comparison failed</h4>
                <p class="mb-2">%s</p>
                <a href="/" class="btn btn-outline-danger btn-sm">Try again</a>
              </div>
            </body>
            </html>
            """.formatted(message == null ? "Unknown error" : message.replace("<", "&lt;").replace(">", "&gt;"));
    }
}
