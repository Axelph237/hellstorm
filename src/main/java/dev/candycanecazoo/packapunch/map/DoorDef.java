package dev.candycanecazoo.packapunch.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.packapunch.util.Codecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A door players buy to open: any group of blocks, picked with the Door Wand, standing between two zones. Opening it
 * removes the blocks for the rest of the game; the zones beyond unlock. The zones aren't stored: they are whichever zones
 * the blocks border, so editing zones never leaves a door pointing at the wrong ones.
 */
public record DoorDef(String id, Set<BlockPos> members, int cost, boolean requiresPower, Style style) {
    /** How the door opens: a door swings away, debris crumbles. Only the effect and sound differ. */
    public enum Style implements StringRepresentable {
        DOOR, DEBRIS;

        public static final Codec<Style> CODEC = StringRepresentable.fromEnum(Style::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    /** How far past a door's blocks to look for the zones on each side: a zone's box may stop short of the wall. */
    public static final int ZONE_REACH = 2;

    public static final Codec<DoorDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(DoorDef::id),
            Codecs.strictOptional(BlockPos.CODEC.listOf(), "members", List.of()).forGetter(d -> List.copyOf(d.members)),
            Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "cost", 750).forGetter(DoorDef::cost),
            Codecs.strictOptional(Codec.BOOL, "requires_power", false).forGetter(DoorDef::requiresPower),
            Codecs.strictOptional(Style.CODEC, "style", Style.DOOR).forGetter(DoorDef::style)
    ).apply(i, (id, members, cost, power, style) -> new DoorDef(id, Set.copyOf(members), cost, power, style)));

    public DoorDef {
        TreeSet<BlockPos> sorted = new TreeSet<>(Comparator.comparingLong(BlockPos::asLong));
        sorted.addAll(members);
        members = java.util.Collections.unmodifiableSortedSet(sorted);
    }

    public static DoorDef empty(String id, int cost) {
        return new DoorDef(id, Set.of(), cost, false, Style.DOOR);
    }

    public DoorDef withMember(BlockPos pos) {
        TreeSet<BlockPos> next = new TreeSet<>(Comparator.comparingLong(BlockPos::asLong));
        next.addAll(members);
        next.add(pos.immutable());
        return new DoorDef(id, next, cost, requiresPower, style);
    }

    public DoorDef withoutMember(BlockPos pos) {
        TreeSet<BlockPos> next = new TreeSet<>(Comparator.comparingLong(BlockPos::asLong));
        next.addAll(members);
        next.remove(pos);
        return new DoorDef(id, next, cost, requiresPower, style);
    }

    public DoorDef withCost(int cost) {
        return new DoorDef(id, members, cost, requiresPower, style);
    }

    public DoorDef withPower(boolean requiresPower) {
        return new DoorDef(id, members, cost, requiresPower, style);
    }

    public DoorDef withStyle(Style style) {
        return new DoorDef(id, members, cost, requiresPower, style);
    }

    /** The zones this door borders: found by looking out from each block, up to {@link #ZONE_REACH} cells. */
    public Set<ZoneId> zones(ZoneIndex index) {
        Set<ZoneId> zones = new TreeSet<>();
        for (BlockPos member : members) {
            for (Direction dir : Direction.values()) {
                for (int step = 1; step <= ZONE_REACH; step++) {
                    BlockPos at = member.relative(dir, step);
                    if (members.contains(at)) {
                        break;
                    }
                    var zone = index.zoneAt(at);
                    if (zone.isPresent()) {
                        zones.add(zone.get());
                        break;
                    }
                }
            }
        }
        return zones;
    }

    /** Every pair of zones this door joins once open; usually exactly one. */
    public Set<Passages.Link> links(ZoneIndex index) {
        List<ZoneId> zones = List.copyOf(zones(index));
        Set<Passages.Link> links = new java.util.HashSet<>();
        for (int i = 0; i < zones.size(); i++) {
            for (int j = i + 1; j < zones.size(); j++) {
                links.add(Passages.Link.of(zones.get(i), zones.get(j)));
            }
        }
        return links;
    }
}
