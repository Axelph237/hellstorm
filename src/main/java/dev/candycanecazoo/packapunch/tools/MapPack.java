package dev.candycanecazoo.packapunch.tools;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.MapGeometry;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * A map as one file to share: its blocks, its zones and machines, and its datapack. A zip holding
 * <ul>
 *   <li>{@code map.json}: the {@link Manifest}, with the map's positions relative to its corner;</li>
 *   <li>{@code structure/<x>_<y>_<z>.nbt}: the blocks of one {@value #PIECE}-block piece, numbered from the corner, as an
 *       uncompressed structure template (the zip compresses it);</li>
 *   <li>{@code datapack/...}: the world's {@code datapacks/<name>} folder, when the map has one.</li>
 * </ul>
 * Packs come from other people, so reading one accepts only those entries, never a path that leaves its folder, and at
 * most {@value #MAX_ENTRIES} entries and {@value #MAX_MEGABYTES} MB in all.
 */
public final class MapPack {
    public static final int FORMAT = 1;
    public static final int PIECE = 48;
    static final int MAX_ENTRIES = 20_000;
    static final int MAX_MEGABYTES = 512;
    private static final long MAX_BYTES = (long) MAX_MEGABYTES << 20;
    private static final String MANIFEST = "map.json";
    private static final String DATAPACK = "datapack/";
    public static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Pattern PIECE_FILE = Pattern.compile("structure/(\\d{1,4})_(\\d{1,4})_(\\d{1,4})\\.nbt");
    private static final Pattern DATAPACK_FILE = Pattern.compile("datapack/(?:[A-Za-z0-9_.-]+/)*[A-Za-z0-9_.-]+");

    /** {@code origin} is where the map's corner was when exported; {@code map} is relative to it. */
    public record Manifest(int format, String name, BlockPos origin, Vec3i size, ZombiesMap map) {
        public static final Codec<Manifest> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("format").forGetter(Manifest::format),
                Codec.STRING.fieldOf("name").forGetter(Manifest::name),
                BlockPos.CODEC.fieldOf("origin").forGetter(Manifest::origin),
                Vec3i.CODEC.fieldOf("size").forGetter(Manifest::size),
                ZombiesMap.CODEC.fieldOf("map").forGetter(Manifest::map)
        ).apply(i, Manifest::new));
    }

    /** A pack read back: the manifest, each piece's template by its offset from the corner, and the datapack's files by path. */
    public record Contents(Manifest manifest, Map<BlockPos, CompoundTag> pieces, Map<String, byte[]> datapack) {}

    /** Why a pack can't be read. The message is for the player who asked. */
    public static final class BadPack extends Exception {
        public BadPack(String message) {
            super(message);
        }
    }

    private MapPack() {}

    /** Saves {@code area} of {@code level} and its map to {@code file}, replacing it only once the new pack is complete. */
    public static void export(ServerLevel level, String name, BlockBox area, ZombiesMap map, Optional<Path> datapack, Path file) throws IOException {
        Manifest manifest = new Manifest(FORMAT, name, area.min(), new Vec3i(area.sizeX(), area.sizeY(), area.sizeZ()),
                MapGeometry.moved(map, -area.minX(), -area.minY(), -area.minZ()));
        Files.createDirectories(file.getParent());
        Path partial = file.resolveSibling(file.getFileName() + ".partial");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(partial))) {
            JsonElement json = Manifest.CODEC.encodeStart(JsonOps.INSTANCE, manifest).getOrThrow(false, e -> {});
            write(zip, MANIFEST, new GsonBuilder().setPrettyPrinting().create().toJson(json).getBytes(StandardCharsets.UTF_8));
            for (int x = 0; x * PIECE < area.sizeX(); x++) {
                for (int y = 0; y * PIECE < area.sizeY(); y++) {
                    for (int z = 0; z * PIECE < area.sizeZ(); z++) {
                        BlockPos corner = area.min().offset(x * PIECE, y * PIECE, z * PIECE);
                        Vec3i size = new Vec3i(Math.min(PIECE, area.sizeX() - x * PIECE), Math.min(PIECE, area.sizeY() - y * PIECE),
                                Math.min(PIECE, area.sizeZ() - z * PIECE));
                        StructureTemplate piece = new StructureTemplate();
                        piece.fillFromWorld(level, corner, size, false, null);
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        NbtIo.write(piece.save(new CompoundTag()), new DataOutputStream(bytes));
                        write(zip, "structure/" + x + "_" + y + "_" + z + ".nbt", bytes.toByteArray());
                    }
                }
            }
            if (datapack.isPresent() && Files.isDirectory(datapack.get())) {
                Path root = datapack.get();
                try (Stream<Path> files = Files.walk(root)) {
                    for (Path path : files.filter(Files::isRegularFile).sorted().toList()) {
                        write(zip, DATAPACK + root.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
                    }
                }
            }
        }
        Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void write(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    public static Contents read(Path file) throws IOException, BadPack {
        Manifest manifest = null;
        Map<BlockPos, CompoundTag> pieces = new LinkedHashMap<>();
        Map<String, byte[]> datapack = new LinkedHashMap<>();
        long total = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > MAX_ENTRIES) {
                    throw new BadPack("The pack has more than " + MAX_ENTRIES + " files");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                byte[] bytes = readCapped(zip, MAX_BYTES - total);
                total += bytes.length;
                Matcher piece = PIECE_FILE.matcher(name);
                if (name.equals(MANIFEST)) {
                    manifest = Manifest.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)))
                            .getOrThrow(false, e -> {});
                } else if (piece.matches()) {
                    BlockPos offset = new BlockPos(Integer.parseInt(piece.group(1)) * PIECE, Integer.parseInt(piece.group(2)) * PIECE,
                            Integer.parseInt(piece.group(3)) * PIECE);
                    try {
                        pieces.put(offset, NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes)), new NbtAccounter(MAX_BYTES)));
                    } catch (IOException e) {
                        throw new BadPack("The piece " + name + " is damaged");
                    }
                } else if (DATAPACK_FILE.matcher(name).matches() && !name.contains("..")) {
                    datapack.put(name.substring(DATAPACK.length()), bytes);
                } else {
                    throw new BadPack("Not part of a map pack: " + name);
                }
            }
        } catch (RuntimeException e) {
            throw new BadPack("The pack is damaged: " + e.getMessage());
        }
        if (manifest == null) {
            throw new BadPack("The pack has no " + MANIFEST);
        }
        if (manifest.format() != FORMAT) {
            throw new BadPack("The pack is format " + manifest.format() + "; this version reads format " + FORMAT);
        }
        Vec3i size = manifest.size();
        for (BlockPos offset : pieces.keySet()) {
            if (offset.getX() >= size.getX() || offset.getY() >= size.getY() || offset.getZ() >= size.getZ()) {
                throw new BadPack("A piece lies outside the map: " + offset.toShortString());
            }
        }
        return new Contents(manifest, pieces, datapack);
    }

    private static byte[] readCapped(InputStream in, long room) throws IOException, BadPack {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n = in.read(buffer); n > 0; n = in.read(buffer)) {
            if (out.size() + (long) n > room) {
                throw new BadPack("The pack unpacks to more than " + MAX_MEGABYTES + " MB");
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /** Builds the pack's blocks with its corner at {@code at}, then makes its map, moved there, the level's map. */
    public static void place(ServerLevel level, Contents contents, BlockPos at) {
        var blocks = level.holderLookup(Registries.BLOCK);
        contents.pieces().forEach((offset, tag) -> {
            StructureTemplate piece = new StructureTemplate();
            piece.load(blocks, tag);
            BlockPos corner = at.offset(offset);
            piece.placeInWorld(level, corner, corner, new StructurePlaceSettings(), level.random, Block.UPDATE_CLIENTS);
        });
        ZombiesMap map = MapGeometry.moved(contents.manifest().map(), at.getX(), at.getY(), at.getZ());
        MapEdits.apply(level, old -> map);
    }

    /** Writes the pack's datapack as {@code datapacks/<name>}, replacing any folder there, and never outside it. */
    public static void installDatapack(Path datapacks, String name, Map<String, byte[]> files) throws IOException {
        Path root = datapacks.resolve(name).normalize();
        if (!root.startsWith(datapacks.normalize())) {
            throw new IOException("Bad datapack name " + name);
        }
        if (Files.exists(root)) {
            try (Stream<Path> old = Files.walk(root)) {
                for (Path path : old.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            Path path = root.resolve(file.getKey()).normalize();
            if (!path.startsWith(root)) {
                throw new IOException("A datapack file would land outside its folder: " + file.getKey());
            }
            Files.createDirectories(path.getParent());
            Files.write(path, file.getValue());
        }
    }

    /** The pack files in {@code folder}, by name without {@code .zip}. */
    public static List<String> available(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".zip")).map(n -> n.substring(0, n.length() - 4))
                    .filter(n -> NAME.matcher(n).matches()).sorted().toList();
        }
    }
}
