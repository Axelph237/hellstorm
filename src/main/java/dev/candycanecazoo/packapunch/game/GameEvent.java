package dev.candycanecazoo.packapunch.game;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Everything a running game reports. Scripts (phase 3) and tests subscribe through {@link GameEvents}. */
public sealed interface GameEvent {
    record GameStart(List<UUID> players) implements GameEvent {}

    record GameEnd(int roundReached) implements GameEvent {}

    record RoundStart(int round) implements GameEvent {}

    record RoundEnd(int round) implements GameEvent {}

    record ZombieSpawned(UUID mob, BlockPos pos) implements GameEvent {}

    /** {@code unlocked} lists the zones the door made reachable, if any. */
    record DoorOpened(String door, java.util.Optional<UUID> by, List<dev.candycanecazoo.packapunch.map.ZoneId> unlocked) implements GameEvent {}

    /** {@code by} is empty when the power came on without a player (a script, or a map that starts powered). */
    record PowerOn(java.util.Optional<UUID> by) implements GameEvent {}

    /** From a perk machine. {@code item} is the potion id for a potion ({@code packapunch:juggernog}), the item spec otherwise. */
    record PerkBought(UUID player, String item, int cost) implements GameEvent {}

    record ZombieKilled(UUID mob, Optional<UUID> killer, boolean headshot, boolean melee, BlockPos pos) implements GameEvent {}

    /** {@code refill} is true for an ammo refill of a gun the player already had. */
    record WallBuy(UUID player, String item, int cost, boolean refill) implements GameEvent {}

    record MysteryBoxOpened(UUID player, String item, BlockPos pos) implements GameEvent {}

    record MysteryBoxMoved(BlockPos from, BlockPos to) implements GameEvent {}
    /** {@code item} is the gun id for a TacZ gun, the item id otherwise. */
    record PapUsed(UUID player, String item) implements GameEvent {}

    record PlayerDowned(UUID player, BlockPos pos) implements GameEvent {}

    /** {@code by} is the teammate who revived them, or empty for a solo Quick Revive self-revive. */
    record PlayerRevived(UUID player, Optional<UUID> by) implements GameEvent {}

    record PlayerBledOut(UUID player) implements GameEvent {}

    record PowerUpSpawned(net.minecraft.resources.ResourceLocation powerUp, BlockPos pos) implements GameEvent {}

    record PowerUpCollected(net.minecraft.resources.ResourceLocation powerUp, UUID player) implements GameEvent {}

    record TrapActivated(UUID player, BlockPos pos) implements GameEvent {}

    /** Fires as the special round starts, just before its {@code round_start}. */
    record WaveEventStarted(net.minecraft.resources.ResourceLocation waveEvent, int round) implements GameEvent {}
}
