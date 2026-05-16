package com.ferraz.jarcomparator;

import jakarta.enterprise.context.ApplicationScoped;
import japicmp.cmp.JApiCmpArchive;
import japicmp.cmp.JarArchiveComparator;
import japicmp.cmp.JarArchiveComparatorOptions;
import japicmp.model.AccessModifier;
import japicmp.model.JApiClass;

import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class JarComparisonService {

    private static final Logger LOG = Logger.getLogger(JarComparisonService.class);

    public List<JApiClass> compare(VersionedJar old, VersionedJar nw,
                                   AccessModifier accessModifier, boolean ignoreMissingClasses) {
        LOG.infof("Comparing %s vs %s (access: %s, ignoreMissing: %b)",
                old.coords(), nw.coords(), accessModifier, ignoreMissingClasses);

        JarArchiveComparatorOptions opts = new JarArchiveComparatorOptions();
        opts.setAccessModifier(accessModifier);
        opts.getIgnoreMissingClasses().setIgnoreAllMissingClasses(ignoreMissingClasses);

        JApiCmpArchive oldArchive = new JApiCmpArchive(old.jar().toFile(), old.coords().version());
        JApiCmpArchive newArchive = new JApiCmpArchive(nw.jar().toFile(), nw.coords().version());

        List<JApiClass> results = new JarArchiveComparator(opts).compare(List.of(oldArchive), List.of(newArchive));
        LOG.infof("Comparison completed. Found %d classes with changes.", results.size());
        return results;
    }
}
