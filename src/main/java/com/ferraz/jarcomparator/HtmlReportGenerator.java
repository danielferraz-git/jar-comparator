package com.ferraz.jarcomparator;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import japicmp.model.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class HtmlReportGenerator {

    @Inject
    @Location("report.html")
    Template reportTemplate;

    public String generateHtml(List<JApiClass> classes, MavenCoordinates oldC, MavenCoordinates newC) {
        List<ClassRow> rows = classes.stream().map(this::toRow).toList();
        long incompatibleCount = rows.stream().filter(ClassRow::incompatible).count();

        return reportTemplate
            .data("rows", rows)
            .data("oldCoord", oldC.toString())
            .data("newCoord", newC.toString())
            .data("totalCount", classes.size())
            .data("incompatibleCount", incompatibleCount)
            .render();
    }

    public void generate(List<JApiClass> classes, MavenCoordinates oldC, MavenCoordinates newC, Path outputPath) {
        String html = generateHtml(classes, oldC, newC);
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            w.print(html);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write HTML report to " + outputPath + ": " + e.getMessage(), e);
        }
    }

    private ClassRow toRow(JApiClass cls) {
        boolean binaryOk = cls.isBinaryCompatible();
        boolean sourceOk = cls.isSourceCompatible();

        List<ClassRow.MemberChange> changes = new ArrayList<>();
        cls.getMethods().stream()
            .filter(m -> m.getChangeStatus() != JApiChangeStatus.UNCHANGED)
            .map(m -> new ClassRow.MemberChange("method", m.getName(), m.getChangeStatus().name()))
            .forEach(changes::add);
        cls.getConstructors().stream()
            .filter(c -> c.getChangeStatus() != JApiChangeStatus.UNCHANGED)
            .map(c -> new ClassRow.MemberChange("constructor", "<init>", c.getChangeStatus().name()))
            .forEach(changes::add);
        cls.getFields().stream()
            .filter(f -> f.getChangeStatus() != JApiChangeStatus.UNCHANGED)
            .map(f -> new ClassRow.MemberChange("field", f.getName(), f.getChangeStatus().name()))
            .forEach(changes::add);

        return new ClassRow(
            cls.getFullyQualifiedName(),
            cls.getChangeStatus().name(),
            binaryOk,
            sourceOk,
            !binaryOk || !sourceOk,
            changes
        );
    }
}
