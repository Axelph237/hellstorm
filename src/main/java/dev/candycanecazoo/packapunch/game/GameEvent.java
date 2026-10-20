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

    /** {@code by} is empty when the power came on without a player (a script, or a map that starts powered). */
    record PowerOn(java.util.Optional<UUID> by) implements GameEvent {}

    record PerkBought(UUID player, net.minecraft.resources.ResourceLocation perk) implements GameEvent {}

    record ZombieKilled(UUID mob, Optional<UUID> killer, boolean headshot, boolean melee, BlockPos pos) implements GameEvent {}
}
