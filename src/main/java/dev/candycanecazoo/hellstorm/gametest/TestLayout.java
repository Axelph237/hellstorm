package dev.candycanecazoo.hellstorm.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;

import java.util.Collection;

/** The "floor" template places its stone floor at relative y = 1 (the structure sits one block above the test origin). */
final class TestLayout {
    static final int FLOOR_Y = 1;
    static final int ROOM_Y = FLOOR_Y + 1;

    private TestLayout() {}

    /**
     * Runs {@code then} once every one of {@code absolute}'s chunks ticks entities, force-loading them first. Arenas that
     * reach past the test's own floor need it: the game spawns nothing in a chunk that isn't ticking entities.
     */
    static void onceTicking(GameTestHelper helper, Collection<BlockPos> absolute, Runnable then) {
        absolute.stream().map(ChunkPos::new).distinct().forEach(chunk -> helper.getLevel().setChunkForced(chunk.x, chunk.z, true));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(absolute.stream().allMatch(helper.getLevel()::isPositionEntityTicking),
                        "the arena's chunks aren't ticking entities yet"))
                .thenExecute(then);
    }
}
