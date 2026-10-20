package dev.candycanecazoo.packapunch.feature;

import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.data.BoxPool;
import dev.candycanecazoo.packapunch.data.BoxPools;
import dev.candycanecazoo.packapunch.data.ItemSpec;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.net.OpenSettingsPacket;
import dev.candycanecazoo.packapunch.net.PapNetwork;
import dev.candycanecazoo.packapunch.perk.PerkDefs;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The server side of a machine's settings screen. Ops in creative mode get the screen when they use a machine outside a
 * game; saving goes through the machine's own checks, the same ones the {@code /pap} commands use.
 */
public final class MachineSettings {
    private static final double REACH_SLACK = 4.0;

    private MachineSettings() {}

    public static boolean mayEdit(ServerPlayer player) {
        return player.isCreative() && player.hasPermissions(2);
    }

    /** Sends the screen for the machine at {@code pos}. Returns false when this player doesn't get one. */
    public static boolean open(ServerPlayer player, BlockPos pos) {
        if (!mayEdit(player) || !(player.serverLevel().getBlockEntity(pos) instanceof ConfigBlockEntity<?> entity)) {
            return false;
        }
        PapNetwork.send(player, new OpenSettingsPacket(pos, choices(entity), held(player, entity), defaultCost(entity)));
        return true;
    }

    /** Applies settings sent from the screen. Returns why they were refused, if they were. */
    public static Optional<String> save(ServerPlayer player, BlockPos pos, CompoundTag settings) {
        if (!mayEdit(player)) {
            return Optional.of("Editing machines needs operator permission and creative mode");
        }
        if (!player.canReach(pos, REACH_SLACK)) {
            return Optional.of("You're too far from the machine");
        }
        if (!(player.serverLevel().getBlockEntity(pos) instanceof ConfigBlockEntity<?> entity)) {
            return Optional.of("The machine isn't there any more");
        }
        return entity.trySet(settings);
    }

    static List<OpenSettingsPacket.Choice> choices(ConfigBlockEntity<?> entity) {
        if (entity instanceof PerkMachineBlock.Entity) {
            return PerkDefs.all().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .map(e -> new OpenSettingsPacket.Choice(e.getKey(), Component.translatable(e.getValue().name()), e.getValue().cost()))
                    .toList();
        }
        if (entity instanceof MysteryBoxBlock.Entity) {
            return Stream.concat(Stream.of(BoxPool.DEFAULT_ID), BoxPools.all().keySet().stream()).distinct().sorted(Comparator.naturalOrder())
                    .map(id -> new OpenSettingsPacket.Choice(id, Component.literal(id.toString()), 0))
                    .toList();
        }
        return List.of();
    }

    private static Optional<OpenSettingsPacket.Held> held(ServerPlayer player, ConfigBlockEntity<?> entity) {
        ItemStack stack = player.getMainHandItem();
        return entity instanceof WallBuyBlock.Entity && !stack.isEmpty()
                ? Optional.of(new OpenSettingsPacket.Held(ItemSpec.of(stack), stack.getHoverName()))
                : Optional.empty();
    }

    private static int defaultCost(ConfigBlockEntity<?> entity) {
        GameSettings settings = GameManager.settings();
        if (entity instanceof PapMachineBlock.Entity) {
            return settings.pap().defaultCost();
        }
        return entity instanceof MysteryBoxBlock.Entity ? settings.box().cost() : 0;
    }
}
