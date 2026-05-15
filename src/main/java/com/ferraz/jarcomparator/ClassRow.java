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
    List<MemberChange> changes
) {
    @TemplateData
    public record MemberChange(String kind, String name, String changeStatus) {}
}
