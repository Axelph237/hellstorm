package dev.candycanecazoo.hellstorm.gametest;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.ForgeGameWorld;
import dev.candycanecazoo.hellstorm.game.GameManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static dev.candycanecazoo.hellstorm.gametest.TestLayout.ROOM_Y;

/** Game mobs drop nothing when they die, and neither do the mobs they leave behind. */
@GameTestHolder(Hellstorm.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobDropsGameTests {
    private MobDropsGameTests() {}

    /**
     * A player kills an armed, armoured game zombie, whose gear always drops outside a game, and a game slime, whose young
     * would drop slimeballs. Nothing drops: no items and no XP, from the zombie, the slime, or the young it splits into,
     * which are game mobs too and go when the game ends.
     */
    @GameTest(template = "floor", timeoutTicks = 100, batch = "mob_drops")
    public static void gameMobsAndTheYoungTheyLeaveDropNothing(GameTestHelper helper) {
        ServerPlayer player = PurchaseGameTests.player(helper, "pap_drops");
        DismembermentGameTests.Range range = DismembermentGameTests.range(helper, "drops", List.of());
        AABB area = new AABB(range.min(), range.max()).inflate(4);
        Zombie zombie = DismembermentGameTests.spawn(helper, range, "zombie");
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        zombie.setDropChance(EquipmentSlot.MAINHAND, 2f);
        zombie.setDropChance(EquipmentSlot.HEAD, 2f);
        Slime slime = helper.spawn(EntityType.SLIME, 2, ROOM_Y, 2);
        slime.setSize(2, true);
        slime.addTag(ForgeGameWorld.MOB_TAG);
        zombie.hurt(player.damageSources().playerAttack(player), 10_000f);
        slime.hurt(player.damageSources().playerAttack(player), 10_000f);
        helper.runAfterDelay(25, () -> {
            List<Slime> young = helper.getLevel().getEntitiesOfClass(Slime.class, area, Slime::isAlive);
            if (young.size() < 2) {
                GameManager.end(range.active());
                helper.fail("the slime should have split, " + young.size() + " slimes here");
            }
            young.forEach(s -> s.hurt(player.damageSources().playerAttack(player), 10_000f));
        });
        helper.runAfterDelay(30, () -> {
            List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area);
            List<ExperienceOrb> orbs = helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, area);
            if (!items.isEmpty() || !orbs.isEmpty()) {
                GameManager.end(range.active());
                helper.fail("game mobs dropped " + items.stream().map(ItemEntity::getItem).toList() + " and " + orbs.size() + " XP orbs");
            }
            Slime left = helper.spawn(EntityType.SLIME, 6, ROOM_Y, 6);
            left.setSize(2, true);
            left.addTag(ForgeGameWorld.MOB_TAG);
            left.hurt(player.damageSources().playerAttack(player), 10_000f);
        });
        helper.runAfterDelay(55, () -> {
            GameManager.end(range.active());
        });
        helper.runAfterDelay(60, () -> {
            List<Slime> lingering = helper.getLevel().getEntitiesOfClass(Slime.class, area, Slime::isAlive);
            helper.assertTrue(lingering.isEmpty(), "a game mob's young should go with the game, " + lingering.size() + " still here");
            helper.succeed();
        });
    }
}
