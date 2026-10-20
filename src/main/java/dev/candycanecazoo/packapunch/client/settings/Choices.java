package dev.candycanecazoo.packapunch.client.settings;

import dev.candycanecazoo.packapunch.net.OpenSettingsPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The server's list of what a machine can be set to. */
final class Choices {
    private Choices() {}

    /** Every id offered, plus {@code current} when it isn't (a perk or pool that's no longer loaded), so it stays selectable. */
    static List<ResourceLocation> ids(List<OpenSettingsPacket.Choice> choices, ResourceLocation current) {
        List<ResourceLocation> ids = new ArrayList<>(choices.stream().map(OpenSettingsPacket.Choice::id).toList());
        if (current != null && !ids.contains(current)) {
            ids.add(0, current);
        }
        return ids;
    }

    static Map<ResourceLocation, Component> names(List<OpenSettingsPacket.Choice> choices) {
        Map<ResourceLocation, Component> names = new LinkedHashMap<>();
        choices.forEach(c -> names.put(c.id(), c.name()));
        return names;
    }
}
