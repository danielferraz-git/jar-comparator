package com.ferraz.jarcomparator;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
    @Inject SourceDiffService sourceDiffService;

    @Inject @Location("form.html")  Template formTemplate;
    @Inject @Location("error.html") Template errorTemplate;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String form() {
        return formTemplate.render();
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

    @GET
    @Path("/source-diff")
    @Produces(MediaType.TEXT_PLAIN)
    public Response sourceDiff(
            @QueryParam("class") String className,
            @QueryParam("old")   String oldVersion,
            @QueryParam("new")   String newVersion) {
        if (className == null || !className.matches("[\\w.$]+")) {
            return Response.status(400).entity("Invalid class name").build();
        }
        try {
            MavenCoordinates oldC = MavenCoordinates.parse(oldVersion);
            MavenCoordinates newC = MavenCoordinates.parse(newVersion);
            return sourceDiffService.diff(oldC, newC, className)
                .map(diff -> Response.ok(diff).build())
                .orElse(Response.status(404).entity("Source not available for this artifact").build());
        } catch (Exception e) {
            return Response.status(500).entity(e.getMessage()).build();
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
        return errorTemplate
            .data("message", message == null ? "Unknown error" : message)
            .render();
    }
}
