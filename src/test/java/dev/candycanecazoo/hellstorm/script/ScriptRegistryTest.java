package dev.candycanecazoo.hellstorm.script;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScriptRegistryTest {
    @Test
    void datapackPathsBecomeScriptIds() {
        assertEquals(Optional.of("mymap:announcer"), ScriptRegistry.scriptId(ResourceLocation.tryBuild("mymap", "hellstorm/scripts/announcer.lua")));
        assertEquals(Optional.of("mymap:rounds/hounds"), ScriptRegistry.scriptId(ResourceLocation.tryBuild("mymap", "hellstorm/scripts/rounds/hounds.lua")));
        assertEquals(Optional.empty(), ScriptRegistry.scriptId(ResourceLocation.tryBuild("mymap", "hellstorm/scripts/notes.txt")));
        assertEquals(Optional.empty(), ScriptRegistry.scriptId(ResourceLocation.tryBuild("mymap", "functions/x.lua")));
    }
}
