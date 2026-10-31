package dev.candycanecazoo.hellstorm.gen.world;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.gen.layout.Box;
import dev.candycanecazoo.hellstorm.gen.layout.Layout;
import dev.candycanecazoo.hellstorm.gen.pack.RoomPack;
import dev.candycanecazoo.hellstorm.gen.place.MapGenerator;
import dev.candycanecazoo.hellstorm.gen.place.RoomSaver;
import dev.candycanecazoo.hellstorm.registry.GenRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.util.List;

/**
 * Builds what a Hellstorm world type's overworld is for, once, the first time the server starts: a map generated from
 * its packs with the world's seed (so the same seed and packs make the same map), or the map workshop. It's remembered
 * with the world, so a restart, a {@code /reload} or a changed pack never changes a world already made. The workshop
 * also hands each player the map maker's kit the first time they join it.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class GeneratedWorlds {
    public static final BlockPos ORIGIN = new BlockPos(0, 64, 0);
    private static final String DATA = Hellstorm.MOD_ID + "_generated_world";
    private static final String KIT = Hellstorm.MOD_ID + "_workshop_kit";
    /** How a failed build's outcome starts, so joining players can be told. */
    private static final String FAILED = "failed: ";

    private GeneratedWorlds() {}

    /** Whether this world's Hellstorm build has happened. */
    static final class Built extends SavedData {
        boolean done;
        String outcome = "";

        static Built load(CompoundTag tag) {
            Built built = new Built();
            built.done = tag.getBoolean("done");
            built.outcome = tag.getString("outcome");
            return built;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putBoolean("done", done);
            tag.putString("outcome", outcome);
            return tag;
        }

        static Built get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(Built::load, Built::new, DATA);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        if (!(level.getChunkSource().getGenerator() instanceof HellstormLevelSource source)) {
            return;
        }
        Built built = Built.get(level);
        if (built.done) {
            return;
        }
        built.done = true;
        built.outcome = build(level, source);
        built.setDirty();
        Hellstorm.LOG.info("Hellstorm world: {}", built.outcome);
    }

    /** Builds the world's map or workshop at {@link #ORIGIN}, and puts the spawn there. Returns what happened, for the log. */
    static String build(ServerLevel level, HellstormLevelSource source) {
        level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, level.getServer());
        if (source.mode() == HellstormLevelSource.Mode.WORKSHOP) {
            Workshop.build(level, ORIGIN, RoomSaver.packs());
            level.setDefaultSpawnPos(ORIGIN.offset(2, 0, -6), 0);
            return "built the map workshop";
        }
        List<RoomPack> packs;
        try {
            packs = MapGenerator.load(source.packs());
        } catch (IOException | RoomPack.BadPack e) {
            return standHere(level, FAILED + "couldn't read the packs " + source.packs() + ": " + e.getMessage());
        }
        MapGenerator.Result result = MapGenerator.generate(level, packs, level.getSeed(), ORIGIN);
        if (result instanceof MapGenerator.Result.Failed failed) {
            return standHere(level, FAILED + "couldn't generate a map: " + failed.reason());
        }
        MapGenerator.Result.Generated made = (MapGenerator.Result.Generated) result;
        Layout.PlacedRoom start = made.layout().rooms().get(0);
        Box box = start.box();
        level.setDefaultSpawnPos(ORIGIN.offset((box.minX() + box.maxX()) / 2, box.minY() + 1, (box.minZ() + box.maxZ()) / 2), 0);
        return "generated " + made.layout().rooms().size() + " rooms from " + packs.stream().map(RoomPack::id).toList()
                + (made.report().playable() ? "" : ", not yet playable: " + made.report().problems());
    }

    /** No map was made: a stone platform at the spawn, so nobody falls into the void, and {@code why} for the log and players. */
    private static String standHere(ServerLevel level, String why) {
        for (BlockPos pos : BlockPos.betweenClosed(ORIGIN.offset(-3, -1, -3), ORIGIN.offset(3, -1, 3))) {
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState());
        }
        level.setDefaultSpawnPos(ORIGIN, 0);
        return why;
    }

    /** In a generated-map world whose map couldn't be made, tells each player who joins why. */
    @SubscribeEvent
    public static void onJoinWithoutAMap(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.serverLevel().getServer().overworld().getChunkSource().getGenerator() instanceof HellstormLevelSource source
                && source.mode() == HellstormLevelSource.Mode.MAP) {
            String outcome = Built.get(player.serverLevel().getServer().overworld()).outcome;
            if (outcome.startsWith(FAILED)) {
                dev.candycanecazoo.hellstorm.game.Notices.show(player, Component.literal("No map was generated: " + outcome.substring(FAILED.length())
                        + ". Save rooms in a Hellstorm Map Workshop world, then make a new world, or use /hs map generate here.")
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
        }
    }

    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.serverLevel().getServer().overworld().getChunkSource().getGenerator() instanceof HellstormLevelSource source)
                || source.mode() != HellstormLevelSource.Mode.WORKSHOP || player.getPersistentData().getBoolean(KIT)) {
            return;
        }
        player.getPersistentData().putBoolean(KIT, true);
        for (var item : GenRegistries.kit()) {
            player.getInventory().add(new ItemStack(item, item == GenRegistries.ROOM_BLOCK_ITEM.get() ? 1 : 64));
        }
        player.getInventory().add(new ItemStack(dev.candycanecazoo.hellstorm.registry.PapRegistries.ZONE_WAND.get()));
        dev.candycanecazoo.hellstorm.game.Notices.show(player, Component.literal("Welcome to the map workshop. Each brick room has a Room Block in front of it: use it "
                + "to save the room into the workshop pack. Then make a Hellstorm Generated Map world from the pack."));
    }
}
