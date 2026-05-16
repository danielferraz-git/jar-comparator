package com.ferraz.jarcomparator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MavenCoordinatesTest {

    @Test
    void parsesValidCoordinate() {
        MavenCoordinates coord = MavenCoordinates.parse("com.google.guava:guava:32.0-jre");
        assertEquals("com.google.guava", coord.groupId());
        assertEquals("guava", coord.artifactId());
        assertEquals("32.0-jre", coord.version());
    }

    @Test
    void trimsWhitespace() {
        MavenCoordinates coord = MavenCoordinates.parse("  com.example : my-lib : 1.0.0 ");
        assertEquals("com.example", coord.groupId());
        assertEquals("my-lib", coord.artifactId());
        assertEquals("1.0.0", coord.version());
    }

    @Test
    void throwsOnMissingVersion() {
        assertThrows(IllegalArgumentException.class,
            () -> MavenCoordinates.parse("com.example:my-lib"));
    }

    @Test
    void throwsOnExtraSegments() {
        assertThrows(IllegalArgumentException.class,
            () -> MavenCoordinates.parse("com.example:my-lib:1.0.0:extra"));
    }

    @Test
    void toStringRoundtrips() {
        String coord = "com.example:my-lib:1.0.0";
        assertEquals(coord, MavenCoordinates.parse(coord).toString());
    }
}
