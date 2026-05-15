package com.ferraz.jarcomparator;

import jakarta.enterprise.context.ApplicationScoped;
import japicmp.model.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class HtmlReportGenerator {

    public void generate(List<JApiClass> classes, MavenCoordinates oldC, MavenCoordinates newC, Path outputPath) {
        long incompatible = classes.stream()
            .filter(c -> !c.isBinaryCompatible() || !c.isSourceCompatible())
            .count();

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            w.println("""
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>API Comparison Report</title>
                  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
                  <style>
                    .badge-NEW       { background:#198754; }
                    .badge-REMOVED   { background:#dc3545; }
                    .badge-MODIFIED  { background:#fd7e14; }
                    .badge-UNCHANGED { background:#6c757d; }
                    .incompat        { background:#fff3cd; }
                    details summary  { cursor:pointer; }
                  </style>
                </head>
                <body class="p-4">
                """);

            w.printf("<h1 class=\"mb-1\">API Comparison Report</h1>%n");
            w.printf("<p class=\"text-muted mb-3\">%s &nbsp;&#8594;&nbsp; %s</p>%n",
                esc(oldC.toString()), esc(newC.toString()));

            w.printf("""
                <div class="row g-3 mb-4">
                  <div class="col-auto"><div class="card text-bg-secondary px-3 py-2"><b>%d</b> classes</div></div>
                  <div class="col-auto"><div class="card text-bg-danger px-3 py-2"><b>%d</b> incompatible</div></div>
                </div>
                """, classes.size(), incompatible);

            w.println("<table class=\"table table-sm table-bordered align-middle\">");
            w.println("<thead class=\"table-dark\"><tr>"
                + "<th>Class</th><th>Change</th><th>Binary</th><th>Source</th><th>Details</th></tr></thead>");
            w.println("<tbody>");

            for (JApiClass cls : classes) {
                boolean compat = cls.isBinaryCompatible() && cls.isSourceCompatible();
                String rowClass = compat ? "" : " class=\"incompat\"";
                String badge = changeBadge(cls.getChangeStatus());
                w.printf("<tr%s><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>%n",
                    rowClass,
                    esc(cls.getFullyQualifiedName()),
                    badge,
                    boolBadge(cls.isBinaryCompatible()),
                    boolBadge(cls.isSourceCompatible()),
                    detailsCell(cls));
            }

            w.println("</tbody></table></body></html>");
        } catch (IOException e) {
            throw new RuntimeException("Failed to write HTML report to " + outputPath + ": " + e.getMessage(), e);
        }
    }

    private String detailsCell(JApiClass cls) {
        var sb = new StringBuilder();

        List<JApiMethod> changedMethods = cls.getMethods().stream()
            .filter(m -> m.getChangeStatus() != JApiChangeStatus.UNCHANGED)
            .toList();
        List<JApiField> changedFields = cls.getFields().stream()
            .filter(f -> f.getChangeStatus() != JApiChangeStatus.UNCHANGED)
            .toList();
        List<JApiConstructor> changedCtors = cls.getConstructors().stream()
            .filter(c -> c.getChangeStatus() != JApiChangeStatus.UNCHANGED)
            .toList();

        if (changedMethods.isEmpty() && changedFields.isEmpty() && changedCtors.isEmpty()) {
            return "";
        }

        sb.append("<details><summary>show</summary><ul class=\"mb-0 mt-1\">");
        for (var m : changedMethods) {
            sb.append("<li>").append(changeBadge(m.getChangeStatus()))
              .append(" <code>").append(esc(m.getName())).append("()</code></li>");
        }
        for (var c : changedCtors) {
            sb.append("<li>").append(changeBadge(c.getChangeStatus()))
              .append(" <code>&lt;init&gt;()</code></li>");
        }
        for (var f : changedFields) {
            sb.append("<li>").append(changeBadge(f.getChangeStatus()))
              .append(" <code>").append(esc(f.getName())).append("</code></li>");
        }
        sb.append("</ul></details>");
        return sb.toString();
    }

    private String changeBadge(JApiChangeStatus status) {
        String label = status.toString();
        String cls = switch (status) {
            case NEW       -> "badge-NEW";
            case REMOVED   -> "badge-REMOVED";
            case MODIFIED  -> "badge-MODIFIED";
            default        -> "badge-UNCHANGED";
        };
        return "<span class=\"badge " + cls + "\">" + label + "</span>";
    }

    private String boolBadge(boolean ok) {
        return ok ? "<span class=\"badge text-bg-success\">yes</span>"
                  : "<span class=\"badge text-bg-danger\">no</span>";
    }

    private String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
