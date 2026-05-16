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
import org.jboss.logging.Logger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Path("/")
@ApplicationScoped
public class JarComparatorResource {

    private static final Logger LOG = Logger.getLogger(JarComparatorResource.class);

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

    @GET
    @Path("/{groupId}/{artifactId}")
    @Produces(MediaType.TEXT_HTML)
    public String compareGet(
            @PathParam("groupId") String groupId,
            @PathParam("artifactId") String artifactId,
            @QueryParam("oldVersion") String oldVersion,
            @QueryParam("newVersion") String newVersion,
            @QueryParam("accessModifier") @DefaultValue("PUBLIC") String accessModifierParam,
            @QueryParam("ignoreMissingClasses") @DefaultValue("true") String ignoreMissingClassesParam,
            @QueryParam("onlyIncompatible") @DefaultValue("false") String onlyIncompatibleParam) {

        if (oldVersion == null || oldVersion.isBlank() || newVersion == null || newVersion.isBlank()) {
            return errorPage("Missing oldVersion or newVersion query parameters");
        }
        return doCompare(
            groupId + ":" + artifactId + ":" + oldVersion,
            groupId + ":" + artifactId + ":" + newVersion,
            accessModifierParam, ignoreMissingClassesParam, onlyIncompatibleParam);
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

        return doCompare(oldVersion, newVersion, accessModifierParam, ignoreMissingClassesParam, onlyIncompatibleParam);
    }

    private String doCompare(String oldVersion, String newVersion,
            String accessModifierParam, String ignoreMissingClassesParam, String onlyIncompatibleParam) {

        LOG.infof("Web Request: compare %s vs %s (access=%s, ignoreMissing=%s, onlyIncompat=%s)",
                oldVersion, newVersion, accessModifierParam, ignoreMissingClassesParam, onlyIncompatibleParam);

        try {
            MavenCoordinates oldC = MavenCoordinates.parse(oldVersion);
            MavenCoordinates newC = MavenCoordinates.parse(newVersion);

            if (oldC.version().equals(newC.version())) {
                return errorPage("Old and new versions are the same (" + oldC.version() + "). Please provide two different versions to compare.");
            }

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
            LOG.errorf(e, "Error during web comparison of %s and %s", oldVersion, newVersion);
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
        LOG.infof("Web Request: source-diff for %s between %s and %s", className, oldVersion, newVersion);

        if (className == null || !className.matches("[\\w.$]+")) {
            LOG.warnf("Invalid class name requested for diff: %s", className);
            return Response.status(400).entity("Invalid class name").build();
        }
        try {
            MavenCoordinates oldC = MavenCoordinates.parse(oldVersion);
            MavenCoordinates newC = MavenCoordinates.parse(newVersion);
            return sourceDiffService.diff(oldC, newC, className)
                .map(diff -> {
                    LOG.infof("Source diff generated for %s", className);
                    return Response.ok(diff).build();
                })
                .orElseGet(() -> {
                    LOG.warnf("Source not available for %s", className);
                    return Response.status(404).entity("Source not available for this artifact").build();
                });
        } catch (Exception e) {
            LOG.errorf(e, "Error during source diff for %s", className);
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
