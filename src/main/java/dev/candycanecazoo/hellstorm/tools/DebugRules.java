package dev.candycanecazoo.hellstorm.tools;

import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Per-world debugging switches set with {@code /hs debug}, saved like gamerules. */
public final class DebugRules extends SavedData {
    public enum Rule {
        /** Send script errors to ops' chat. They always go to the log. */
        SHOW_ERRORS(true),
        /** While holding the Zone Wand, see each game zombie's path and the doorways the pathfinder uses. */
        SHOW_PATHS(false),
        /** Games spawn no zombies. Rounds don't end while it's on, since their zombies never come. */
        NO_ZOMBIES(false);

        private final boolean defaultValue;

        Rule(boolean defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<Rule> byId(String id) {
            for (Rule rule : values()) {
                if (rule.id().equals(id)) {
                    return Optional.of(rule);
                }
            }
            return Optional.empty();
        }
    }

    private static final String NAME = Hellstorm.MOD_ID + "_debug_rules";

    private final Map<Rule, Boolean> values = new EnumMap<>(Rule.class);

    public static DebugRules get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DebugRules::load, DebugRules::new, NAME);
    }

    public boolean get(Rule rule) {
        return values.getOrDefault(rule, rule.defaultValue);
    }

    public void set(Rule rule, boolean value) {
        values.put(rule, value);
        setDirty();
    }

    private static DebugRules load(CompoundTag tag) {
        DebugRules rules = new DebugRules();
        for (Rule rule : Rule.values()) {
            if (tag.contains(rule.id())) {
                rules.values.put(rule, tag.getBoolean(rule.id()));
            }
        }
        return rules;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        values.forEach((rule, value) -> tag.putBoolean(rule.id(), value));
        return tag;
    }
}
