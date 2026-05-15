package com.ferraz.jarcomparator;

public record MavenCoordinates(String groupId, String artifactId, String version) {

    public static MavenCoordinates parse(String coord) {
        String[] parts = coord.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                "Expected groupId:artifactId:version but got: \"" + coord + "\"");
        }
        return new MavenCoordinates(parts[0].trim(), parts[1].trim(), parts[2].trim());
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version;
    }
}
