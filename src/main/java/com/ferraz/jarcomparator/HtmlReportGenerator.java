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
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        List<ClassRow.MemberGroup> groups = new ArrayList<>();
        buildGroup("Methods",      cls.getMethods(),      JApiMethod::getChangeStatus,      JApiMethod::getName)     .ifPresent(groups::add);
        buildGroup("Constructors", cls.getConstructors(), JApiConstructor::getChangeStatus, c -> "<init>")           .ifPresent(groups::add);
        buildGroup("Fields",       cls.getFields(),       JApiField::getChangeStatus,       JApiField::getName)      .ifPresent(groups::add);

        return new ClassRow(
            cls.getFullyQualifiedName(),
            cls.getChangeStatus().name(),
            binaryOk, sourceOk, !binaryOk || !sourceOk,
            groups
        );
    }

    private <T> Optional<ClassRow.MemberGroup> buildGroup(
            String kind,
            List<T> items,
            Function<T, JApiChangeStatus> statusFn,
            Function<T, String> nameFn) {
        List<ClassRow.MemberChange> members = items.stream()
            .filter(i -> statusFn.apply(i) != JApiChangeStatus.UNCHANGED)
            .map(i -> new ClassRow.MemberChange(nameFn.apply(i), statusFn.apply(i).name()))
            .toList();
        if (members.isEmpty()) return Optional.empty();
        Set<String> statuses = members.stream()
            .map(ClassRow.MemberChange::changeStatus)
            .collect(Collectors.toSet());
        boolean uniform = statuses.size() == 1;
        return Optional.of(new ClassRow.MemberGroup(kind, members, uniform,
            uniform ? statuses.iterator().next() : ""));
    }
}
