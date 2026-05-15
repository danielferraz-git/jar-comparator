package com.ferraz.jarcomparator;

import io.quarkus.qute.TemplateData;
import java.util.List;

@TemplateData
public record ClassRow(
    String fullyQualifiedName,
    String changeStatus,
    boolean binaryCompatible,
    boolean sourceCompatible,
    boolean incompatible,
    List<MemberGroup> groups,
    String diffBlock
) {
    @TemplateData
    public record MemberGroup(
        String kind,          // "Methods" | "Constructors" | "Fields"
        List<MemberChange> members,
        boolean uniform,      // all members share the same changeStatus
        String commonStatus   // non-empty only when uniform=true
    ) {}

    @TemplateData
    public record MemberChange(String name, String changeStatus) {}
}
