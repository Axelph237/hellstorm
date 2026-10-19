package dev.candycanecazoo.packapunch.gametest;

import com.mojang.authlib.GameProfile;
import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.mob.AiInjector;
import dev.candycanecazoo.packapunch.mob.MobProfile;
import dev.candycanecazoo.packapunch.mob.SupportTier;
import dev.candycanecazoo.packapunch.mob.goals.GameMeleeGoal;
import dev.candycanecazoo.packapunch.mob.goals.TargetGamePlayerGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static dev.candycanecazoo.packapunch.gametest.TestLayout.ROOM_Y;

@GameTestHolder(PackAPunch.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobGameTests {
    private static final AiInjector.Stats STATS = new AiInjector.Stats(42.0, 0.3, 5f);

    private MobGameTests() {}

    private static MobProfile profile(String entity, MobProfile.Ai ai) {
        return new MobProfile(ResourceLocation.tryParse(entity), 10, 1, Optional.empty(), ai, Optional.empty(), 1, 1, 5, true);
    }

    private static Set<Class<?>> goalTypes(Set<WrappedGoal> goals) {
        return goals.stream().map(g -> g.getGoal().getClass()).collect(Collectors.toSet());
    }

    @GameTest(template = "floor")
    public static void replaceLeavesOnlyTheGamesGoalsAndAppliesStats(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, ROOM_Y, 4));
        MobProfile.Ai used = AiInjector.inject(zombie, profile("minecraft:zombie", MobProfile.Ai.REPLACE), STATS, List::of);
        helper.assertTrue(used == MobProfile.Ai.REPLACE, "mode " + used);
        helper.assertTrue(goalTypes(zombie.goalSelector.getAvailableGoals()).equals(Set.of(FloatGoal.class, GameMeleeGoal.class)),
                "goals: " + goalTypes(zombie.goalSelector.getAvailableGoals()));
        helper.assertTrue(goalTypes(zombie.targetSelector.getAvailableGoals()).equals(Set.of(TargetGamePlayerGoal.class)),
                "target goals: " + goalTypes(zombie.targetSelector.getAvailableGoals()));
        helper.assertTrue(zombie.getMaxHealth() == 42f && zombie.getHealth() == 42f, "health " + zombie.getHealth() + "/" + zombie.getMaxHealth());
        helper.assertTrue(Math.abs(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.3) < 1e-9, "speed");
        helper.assertTrue(zombie.isPersistenceRequired(), "game zombies must not despawn");
        helper.succeed();
    }

    /** An iron golem never attacks players on its own. Injected, it targets the game's player. */
    @GameTest(template = "floor")
    public static void aPassiveMobBecomesAZombieThatTargetsTheGamesPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "pap_target_test"));
        player.moveTo(helper.absolutePos(new BlockPos(1, ROOM_Y, 1)).getCenter());
        IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(6, ROOM_Y, 6));
        helper.assertTrue(SupportTier.of(golem) == SupportTier.FULL, "golem tier " + SupportTier.of(golem));
        AiInjector.inject(golem, profile("minecraft:iron_golem", MobProfile.Ai.REPLACE), STATS, () -> List.of(player));
        helper.succeedWhen(() -> helper.assertTrue(golem.getTarget() == player, "golem target is " + golem.getTarget()));
    }

    @GameTest(template = "floor")
    public static void brainMobsAreForcedToAugment(GameTestHelper helper) {
        Piglin piglin = helper.spawn(EntityType.PIGLIN, new BlockPos(4, ROOM_Y, 4));
        int goalsBefore = piglin.goalSelector.getAvailableGoals().size();
        helper.assertTrue(SupportTier.of(piglin) == SupportTier.PARTIAL_BRAIN, "piglin tier " + SupportTier.of(piglin));
        MobProfile.Ai used = AiInjector.inject(piglin, profile("minecraft:piglin", MobProfile.Ai.REPLACE), STATS, List::of);
        helper.assertTrue(used == MobProfile.Ai.AUGMENT, "brain mob used " + used);
        helper.assertTrue(piglin.goalSelector.getAvailableGoals().size() == goalsBefore + 1, "augment keeps existing goals and adds melee");
        helper.succeed();
    }
}
