package dev.candycanecazoo.hellstorm.tools;

import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.net.PapNetwork;
import dev.candycanecazoo.hellstorm.net.PreviewPacket;
import dev.candycanecazoo.hellstorm.net.SelectionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player, in-memory map-making state on the server: the wand selection and a pending room detection.
 * Not saved; cleared on logout. Every change is mirrored to the player's client.
 */
public final class ToolState {
    public record PendingDetection(ResourceKey<Level> level, ZoneId zone, List<BlockBox> boxes) {}

    private static final Map<UUID, Selection> SELECTIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingDetection> DETECTIONS = new ConcurrentHashMap<>();

    private ToolState() {}

    public static Selection selection(ServerPlayer player) {
        return SELECTIONS.getOrDefault(player.getUUID(), Selection.NONE);
    }

    public static void setSelection(ServerPlayer player, Selection selection) {
        SELECTIONS.put(player.getUUID(), selection);
        PapNetwork.send(player, new SelectionPacket(selection));
    }

    public static Optional<PendingDetection> detection(ServerPlayer player) {
        return Optional.ofNullable(DETECTIONS.get(player.getUUID()));
    }

    public static void setDetection(ServerPlayer player, PendingDetection detection) {
        DETECTIONS.put(player.getUUID(), detection);
        PapNetwork.send(player, new PreviewPacket(detection.boxes()));
    }

    public static void clearDetection(ServerPlayer player) {
        DETECTIONS.remove(player.getUUID());
        PapNetwork.send(player, new PreviewPacket(List.of()));
    }

    public static void forget(UUID player) {
        SELECTIONS.remove(player);
        DETECTIONS.remove(player);
    }
}
