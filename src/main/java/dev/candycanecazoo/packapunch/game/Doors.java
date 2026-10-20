package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.config.ServerConfig;
import dev.candycanecazoo.packapunch.map.DoorDef;
import dev.candycanecazoo.packapunch.map.Passages;
import dev.candycanecazoo.packapunch.map.ZoneGraph;
import dev.candycanecazoo.packapunch.map.ZoneIndex;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.tools.LevelCellProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Optional;
import java.util.UUID;

/** Buying, opening, and restoring doors. */
public final class Doors {
    /** Removing door blocks must not knock off torches or pop neighbours, and must not drop anything. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private Doors() {}

    /** A player in a game used a block: if it belongs to a door, they try to buy the door. Returns whether it did. */
    public static boolean use(ServerPlayer player, BlockPos pos) {
        Optional<GameManager.Active> game = GameManager.forPlayer(player.getUUID()).filter(a -> a.level() == player.serverLevel());
        Optional<DoorDef> door = game.flatMap(g -> g.game().map().map().doorAt(pos));
        if (door.isEmpty()) {
            return false;
        }
        buy(player, game.get(), door.get());
        return true;
    }

    public static void buy(ServerPlayer player, GameManager.Active game, DoorDef door) {
        Shop.buy(player, game, new Shop.Sale() {
            @Override
            public int cost() {
                return door.cost();
            }

            @Override
            public boolean needsPower() {
                return door.requiresPower();
            }

            @Override
            public Optional<Refusal> check(GameManager.Active g, ServerPlayer p) {
                return g.game().doorOpen(door.id()) ? Optional.of(new Refusal.Because("That door is already open")) : Optional.empty();
            }

            @Override
            public Optional<Refusal> deliver(GameManager.Active g, ServerPlayer p) {
                open(g, door, Optional.of(p.getUUID()));
                return Optional.empty();
            }
        });
    }

    /**
     * Removes the door's blocks, then finds passages in the world again: the opening is now a doorway, so the zones
     * beyond unlock, their spawners join in, and zombie pathing rebuilds over them.
     */
    public static void open(GameManager.Active active, DoorDef door, Optional<UUID> by) {
        ServerLevel level = active.level();
        for (BlockPos pos : door.members()) {
            BlockState state = level.getBlockState(pos);
            if (door.style() == DoorDef.Style.DEBRIS) {
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
        }
        BlockPos center = door.members().iterator().next();
        level.playSound(null, center, door.style() == DoorDef.Style.DOOR ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.GRAVEL_BREAK,
                SoundSource.BLOCKS, 1.0f, door.style() == DoorDef.Style.DOOR ? 0.8f : 1.0f);
        ZombiesMap map = active.game().map().map();
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), LevelCellProbe.of(level), ServerConfig.MAX_PASSAGE_GAP.get());
        active.game().openDoor(door.id(), by, ZoneGraph.of(map, passages.open()));
        active.pathing().reconnect(passages, active.game().unlocked());
    }

    /** Saves every door's blocks before a game, so the reset can put them back even after a crash mid-game. */
    public static void snapshot(ServerLevel level, ZombiesMap map) {
        Snapshots snapshots = Snapshots.get(level);
        snapshots.blocks = new ListTag();
        for (DoorDef door : map.doors().values()) {
            for (BlockPos pos : door.members()) {
                CompoundTag entry = new CompoundTag();
                entry.put("pos", NbtUtils.writeBlockPos(pos));
                entry.put("state", NbtUtils.writeBlockState(level.getBlockState(pos)));
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    entry.put("block_entity", be.saveWithFullMetadata());
                }
                snapshots.blocks.add(entry);
            }
        }
        snapshots.setDirty();
    }

    /** Puts back every block saved by {@link #snapshot}, then forgets them. Doing it twice changes nothing. */
    public static void restore(ServerLevel level) {
        Snapshots snapshots = Snapshots.get(level);
        if (snapshots.blocks.isEmpty()) {
            return;
        }
        var blocks = level.holderLookup(Registries.BLOCK);
        for (Tag tag : snapshots.blocks) {
            CompoundTag entry = (CompoundTag) tag;
            BlockPos pos = NbtUtils.readBlockPos(entry.getCompound("pos"));
            level.setBlock(pos, NbtUtils.readBlockState(blocks, entry.getCompound("state")), QUIET);
            if (entry.contains("block_entity")) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    be.load(entry.getCompound("block_entity"));
                    be.setChanged();
                } else {
                    PackAPunch.LOG.warn("Door block at {} had a block entity that couldn't be restored", pos);
                }
            }
        }
        snapshots.blocks = new ListTag();
        snapshots.setDirty();
    }

    private static final class Snapshots extends SavedData {
        private static final String NAME = PackAPunch.MOD_ID + "_door_snapshots";

        private ListTag blocks = new ListTag();

        static Snapshots get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(Snapshots::load, Snapshots::new, NAME);
        }

        private static Snapshots load(CompoundTag tag) {
            Snapshots snapshots = new Snapshots();
            snapshots.blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
            return snapshots;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.put("blocks", blocks);
            return tag;
        }
    }
}
