package dev.candycanecazoo.packapunch.client;

import dev.candycanecazoo.packapunch.net.PerkSyncPacket;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** The perks the server last said this player holds. No client-only types, so machine prompts can ask it. */
public final class ClientPerks {
    private static PerkSyncPacket last = PerkSyncPacket.NONE;

    private ClientPerks() {}

    public static void update(PerkSyncPacket packet) {
        last = packet;
    }

    public static List<PerkSyncPacket.Badge> badges() {
        return last.perks();
    }

    public static boolean owns(ResourceLocation perk) {
        return last.perks().stream().anyMatch(b -> b.perk().equals(perk));
    }

    public static float rpmMultiplier() {
        return last.rpmMultiplier();
    }
}
