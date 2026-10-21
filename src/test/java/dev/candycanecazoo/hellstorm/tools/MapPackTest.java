package dev.candycanecazoo.hellstorm.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Packs come from strangers: anything that isn't a map pack's own kind of file, or that would leave its folder, is refused. */
class MapPackTest {
    private static final String MANIFEST = "{\"format\":1,\"name\":\"t\",\"origin\":[0,0,0],\"size\":[1,1,1],\"map\":{}}";

    private static Path zip(Path dir, Map<String, String> entries) throws IOException {
        Path file = dir.resolve("t.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return file;
    }

    @Test
    void aManifestAndADatapackFileRead(@TempDir Path dir) throws Exception {
        MapPack.Contents contents = MapPack.read(zip(dir, Map.of("map.json", MANIFEST, "datapack/pack.mcmeta", "{}")));
        assertTrue(contents.datapack().containsKey("pack.mcmeta"));
    }

    @Test
    void pathsThatClimbOutOrUnknownFilesAreRefused(@TempDir Path dir) throws IOException {
        assertThrows(MapPack.BadPack.class, () -> MapPack.read(zip(dir, Map.of("map.json", MANIFEST, "datapack/../../evil.lua", "x"))));
        assertThrows(MapPack.BadPack.class, () -> MapPack.read(zip(dir, Map.of("map.json", MANIFEST, "../evil", "x"))));
        assertThrows(MapPack.BadPack.class, () -> MapPack.read(zip(dir, Map.of("map.json", MANIFEST, "mods/evil.jar", "x"))));
        assertThrows(MapPack.BadPack.class, () -> MapPack.read(zip(dir, Map.of("datapack/pack.mcmeta", "{}"))), "no map.json");
        assertThrows(MapPack.BadPack.class, () -> MapPack.read(zip(dir, Map.of("map.json", MANIFEST.replace("\"format\":1", "\"format\":9")))));
    }

    @Test
    void aPieceOutsideTheMapOrADamagedOneIsRefused(@TempDir Path dir) throws IOException {
        java.io.ByteArrayOutputStream piece = new java.io.ByteArrayOutputStream();
        net.minecraft.nbt.NbtIo.write(new net.minecraft.nbt.CompoundTag(), new java.io.DataOutputStream(piece));
        Path file = dir.resolve("p.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("map.json"));
            zip.write(MANIFEST.getBytes(StandardCharsets.UTF_8));
            zip.putNextEntry(new ZipEntry("structure/1_0_0.nbt"));
            zip.write(piece.toByteArray());
        }
        MapPack.BadPack outside = assertThrows(MapPack.BadPack.class, () -> MapPack.read(file));
        assertTrue(outside.getMessage().contains("outside the map"), outside.getMessage());
        MapPack.BadPack damaged = assertThrows(MapPack.BadPack.class, () -> MapPack.read(zip(dir, Map.of("map.json", MANIFEST, "structure/0_0_0.nbt", ""))));
        assertTrue(damaged.getMessage().contains("damaged"), damaged.getMessage());
    }

    @Test
    void aDatapackIsWrittenOnlyInsideItsFolder(@TempDir Path dir) throws IOException {
        Path datapacks = Files.createDirectories(dir.resolve("datapacks"));
        MapPack.installDatapack(datapacks, "mymap", Map.of("pack.mcmeta", new byte[0]));
        assertTrue(Files.exists(datapacks.resolve("mymap/pack.mcmeta")));
        assertThrows(IOException.class, () -> MapPack.installDatapack(datapacks, "mymap", Map.of("../escaped.txt", new byte[0])));
        assertFalse(Files.exists(datapacks.resolve("escaped.txt")));
        assertThrows(IOException.class, () -> MapPack.installDatapack(datapacks, "../outside", Map.of("pack.mcmeta", new byte[0])));
    }
}
