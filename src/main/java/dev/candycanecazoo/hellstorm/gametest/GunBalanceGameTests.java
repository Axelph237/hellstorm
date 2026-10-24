package dev.candycanecazoo.hellstorm.gametest;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IGun;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.apache.commons.lang3.tuple.Pair;

/** The mod's own gun balance datapack loads and applies: pistols carry more reserve, and weak guns hit harder. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GunBalanceGameTests {
    private GunBalanceGameTests() {}

    private static int reserve(ResourceLocation gun) {
        ItemStack stack = TaczBridge.createGun(gun, 4).orElseThrow(() -> new IllegalStateException(gun + " missing"));
        return IGun.getIGunOrNull(stack).getMaxDummyAmmoAmount(stack);
    }

    @GameTest(template = "floor")
    public static void aPistolCarriesMoreReserveThanTaczGivesIt(GameTestHelper helper) {
        int m1911 = reserve(ResourceLocation.tryBuild("tacz", "m1911"));
        int m870 = reserve(ResourceLocation.tryBuild("tacz", "m870"));
        int ak47 = reserve(ResourceLocation.tryBuild("tacz", "ak47"));
        helper.assertTrue(m1911 == 119, "the M1911 (7-round magazine, 4 magazines, x4.25) should carry 119 in reserve, not " + m1911);
        helper.assertTrue(m870 == 40, "the M870 (5 shells, 4 magazines, x2) should carry 40 in reserve, not " + m870);
        helper.assertTrue(ak47 == 120, "the AK-47 is the baseline and keeps 120 in reserve, not " + ak47);
        helper.succeed();
    }

    @GameTest(template = "floor")
    public static void theMinigunGetsAMagazineMoreReserveSinceItNeverUsesItsMagazine(GameTestHelper helper) {
        int minigun = reserve(ResourceLocation.tryBuild("tacz", "minigun"));
        helper.assertTrue(minigun == (4 + 1) * 30, "the minigun (30-round magazine, fed from the reserve) should carry 5 magazines, 150, not " + minigun);
        helper.succeed();
    }

    private static float hit(GameTestHelper helper, Zombie target, String gun, float amount, LogicalSide side) {
        EntityHurtByGunEvent.Pre event = new EntityHurtByGunEvent.Pre(null, target, null, ResourceLocation.tryBuild("tacz", gun), null,
                amount, Pair.of(null, null), false, 1f, side);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getBaseAmount();
    }

    @GameTest(template = "floor")
    public static void weakGunsHitHarderOnTheServer(GameTestHelper helper) {
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, TestLayout.ROOM_Y, 4));
        float glock = hit(helper, target, "glock_17", 6, LogicalSide.SERVER);
        helper.assertTrue(Math.abs(glock - 6 * 1.35f) < 0.01, "a Glock 17 hit should go from 6 to 8.1, not " + glock);
        float ak = hit(helper, target, "ak47", 9, LogicalSide.SERVER);
        helper.assertTrue(ak == 9, "an AK-47 hit should stay 9, not " + ak);
        float client = hit(helper, target, "glock_17", 6, LogicalSide.CLIENT);
        helper.assertTrue(client == 6, "the client's copy of the event isn't scaled, but went to " + client);
        target.discard();
        helper.succeed();
    }
}
