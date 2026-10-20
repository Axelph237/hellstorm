package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.tacz.TaczBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * How many TacZ guns a player can carry: {@code players.weapon_slots} plus perk bonuses (Mule Kick). Getting a gun at the
 * limit swaps out the gun in the player's hand. Wall-buys and the Mystery Box both give guns through here.
 */
public final class WeaponSlots {
    public sealed interface Plan {
        record Add() implements Plan {}

        /** Replaces the gun in the selected hotbar slot. */
        record Replace(int slot) implements Plan {}

        record Refuse(String reason) implements Plan {}
    }

    public static final String SWAP = "Hold the gun you want to swap";
    public static final String FULL = "Your inventory is full";

    private WeaponSlots() {}

    public static Plan plan(int guns, int limit, boolean holdingGun, int selectedSlot, boolean hasFreeSlot) {
        if (guns < limit) {
            return hasFreeSlot ? new Plan.Add() : new Plan.Refuse(FULL);
        }
        return holdingGun ? new Plan.Replace(selectedSlot) : new Plan.Refuse(SWAP);
    }

    public static int limit(GameSettings settings, PlayerState player) {
        return settings.players().weaponSlots() + player.bonusWeaponSlots();
    }

    public static int guns(Inventory inventory) {
        int guns = 0;
        for (ItemStack stack : inventory.items) {
            if (isGun(stack)) {
                guns++;
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (isGun(stack)) {
                guns++;
            }
        }
        return guns;
    }

    public static boolean isGun(ItemStack stack) {
        return !stack.isEmpty() && TaczBridge.inspect(stack).isPresent();
    }

    public static Plan plan(ServerPlayer player, PlayerState state, GameSettings settings) {
        Inventory inventory = player.getInventory();
        return plan(guns(inventory), limit(settings, state), isGun(inventory.getSelected()), inventory.selected, inventory.getFreeSlot() >= 0);
    }

    /** Why a gun can't be given right now, checked before charging. */
    public static Optional<Refusal> check(ServerPlayer player, PlayerState state, GameSettings settings) {
        return plan(player, state, settings) instanceof Plan.Refuse refuse ? Optional.of(new Refusal.Because(refuse.reason())) : Optional.empty();
    }

    public static Optional<Refusal> give(ServerPlayer player, PlayerState state, GameSettings settings, ItemStack gun) {
        Plan plan = plan(player, state, settings);
        if (plan instanceof Plan.Refuse refuse) {
            return Optional.of(new Refusal.Because(refuse.reason()));
        }
        Inventory inventory = player.getInventory();
        if (plan instanceof Plan.Replace replace) {
            inventory.setItem(replace.slot(), gun);
        } else if (!inventory.add(gun)) {
            return Optional.of(new Refusal.Because(FULL));
        }
        player.inventoryMenu.broadcastChanges();
        return Optional.empty();
    }
}
