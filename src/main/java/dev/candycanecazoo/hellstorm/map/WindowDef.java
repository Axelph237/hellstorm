package dev.candycanecazoo.hellstorm.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A boarded window: a flat opening, one block thick, in a zone's wall. Zombies outside tear its boards off and climb in.
 * {@code outward} points from the zone to the outside. The current board count isn't stored here: it is the
 * {@code boards} blockstate shared by every barricade block in the opening.
 */
public record WindowDef(String id, BlockBox opening, Direction outward, int maxBoards, ZoneId zone) {
    public static final int MAX_BOARDS = 6;

    public static final Codec<WindowDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("id").forGetter(WindowDef::id),
            BlockBox.CODEC.fieldOf("opening").forGetter(WindowDef::opening),
            Direction.CODEC.fieldOf("outward").forGetter(WindowDef::outward),
            Codecs.strictOptional(Codec.intRange(1, MAX_BOARDS), "max_boards", MAX_BOARDS).forGetter(WindowDef::maxBoards),
            ZoneId.CODEC.fieldOf("zone").forGetter(WindowDef::zone)
    ).apply(i, WindowDef::new));

    public WindowDef {
        if (outward.getAxis().isVertical()) {
            throw new IllegalArgumentException("Windows face sideways, not " + outward);
        }
    }

    /** Opening width along the wall (not counting thickness). */
    public int width() {
        return outward.getAxis() == Direction.Axis.X ? opening.sizeZ() : opening.sizeX();
    }

    public int height() {
        return opening.sizeY();
    }

    /** The bottom cell of every column of the opening, left to right along the wall. */
    public java.util.List<BlockPos> columns() {
        java.util.List<BlockPos> columns = new java.util.ArrayList<>();
        for (int x = opening.minX(); x <= opening.maxX(); x++) {
            for (int z = opening.minZ(); z <= opening.maxZ(); z++) {
                columns.add(new BlockPos(x, opening.minY(), z));
            }
        }
        return columns;
    }

    /** Where a zombie stands to attack the window: just outside the bottom-middle of the opening. */
    public BlockPos attackSpot() {
        return bottomCenter().relative(outward);
    }

    /** Where a zombie ends up after climbing through: just inside the bottom-middle of the opening. */
    public BlockPos insideSpot() {
        return bottomCenter().relative(outward.getOpposite());
    }

    public BlockPos bottomCenter() {
        BlockPos c = opening.center();
        return new BlockPos(c.getX(), opening.minY(), c.getZ());
    }
}
