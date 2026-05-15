package com.ferraz.jarcomparator;

import java.nio.file.Path;

record VersionedJar(MavenCoordinates coords, Path jar) {}
