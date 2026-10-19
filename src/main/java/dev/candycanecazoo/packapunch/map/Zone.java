package dev.candycanecazoo.packapunch.map;

import dev.candycanecazoo.packapunch.util.Codecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** A room of the map: one or more boxes. Players in a start zone's boxes are where a game begins. */
public record Zone(ZoneId id, List<BlockBox> boxes, boolean start) {
    public static final Codec<Zone> CODEC = RecordCodecBuilder.create(i -> i.group(
            ZoneId.CODEC.fieldOf("id").forGetter(Zone::id),
            BlockBox.CODEC.listOf().fieldOf("boxes").forGetter(Zone::boxes),
            Codecs.strictOptional(Codec.BOOL, "start", false).forGetter(Zone::start)
    ).apply(i, Zone::new));

    public Zone {
        boxes = List.copyOf(boxes);
    }

    public static Zone empty(ZoneId id) {
        return new Zone(id, List.of(), false);
    }

    public boolean contains(BlockPos pos) {
        return boxes.stream().anyMatch(b -> b.contains(pos));
    }

    public long volume() {
        return boxes.stream().mapToLong(BlockBox::volume).sum();
    }

    public Zone withBox(BlockBox box) {
        List<BlockBox> next = new ArrayList<>(boxes);
        next.add(box);
        return new Zone(id, next, start);
    }

    public Zone withBoxes(List<BlockBox> added) {
        List<BlockBox> next = new ArrayList<>(boxes);
        next.addAll(added);
        return new Zone(id, next, start);
    }

    public Zone withoutBox(int index) {
        List<BlockBox> next = new ArrayList<>(boxes);
        next.remove(index);
        return new Zone(id, next, start);
    }

    public Zone withStart(boolean start) {
        return new Zone(id, boxes, start);
    }

    /** True when any box of this zone shares a face with any box of the other: an open passage. */
    public boolean touches(Zone other) {
        for (BlockBox a : boxes) {
            for (BlockBox b : other.boxes) {
                if (a.touchesFace(b)) {
                    return true;
                }
            }
        }
        return false;
    }
}
