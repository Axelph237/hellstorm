package dev.candycanecazoo.hellstorm.gen.pack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.gen.layout.Box;
import dev.candycanecazoo.hellstorm.gen.layout.Connector;
import dev.candycanecazoo.hellstorm.gen.layout.Facing;
import dev.candycanecazoo.hellstorm.gen.layout.Piece;
import dev.candycanecazoo.hellstorm.gen.layout.Size;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.util.Codecs;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * A room in a pack ({@code rooms/<id>.json}, beside its blocks in {@code rooms/<id>.nbt}), all relative to its corner:
 * its size, tags, how often it's picked and at most how many a map has, its connectors, its placeholders, and its slice
 * of the map (zones, windows, spawn markers, doors inside it).
 */
public record RoomDef(String id, Size size, Set<String> tags, int weight, Optional<Integer> max, List<Connector> connectors,
                      List<Placeholder> placeholders, ZombiesMap slice) {
    /** A placeholder block: what it stands for, where, and which way the machine it becomes faces. */
    public record Placeholder(PlaceholderKind kind, int x, int y, int z, Facing facing) {}

    static final Codec<Facing> FACING = Codec.STRING.comapFlatMap(
            name -> Arrays.stream(Facing.values()).filter(f -> f.name().toLowerCase(Locale.ROOT).equals(name)).findFirst()
                    .map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Not a side: " + name)),
            f -> f.name().toLowerCase(Locale.ROOT));

    private static final Codec<Connector.Kind> KIND = Codec.STRING.comapFlatMap(
            name -> Arrays.stream(Connector.Kind.values()).filter(k -> k.name().toLowerCase(Locale.ROOT).equals(name)).findFirst()
                    .map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Not a connector kind: " + name)),
            k -> k.name().toLowerCase(Locale.ROOT));

    private static DataResult<List<Integer>> three(List<Integer> list) {
        return list.size() == 3 ? DataResult.success(list) : DataResult.error(() -> "Expected 3 numbers, not " + list);
    }

    private static final Codec<List<Integer>> TRIPLE = Codec.INT.listOf().comapFlatMap(RoomDef::three, p -> p);

    static final Codec<Box> BOX = RecordCodecBuilder.create(i -> i.group(
            TRIPLE.fieldOf("from").forGetter(b -> List.of(b.minX(), b.minY(), b.minZ())),
            TRIPLE.fieldOf("to").forGetter(b -> List.of(b.maxX(), b.maxY(), b.maxZ()))
    ).apply(i, (a, b) -> Box.between(a.get(0), a.get(1), a.get(2), b.get(0), b.get(1), b.get(2))));

    static final Codec<Connector> CONNECTOR = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(Connector::id),
            BOX.fieldOf("opening").forGetter(Connector::opening),
            FACING.fieldOf("facing").forGetter(Connector::facing),
            Codecs.strictOptional(KIND, "kind", Connector.Kind.DOOR).forGetter(Connector::kind),
            Codecs.strictOptional(Codec.STRING, "group", "").forGetter(Connector::group)
    ).apply(i, Connector::new));

    // Declared after the codecs it uses: a nested record's own codec would read them before they're set.
    private static final Codec<Placeholder> PLACEHOLDER = RecordCodecBuilder.create(i -> i.group(
            PlaceholderKind.CODEC.fieldOf("kind").forGetter(Placeholder::kind),
            TRIPLE.fieldOf("pos").forGetter(p -> List.of(p.x(), p.y(), p.z())),
            FACING.fieldOf("facing").forGetter(Placeholder::facing)
    ).apply(i, (kind, pos, facing) -> new Placeholder(kind, pos.get(0), pos.get(1), pos.get(2), facing)));

    private static final Codec<Size> SIZE = TRIPLE.xmap(l -> new Size(l.get(0), l.get(1), l.get(2)), s -> List.of(s.x(), s.y(), s.z()));

    public static final Codec<RoomDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(RoomDef::id),
            SIZE.fieldOf("size").forGetter(RoomDef::size),
            Codecs.strictOptional(Codec.STRING.listOf(), "tags", List.of()).forGetter(r -> r.tags().stream().sorted().toList()),
            Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "weight", 1).forGetter(RoomDef::weight),
            Codecs.strictOptional(Codec.intRange(1, Integer.MAX_VALUE), "max").forGetter(RoomDef::max),
            Codecs.strictOptional(CONNECTOR.listOf(), "connectors", List.of()).forGetter(RoomDef::connectors),
            Codecs.strictOptional(PLACEHOLDER.listOf(), "placeholders", List.of()).forGetter(RoomDef::placeholders),
            Codecs.strictOptional(ZombiesMap.CODEC, "map", ZombiesMap.EMPTY).forGetter(RoomDef::slice)
    ).apply(i, (id, size, tags, weight, max, connectors, placeholders, slice) ->
            new RoomDef(id, size, Set.copyOf(tags), weight, max, connectors, placeholders, slice)));

    public RoomDef {
        tags = Set.copyOf(tags);
        connectors = List.copyOf(connectors);
        placeholders = List.copyOf(placeholders);
    }

    /** The room as the layout sees it, under {@code id} (the pack's namespace and the room's id). */
    public Piece piece(String id) {
        return new Piece(id, size, tags, weight, max.map(OptionalInt::of).orElse(OptionalInt.empty()), connectors);
    }
}
