package com.cookiecraftmods.roadbuilder.preset;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RoadPackArchiveTest {
    @Test
    void rejectsResourceOnlyArchives() throws IOException {
        byte[] archive = archive("assets/example/road_presets/main.json", new byte[]{'{', '}'});

        assertThrows(IllegalArgumentException.class, () -> RoadPackArchive.read(archive));
    }

    @Test
    void expandedSizeLimitAppliesToNonPresetEntries() throws IOException {
        byte[] archive = archive("pack.png", new byte[65_537]);

        assertThrows(IllegalArgumentException.class, () -> RoadPackArchive.read(archive));
    }

    private static byte[] archive(String entry, byte[] contents) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(contents);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
