package dev.candycanecazoo.hellstorm.gen.pack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.tools.MapPack;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A pack of rooms to generate maps from (format {@value #FORMAT}), a folder or zip in {@code hellstorm/maps/}: like a
 * world folder that holds only rooms. {@code pack.json} names it; {@code generation.json} holds its {@link GenerationRules};
 * each room is {@code rooms/<id>.json} (its {@link RoomDef}) and {@code rooms/<id>.nbt} (its blocks, a compressed
 * structure template); {@code datapack/...} is a datapack installed into worlds made from it. Packs come from other
 * people, so reading one takes only those entries, never a path out of its folder, and at most as much as a map pack.
 */
public record RoomPack(String id, Info info, GenerationRules rules, Map<String, Room> rooms, Map<String, byte[]> datapack) {
    public static final int FORMAT = 2;
    public static final Pattern ROOM_ID = Pattern.compile("[a-z0-9_-]{1,64}");
    private static final Pattern ROOM_FILE = Pattern.compile("rooms/([a-z0-9_-]{1,64})\\.(json|nbt)");
    private static final Pattern DATAPACK_FILE = Pattern.compile("datapack/(?:[A-Za-z0-9_.-]+/)*[A-Za-z0-9_.-]+");
    private static final int MAX_FILES = 20_000;
    private static final long MAX_BYTES = 512L << 20;
    static final String INFO = "pack.json";
    static final String GENERATION = "generation.json";

    /** {@code pack.json}. */
    public record Info(int format, String name, String description) {
        public static final Codec<Info> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("format").forGetter(Info::format),
                Codec.STRING.fieldOf("name").forGetter(Info::name),
                Codecs.strictOptional(Codec.STRING, "description", "").forGetter(Info::description)
        ).apply(i, Info::new));
    }

    /** A room: what it is, and its blocks. */
    public record Room(RoomDef def, CompoundTag template) {}

    public static final class BadPack extends Exception {
        public BadPack(String message) {
            super(message);
        }
    }

    public RoomPack {
        rooms = new TreeMap<>(rooms);
        datapack = new TreeMap<>(datapack);
    }

    /** The pack's rooms as the layout sees them, each under {@code <pack>:<room>}. */
    public String pieceId(String room) {
        return id + ":" + room;
    }

    /** The format 2 packs in {@code folder}, folders and zips, by id, in order. Unreadable or other packs are left out. */
    public static List<String> available(Path folder) {
        List<String> ids = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return ids;
        }
        try (Stream<Path> entries = Files.list(folder)) {
            entries.sorted().forEach(path -> {
                String name = path.getFileName().toString();
                String id = name.endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
                if (MapPack.NAME.matcher(id).matches() && (Files.isRegularFile(path.resolve(INFO)) || name.endsWith(".zip") && isFormat2Zip(path))) {
                    ids.add(id);
                }
            });
        } catch (IOException ignored) {
            // Nothing listable.
        }
        return ids;
    }

    private static boolean isFormat2Zip(Path zip) {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (entry.getName().equals(INFO)) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /** The pack {@code id} in {@code folder}: its folder if there is one, else {@code <id>.zip}. */
    public static RoomPack read(Path folder, String id) throws IOException, BadPack {
        if (!MapPack.NAME.matcher(id).matches()) {
            throw new BadPack("Not a pack name: " + id);
        }
        Path dir = folder.resolve(id);
        Map<String, byte[]> files = Files.isDirectory(dir) ? readFolder(dir) : readZip(folder.resolve(id + ".zip"));
        return parse(id, files);
    }

    private static Map<String, byte[]> readFolder(Path dir) throws IOException, BadPack {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long[] total = {0};
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path path : walk.filter(Files::isRegularFile).sorted().toList()) {
                String name = dir.relativize(path).toString().replace('\\', '/');
                if (name.equals(".DS_Store") || name.endsWith("/.DS_Store")) {
                    continue;
                }
                if (files.size() >= MAX_FILES) {
                    throw new BadPack("The pack has more than " + MAX_FILES + " files");
                }
                total[0] += Files.size(path);
                if (total[0] > MAX_BYTES) {
                    throw new BadPack("The pack is more than " + (MAX_BYTES >> 20) + " MB");
                }
                files.put(name, Files.readAllBytes(path));
            }
        }
        return files;
    }

    private static Map<String, byte[]> readZip(Path zip) throws IOException, BadPack {
        if (!Files.isRegularFile(zip)) {
            throw new BadPack("No pack " + zip.getFileName() + " in hellstorm/maps");
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (files.size() >= MAX_FILES) {
                    throw new BadPack("The pack has more than " + MAX_FILES + " files");
                }
                byte[] bytes = readCapped(in, MAX_BYTES - total);
                total += bytes.length;
                files.put(entry.getName(), bytes);
            }
        }
        return files;
    }

    private static byte[] readCapped(InputStream in, long room) throws IOException, BadPack {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n = in.read(buffer); n > 0; n = in.read(buffer)) {
            if (out.size() + (long) n > room) {
                throw new BadPack("The pack is more than " + (MAX_BYTES >> 20) + " MB");
            }
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /** A pack from its files by path, checking every one. */
    static RoomPack parse(String id, Map<String, byte[]> files) throws BadPack {
        Info info = null;
        GenerationRules rules = GenerationRules.DEFAULT;
        Map<String, RoomDef> defs = new TreeMap<>();
        Map<String, CompoundTag> templates = new TreeMap<>();
        Map<String, byte[]> datapack = new TreeMap<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            String name = file.getKey();
            Matcher room = ROOM_FILE.matcher(name);
            try {
                if (name.equals(INFO)) {
                    info = Info.CODEC.parse(JsonOps.INSTANCE, json(file.getValue())).getOrThrow(false, e -> {});
                } else if (name.equals(GENERATION)) {
                    rules = GenerationRules.CODEC.parse(JsonOps.INSTANCE, json(file.getValue())).getOrThrow(false, e -> {});
                } else if (room.matches() && room.group(2).equals("json")) {
                    RoomDef def = RoomDef.CODEC.parse(JsonOps.INSTANCE, json(file.getValue())).getOrThrow(false, e -> {});
                    if (!def.id().equals(room.group(1))) {
                        throw new BadPack("Room file " + name + " names itself " + def.id());
                    }
                    defs.put(def.id(), def);
                } else if (room.matches()) {
                    templates.put(room.group(1), NbtIo.readCompressed(new ByteArrayInputStream(file.getValue())));
                } else if (DATAPACK_FILE.matcher(name).matches() && !name.contains("..")) {
                    datapack.put(name.substring("datapack/".length()), file.getValue());
                } else {
                    throw new BadPack("Not part of a room pack: " + name);
                }
            } catch (BadPack e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                throw new BadPack("In " + name + ": " + e.getMessage());
            }
        }
        if (info == null) {
            throw new BadPack("The pack has no " + INFO);
        }
        if (info.format() != FORMAT) {
            throw new BadPack("The pack is format " + info.format() + "; generated maps read format " + FORMAT);
        }
        Map<String, Room> rooms = new TreeMap<>();
        for (Map.Entry<String, RoomDef> def : defs.entrySet()) {
            CompoundTag template = templates.get(def.getKey());
            if (template == null) {
                throw new BadPack("Room " + def.getKey() + " has no blocks (rooms/" + def.getKey() + ".nbt)");
            }
            rooms.put(def.getKey(), new Room(def.getValue(), template));
        }
        for (String t : templates.keySet()) {
            if (!defs.containsKey(t)) {
                throw new BadPack("Room blocks rooms/" + t + ".nbt have no rooms/" + t + ".json");
            }
        }
        return new RoomPack(id, info, rules, rooms, datapack);
    }

    private static JsonElement json(byte[] bytes) {
        return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
    }

    /**
     * Saves {@code def} and its blocks into the pack folder {@code folder/<pack>}, made with a {@code pack.json} and a
     * default {@code generation.json} if it's new. A room already saved under that id is replaced.
     */
    public static void writeRoom(Path folder, String pack, RoomDef def, CompoundTag template) throws IOException {
        if (!MapPack.NAME.matcher(pack).matches() || !ROOM_ID.matcher(def.id()).matches()) {
            throw new IOException("Bad pack or room name: " + pack + " / " + def.id());
        }
        Path dir = folder.resolve(pack);
        Files.createDirectories(dir.resolve("rooms"));
        if (!Files.exists(dir.resolve(INFO))) {
            writeJson(dir.resolve(INFO), Info.CODEC.encodeStart(JsonOps.INSTANCE, new Info(FORMAT, pack, "")).getOrThrow(false, e -> {}));
        }
        if (!Files.exists(dir.resolve(GENERATION))) {
            writeJson(dir.resolve(GENERATION), GenerationRules.CODEC.encodeStart(JsonOps.INSTANCE, GenerationRules.DEFAULT).getOrThrow(false, e -> {}));
        }
        writeJson(dir.resolve("rooms/" + def.id() + ".json"), RoomDef.CODEC.encodeStart(JsonOps.INSTANCE, def).getOrThrow(false, e -> {}));
        ByteArrayOutputStream nbt = new ByteArrayOutputStream();
        NbtIo.writeCompressed(template, nbt);
        Files.write(dir.resolve("rooms/" + def.id() + ".nbt"), nbt.toByteArray());
    }

    /** Writes {@code rules} as the pack's {@code generation.json}. */
    public static void writeRules(Path folder, String pack, GenerationRules rules) throws IOException {
        writeJson(folder.resolve(pack).resolve(GENERATION), GenerationRules.CODEC.encodeStart(JsonOps.INSTANCE, rules).getOrThrow(false, e -> {}));
    }

    private static void writeJson(Path file, JsonElement json) throws IOException {
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n");
    }

    /** Reads just a pack's blocks for one room, for code that already has the pack. */
    static CompoundTag readTemplate(byte[] bytes) throws IOException {
        return NbtIo.readCompressed(new ByteArrayInputStream(bytes));
    }

    static long maxBytes() {
        return MAX_BYTES;
    }

    @SuppressWarnings("unused")
    private static NbtAccounter accounter() {
        return new NbtAccounter(MAX_BYTES);
    }
}
