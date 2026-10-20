package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.perk.Perks;
import dev.candycanecazoo.packapunch.tacz.TaczBridge;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * What being down looks like on a player: crawling, slowed, perks gone, and holding a pistol. A player with no pistol is
 * lent the starting pistol, which goes away when they get up.
 */
public final class Downs {
    private static final String PISTOL = "pistol";
    private static final String LENT = PackAPunch.MOD_ID + "_downed_pistol";
    private static final UUID SLOW = UUID.fromString("5f1a4b9e-3c2d-4e8f-9a7b-6c5d4e3f2a1b");
    private static final double SLOW_AMOUNT = -0.6;
    /** Food is held here in a game: below vanilla's 18 for natural regen, above 6 so players can still sprint. */
    public static final int FOOD_LEVEL = 17;

    private Downs() {}

    public static void knockDown(ServerPlayer player, GameSettings.Players settings) {
        Perks.clearBuffs(player);
        player.setForcedPose(Pose.SWIMMING);
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
    }

    /** Undoes {@link #knockDown}. Safe to call on anyone. */
    public static void standUp(ServerPlayer player) {
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

    /** Every tick while down: still crawling, still holding the pistol. */
    public static void enforce(ServerPlayer player) {
        if (player.getForcedPose() != Pose.SWIMMING) {
            player.setForcedPose(Pose.SWIMMING);
        }
        if (!isPistol(player.getMainHandItem())) {
            holdPistol(player);
        }
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
