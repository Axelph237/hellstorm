package dev.candycanecazoo.packapunch.script;

import dev.candycanecazoo.packapunch.PackAPunch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Per-world scripting switches set with {@code /pap scripts rules}, saved like gamerules. */
public final class ScriptRules extends SavedData {
    public enum Rule {
        /** Script errors still go to the log, but not to ops' chat. */
        SILENCE_ERRORS(false);

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

    private static final String NAME = PackAPunch.MOD_ID + "_script_rules";

    private final Map<Rule, Boolean> values = new EnumMap<>(Rule.class);

    public static ScriptRules get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ScriptRules::load, ScriptRules::new, NAME);
    }

    public boolean get(Rule rule) {
        return values.getOrDefault(rule, rule.defaultValue);
    }

    public void set(Rule rule, boolean value) {
        values.put(rule, value);
        setDirty();
    }

    private static ScriptRules load(CompoundTag tag) {
        ScriptRules rules = new ScriptRules();
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
