package dev.candycanecazoo.hellstorm.pap;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GunDefaultTest {
    /** Roughly what an upgraded TacZ gun carries. */
    private static final String GUN = "{GunId:\"tacz:glock_17\",GunCurrentAmmoCount:17,MaxDummyAmmo:85,"
            + "pap:{tier:1,damage_multiplier:1.5d,rpm_multiplier:1.25d},display:{Name:'{\"translate\":\"item.hellstorm.upgraded\"}'}}";

    private static CompoundTag snbt(String text) {
        try {
            return TagParser.parseTag(text);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError(e);
        }
    }

    private static CompoundTag applied(GunDefault.Method method, String given) {
        CompoundTag gun = snbt(GUN);
        new GunDefault(snbt(given), method).applyTo(gun);
        return gun;
    }

    private static GunDefault parse(String json) {
        return GunDefault.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow(false, e -> {});
    }

    @Test
    void mergeAddsAndOverwritesDeeply() {
        assertEquals(snbt("{GunId:\"tacz:glock_17\",GunCurrentAmmoCount:17,MaxDummyAmmo:200,"
                        + "pap:{tier:1,damage_multiplier:3.0d,rpm_multiplier:1.25d,glow:1b},display:{Name:'{\"translate\":\"item.hellstorm.upgraded\"}',Lore:['\"Hot\"']}}"),
                applied(GunDefault.Method.MERGE, "{MaxDummyAmmo:200,pap:{damage_multiplier:3.0d,glow:1b},display:{Lore:['\"Hot\"']}}"));
    }

    @Test
    void modifyOverwritesOnlyKeysTheGunHas() {
        assertEquals(snbt("{GunId:\"tacz:glock_17\",GunCurrentAmmoCount:17,MaxDummyAmmo:200,"
                        + "pap:{tier:1,damage_multiplier:3.0d,rpm_multiplier:1.25d},display:{Name:'{\"translate\":\"item.hellstorm.upgraded\"}'}}"),
                applied(GunDefault.Method.MODIFY, "{MaxDummyAmmo:200,pap:{damage_multiplier:3.0d,glow:1b},display:{Lore:['\"Hot\"']},Unbreakable:1b}"));
    }

    @Test
    void modifyReplacesAValueWhoseTypeDiffers() {
        assertTrue(applied(GunDefault.Method.MODIFY, "{GunId:{nested:1b}}").getCompound("GunId").getBoolean("nested"));
    }

    @Test
    void removeTakesOutTheGivenKeysByStructure() {
        assertEquals(snbt("{GunId:\"tacz:glock_17\",GunCurrentAmmoCount:17,pap:{tier:1,damage_multiplier:1.5d}}"),
                applied(GunDefault.Method.REMOVE, "{MaxDummyAmmo:0b,pap:{rpm_multiplier:0b,missing:0b},display:{},Absent:1b,GunId:{inside:1b}}"));
    }

    @Test
    void emptyNbtChangesNothing() {
        for (GunDefault.Method method : GunDefault.Method.values()) {
            assertEquals(snbt(GUN), applied(method, "{}"), method.id());
        }
    }

    @Test
    void methodDefaultsToModifyAndNbtToEmpty() {
        assertEquals(new GunDefault(new CompoundTag(), GunDefault.Method.MODIFY), parse("{}"));
        assertEquals(new GunDefault(snbt("{pap:{tier:2}}"), GunDefault.Method.REMOVE), parse("{ \"nbt\": \"{pap:{tier:2}}\", \"method\": \"remove\" }"));
        assertEquals(new GunDefault(snbt("{pap:{tier:2b}}"), GunDefault.Method.MERGE), parse("{ \"nbt\": { \"pap\": { \"tier\": 2 } }, \"method\": \"merge\" }"));
    }

    @Test
    void badMethodsAndNbtAreErrors() {
        for (String json : new String[]{"{ \"method\": \"replace\" }", "{ \"method\": \"MERGE\" }", "{ \"method\": 1 }", "{ \"nbt\": 5 }", "{ \"nbt\": \"{oops\" }"}) {
            assertTrue(GunDefault.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent(), json);
        }
    }

    @Test
    void roundTrips() {
        GunDefault gunDefault = parse("{ \"nbt\": { \"pap\": { \"damage_multiplier\": 2.5 } }, \"method\": \"merge\" }");
        assertEquals(gunDefault, parse(GunDefault.CODEC.encodeStart(JsonOps.INSTANCE, gunDefault).getOrThrow(false, e -> {}).toString()));
    }
}
