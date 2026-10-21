package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.perk.Perks;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * What being down looks like on a player: crawling, slowed, glowing red, perks gone, and holding a pistol. A player with no pistol is
 * lent the starting pistol, which goes away when they get up.
 */
public final class Downs {
    private static final String PISTOL = "pistol";
    private static final String LENT = Hellstorm.MOD_ID + "_downed_pistol";
    private static final UUID SLOW = UUID.fromString("5f1a4b9e-3c2d-4e8f-9a7b-6c5d4e3f2a1b");
    private static final double SLOW_AMOUNT = -0.6;
    private static final String DOWNED_TEAM = Hellstorm.MOD_ID + "_downed";
    /** The team each downed player was on before, if any, to put them back when they get up. */
    private static final Map<UUID, Optional<String>> PREVIOUS_TEAMS = new HashMap<>();
    /** Food is held here in a game: below vanilla's 18 for natural regen, above 6 so players can still sprint. */
    public static final int FOOD_LEVEL = 17;

    private Downs() {}

    public static void knockDown(ServerPlayer player, GameSettings.Players settings) {
        Perks.clearBuffs(player);
        crawl(player);
        player.setSprinting(false);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SLOW) == null) {
            speed.addTransientModifier(new AttributeModifier(SLOW, "Downed", SLOW_AMOUNT, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        player.setHealth(1);
        if (pistolSlot(player.getInventory()).isEmpty()) {
            lendPistol(player, settings);
        }
        holdPistol(player);
        glow(player);
    }

    /** Undoes {@link #knockDown}. Safe to call on anyone. */
    public static void standUp(ServerPlayer player) {
        stopGlowing(player);
        player.setForcedPose(null);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW);
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.hasTag() && stack.getTag().getBoolean(LENT)) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * Puts a downed player in the crawl, on either side. Called after each player tick: TacZ's own crawl clears the forced
     * pose of anyone holding a gun who isn't crawling by its key, partway through the tick and before vanilla applies
     * the forced pose, so a forced pose alone never takes.
     */
    public static void crawl(Player player) {
        player.setForcedPose(Pose.SWIMMING);
        if (player.getPose() != Pose.SWIMMING) {
            player.setPose(Pose.SWIMMING);
        }
    }

    /** Every tick while down: still crawling, still holding the pistol. */
    public static void enforce(ServerPlayer player) {
        crawl(player);
        if (!isPistol(player.getMainHandItem())) {
            holdPistol(player);
        }
    }

    /**
     * Outlines the player in red for everyone, through walls, so teammates can find them. Glowing takes its color from the
     * player's team, so they join a red team for the duration and go back to their own team when they get up.
     */
    private static void glow(ServerPlayer player) {
        Scoreboard scoreboard = player.getScoreboard();
        String name = player.getScoreboardName();
        PlayerTeam previous = scoreboard.getPlayersTeam(name);
        if (previous == null || !previous.getName().equals(DOWNED_TEAM)) {
            PREVIOUS_TEAMS.put(player.getUUID(), Optional.ofNullable(previous).map(PlayerTeam::getName));
        }
        PlayerTeam team = scoreboard.getPlayerTeam(DOWNED_TEAM);
        if (team == null) {
            team = scoreboard.addPlayerTeam(DOWNED_TEAM);
            team.setColor(ChatFormatting.RED);
        }
        scoreboard.addPlayerToTeam(name, team);
        player.setGlowingTag(true);
    }

    private static void stopGlowing(ServerPlayer player) {
        player.setGlowingTag(false);
        Optional<Optional<String>> previous = Optional.ofNullable(PREVIOUS_TEAMS.remove(player.getUUID()));
        if (previous.isEmpty()) {
            return;
        }
        Scoreboard scoreboard = player.getScoreboard();
        String name = player.getScoreboardName();
        PlayerTeam current = scoreboard.getPlayersTeam(name);
        if (current != null && current.getName().equals(DOWNED_TEAM)) {
            scoreboard.removePlayerFromTeam(name, current);
        }
        previous.get().map(scoreboard::getPlayerTeam).ifPresent(team -> scoreboard.addPlayerToTeam(name, team));
    }

    static boolean isPistol(ItemStack stack) {
        return TaczBridge.inspect(stack).map(info -> PISTOL.equals(info.type())).orElse(false);
    }

    private static OptionalInt pistolSlot(Inventory inventory) {
        for (int i = 0; i < inventory.items.size(); i++) {
            if (isPistol(inventory.items.get(i))) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    /** Selects a pistol in the hotbar, first swapping one up from the inventory if it's only there. */
    private static void holdPistol(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        OptionalInt slot = pistolSlot(inventory);
        if (slot.isEmpty()) {
            return;
        }
        int hotbar = slot.getAsInt();
        if (!Inventory.isHotbarSlot(hotbar)) {
            ItemStack held = inventory.getItem(inventory.selected);
            inventory.setItem(inventory.selected, inventory.getItem(hotbar));
            inventory.setItem(hotbar, held);
            hotbar = inventory.selected;
        }
        if (inventory.selected != hotbar) {
            inventory.selected = hotbar;
            player.connection.send(new ClientboundSetCarriedItemPacket(hotbar));
        }
        player.inventoryMenu.broadcastChanges();
    }

    private static void lendPistol(ServerPlayer player, GameSettings.Players settings) {
        Optional<ItemStack> pistol = settings.startingGuns().stream()
                .map(id -> TaczBridge.createGun(id, settings.reserveMagazines()))
                .flatMap(Optional::stream)
                .filter(Downs::isPistol)
                .findFirst()
                .or(() -> TaczBridge.createGun(ResourceLocation.tryBuild("tacz", "m1911"), settings.reserveMagazines()));
        pistol.ifPresent(stack -> {
            stack.getOrCreateTag().putBoolean(LENT, true);
            if (!player.getInventory().add(stack)) {
                player.getInventory().setItem(player.getInventory().selected, stack);
            }
        });
    }
}
