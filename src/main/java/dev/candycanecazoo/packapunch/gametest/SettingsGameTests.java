package dev.candycanecazoo.packapunch.gametest;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.data.ItemSpec;
import dev.candycanecazoo.packapunch.feature.ConfigBlockEntity;
import dev.candycanecazoo.packapunch.feature.MachineSettings;
import dev.candycanecazoo.packapunch.feature.MysteryBoxBlock;
import dev.candycanecazoo.packapunch.feature.PerkMachineBlock;
import dev.candycanecazoo.packapunch.feature.PowerSwitchBlock;
import dev.candycanecazoo.packapunch.feature.WallBuy;
import dev.candycanecazoo.packapunch.feature.WallBuyBlock;
import dev.candycanecazoo.packapunch.registry.PapRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

import static dev.candycanecazoo.packapunch.gametest.PurchaseGameTests.player;
import static dev.candycanecazoo.packapunch.gametest.TestLayout.ROOM_Y;

/** Saving from a machine's settings screen: the server keeps settings that fail the machine's checks off the block. */
@GameTestHolder(PackAPunch.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SettingsGameTests {
    private static final ResourceLocation AK47 = ResourceLocation.tryBuild("tacz", "ak47");

    private SettingsGameTests() {}

    @SuppressWarnings("unchecked")
    private static <E extends ConfigBlockEntity<?>> E place(GameTestHelper helper, int x, BlockState state) {
        BlockPos at = helper.absolutePos(new BlockPos(x, ROOM_Y, 2));
        helper.getLevel().setBlockAndUpdate(at, state);
        return (E) helper.getLevel().getBlockEntity(at);
    }

    private static <C> CompoundTag encode(ConfigBlockEntity<C> entity, C config) {
        return (CompoundTag) entity.codec().encodeStart(NbtOps.INSTANCE, config).getOrThrow(false, e -> {});
    }

    private static <C> void assertRefused(GameTestHelper helper, ConfigBlockEntity<C> entity, CompoundTag sent, String why) {
        C before = entity.config();
        Optional<String> problem = entity.trySet(sent);
        helper.assertTrue(problem.isPresent(), why + " should be refused");
        helper.assertTrue(entity.config().equals(before), why + " changed the settings anyway: " + entity.config());
    }

    @GameTest(template = "floor")
    public static void machinesRefuseSettingsTheirCommandsWouldRefuseAndKeepTheOldOnes(GameTestHelper helper) {
        PerkMachineBlock.Entity perk = place(helper, 1, PapRegistries.PERK_MACHINE.get().defaultBlockState());
        assertRefused(helper, perk, encode(perk, new PerkMachineBlock.Config(Optional.of(ResourceLocation.tryBuild("nope", "perk")), Optional.empty())), "an unloaded perk");
        ResourceLocation juggernog = ResourceLocation.tryBuild(PackAPunch.MOD_ID, "juggernog");
        helper.assertTrue(perk.trySet(encode(perk, new PerkMachineBlock.Config(Optional.of(juggernog), Optional.of(1234)))).isEmpty(), "Juggernog should be accepted");
        helper.assertTrue(perk.config().perk().equals(Optional.of(juggernog)), "the perk machine should sell Juggernog");
        helper.assertTrue(perk.display().map(d -> d.cost() == 1234).orElse(false), "the machine's own cost should replace Juggernog's, shows " + perk.display());

        MysteryBoxBlock.Entity box = place(helper, 3, PapRegistries.MYSTERY_BOX.get().defaultBlockState());
        assertRefused(helper, box, encode(box, new MysteryBoxBlock.Config(ResourceLocation.tryBuild("nope", "pool"), Optional.empty())), "an unloaded box pool");

        WallBuyBlock.Entity wallBuy = place(helper, 5, PapRegistries.WALL_BUY.get().defaultBlockState().setValue(WallBuyBlock.FACING, Direction.NORTH));
        ItemSpec apple = new ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "golden_apple"), 1, Optional.empty());
        assertRefused(helper, wallBuy, encode(wallBuy, new WallBuy.Config(ItemSpec.Gun.of(ResourceLocation.tryBuild("tacz", "nope")), 500,
                WallBuy.Repurchase.REFILL_AMMO, 250, 4500)), "an unknown gun");
        assertRefused(helper, wallBuy, encode(wallBuy, new WallBuy.Config(apple, 500, WallBuy.Repurchase.REFILL_AMMO, 250, 4500)),
                "refilling ammo on a golden apple");
        WallBuy.Config m4 = new WallBuy.Config(ItemSpec.Gun.of(AK47), 900, WallBuy.Repurchase.REFILL_AMMO, 300, 3000);
        helper.assertTrue(wallBuy.trySet(encode(wallBuy, m4)).isEmpty(), "a real gun should be accepted");
        helper.assertTrue(wallBuy.config().equals(m4), "the wall-buy should keep what was sent, has " + wallBuy.config());

        PowerSwitchBlock.Entity power = place(helper, 7, PapRegistries.POWER_SWITCH.get().defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.NORTH));
        CompoundTag negative = new CompoundTag();
        negative.putInt("cost", -5);
        assertRefused(helper, power, negative, "a negative cost");
        helper.succeed();
    }

    @GameTest(template = "floor")
    public static void onlyOpsInCreativeCanSaveSettings(GameTestHelper helper) {
        PowerSwitchBlock.Entity power = place(helper, 2, PapRegistries.POWER_SWITCH.get().defaultBlockState()
                .setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, Direction.NORTH));
        ServerPlayer player = player(helper, "pap_settings_stranger");
        player.moveTo(power.getBlockPos().above().getCenter());
        helper.assertFalse(MachineSettings.open(player, power.getBlockPos()), "a player who isn't an op got the settings screen");
        Optional<String> problem = MachineSettings.save(player, power.getBlockPos(), encode(power, new PowerSwitchBlock.Config(999)));
        helper.assertTrue(problem.isPresent(), "a player who isn't an op saved settings");
        helper.assertTrue(power.config().cost() == 0, "the power switch cost changed to " + power.config().cost());
        helper.succeed();
    }
}
