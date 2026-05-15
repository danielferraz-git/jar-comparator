package com.ferraz.jarcomparator;

import jakarta.enterprise.context.ApplicationScoped;
import japicmp.cmp.JApiCmpArchive;
import japicmp.cmp.JarArchiveComparator;
import japicmp.cmp.JarArchiveComparatorOptions;
import japicmp.model.AccessModifier;
import japicmp.model.JApiClass;

import java.util.List;

@ApplicationScoped
public class JarComparisonService {

    public List<JApiClass> compare(VersionedJar old, VersionedJar nw,
                                   AccessModifier accessModifier, boolean ignoreMissingClasses) {
        JarArchiveComparatorOptions opts = new JarArchiveComparatorOptions();
        opts.setAccessModifier(accessModifier);
        opts.getIgnoreMissingClasses().setIgnoreAllMissingClasses(ignoreMissingClasses);

        JApiCmpArchive oldArchive = new JApiCmpArchive(old.jar().toFile(), old.coords().version());
        JApiCmpArchive newArchive = new JApiCmpArchive(nw.jar().toFile(), nw.coords().version());

        return new JarArchiveComparator(opts).compare(List.of(oldArchive), List.of(newArchive));
    }
}
