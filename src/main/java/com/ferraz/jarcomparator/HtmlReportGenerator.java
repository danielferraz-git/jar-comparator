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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class HtmlReportGenerator {

    @Inject
    @Location("report.html")
    Template reportTemplate;

    public String generateHtml(List<JApiClass> classes, MavenCoordinates oldC, MavenCoordinates newC) {
        String oldCoord = oldC.toString();
        String newCoord = newC.toString();
        List<ClassRow> rows = classes.stream()
            .map(c -> toRow(c, oldCoord, newCoord))
            .toList();
        long incompatibleCount = rows.stream().filter(ClassRow::incompatible).count();

        return reportTemplate
            .data("rows", rows)
            .data("oldCoord", oldCoord)
            .data("newCoord", newCoord)
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

    private ClassRow toRow(JApiClass cls, String oldCoord, String newCoord) {
        boolean binaryOk = cls.isBinaryCompatible();
        boolean sourceOk = cls.isSourceCompatible();

        List<ClassRow.MemberGroup> groups = new ArrayList<>();
        buildGroup("Methods",      cls.getMethods(),      JApiMethod::getChangeStatus,      JApiMethod::getName).ifPresent(groups::add);
        buildGroup("Constructors", cls.getConstructors(), JApiConstructor::getChangeStatus, c -> "<init>").ifPresent(groups::add);
        buildGroup("Fields",       cls.getFields(),       JApiField::getChangeStatus,       JApiField::getName).ifPresent(groups::add);

        return new ClassRow(
            cls.getFullyQualifiedName(),
            cls.getChangeStatus().name(),
            binaryOk, sourceOk, !binaryOk || !sourceOk,
            groups,
            buildDiffBlock(cls, oldCoord, newCoord)
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

    // --- diff block ---

    private String buildDiffBlock(JApiClass cls, String oldCoord, String newCoord) {
        String fqn = cls.getFullyQualifiedName();
        String simpleName = fqn.substring(fqn.lastIndexOf('.') + 1);
        List<String> lines = new ArrayList<>();

        appendSection(lines, "Methods", buildMethodLines(cls.getMethods()));
        appendSection(lines, "Constructors", buildCtorLines(cls.getConstructors(), simpleName));
        appendSection(lines, "Fields", buildFieldLines(cls.getFields()));

        if (lines.isEmpty()) return "";

        var sb = new StringBuilder();
        sb.append("--- ").append(fqn).append("  (").append(oldCoord).append(")\n");
        sb.append("+++ ").append(fqn).append("  (").append(newCoord).append(")");
        for (String line : lines) sb.append("\n").append(line);
        return sb.toString();
    }

    private List<String> buildMethodLines(List<JApiMethod> methods) {
        List<String> out = new ArrayList<>();
        for (JApiMethod m : methods) {
            switch (m.getChangeStatus()) {
                case NEW      -> out.add("+ " + formatMethod(m, true));
                case REMOVED  -> out.add("- " + formatMethod(m, false));
                case MODIFIED -> {
                    String o = formatMethod(m, false), n = formatMethod(m, true);
                    out.add(o.equals(n) ? "~ " + n : "~ " + o + "  →  " + n);
                }
                default -> {}
            }
        }
        return out;
    }

    private List<String> buildCtorLines(List<JApiConstructor> ctors, String simpleName) {
        List<String> out = new ArrayList<>();
        for (JApiConstructor c : ctors) {
            switch (c.getChangeStatus()) {
                case NEW      -> out.add("+ " + formatConstructor(c, simpleName, true));
                case REMOVED  -> out.add("- " + formatConstructor(c, simpleName, false));
                case MODIFIED -> {
                    String o = formatConstructor(c, simpleName, false), n = formatConstructor(c, simpleName, true);
                    out.add(o.equals(n) ? "~ " + n : "~ " + o + "  →  " + n);
                }
                default -> {}
            }
        }
        return out;
    }

    private List<String> buildFieldLines(List<JApiField> fields) {
        List<String> out = new ArrayList<>();
        for (JApiField f : fields) {
            switch (f.getChangeStatus()) {
                case NEW      -> out.add("+ " + formatField(f, true));
                case REMOVED  -> out.add("- " + formatField(f, false));
                case MODIFIED -> {
                    String o = formatField(f, false), n = formatField(f, true);
                    out.add(o.equals(n) ? "~ " + n : "~ " + o + "  →  " + n);
                }
                default -> {}
            }
        }
        return out;
    }

    private void appendSection(List<String> lines, String header, List<String> sectionLines) {
        if (sectionLines.isEmpty()) return;
        lines.add("");
        lines.add(header);
        lines.addAll(sectionLines);
    }

    // --- signature formatters ---

    private String formatMethod(JApiMethod m, boolean useNew) {
        String mod = modifierStr(useNew
            ? m.getAccessModifier().getNewModifier().orElse(null)
            : m.getAccessModifier().getOldModifier().orElse(null));
        String ret = orUnknown(useNew
            ? m.getReturnType().getNewReturnType()
            : m.getReturnType().getOldReturnType());
        String params = buildParams(m.getParameters(), useNew);
        return (mod.isEmpty() ? "" : mod + " ") + ret + " " + m.getName() + "(" + params + ")";
    }

    private String formatField(JApiField f, boolean useNew) {
        String mod = modifierStr(useNew
            ? f.getAccessModifier().getNewModifier().orElse(null)
            : f.getAccessModifier().getOldModifier().orElse(null));
        String type = orUnknown(useNew ? f.getType().getNewValue() : f.getType().getOldValue());
        return (mod.isEmpty() ? "" : mod + " ") + type + " " + f.getName();
    }

    private String formatConstructor(JApiConstructor c, String simpleName, boolean useNew) {
        String mod = modifierStr(useNew
            ? c.getAccessModifier().getNewModifier().orElse(null)
            : c.getAccessModifier().getOldModifier().orElse(null));
        String params = buildParams(c.getParameters(), useNew);
        return (mod.isEmpty() ? "" : mod + " ") + simpleName + "(" + params + ")";
    }

    private String buildParams(List<JApiParameter> params, boolean useNew) {
        return params.stream()
            .filter(p -> useNew
                ? p.getChangeStatus() != JApiChangeStatus.REMOVED
                : p.getChangeStatus() != JApiChangeStatus.NEW)
            .map(JApiParameter::getType)
            .collect(Collectors.joining(", "));
    }

    private String modifierStr(AccessModifier mod) {
        if (mod == null) return "";
        return switch (mod) {
            case PUBLIC            -> "public";
            case PROTECTED         -> "protected";
            case PACKAGE_PROTECTED -> "";
            case PRIVATE           -> "private";
        };
    }

    private String orUnknown(String s) {
        return (s == null || s.isBlank()) ? "?" : s;
    }
}
