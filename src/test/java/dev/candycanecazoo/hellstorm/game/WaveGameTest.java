package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.powerup.PowerUpDrops;
import dev.candycanecazoo.hellstorm.wave.WaveEffect;
import dev.candycanecazoo.hellstorm.wave.WaveEventDef;
import dev.candycanecazoo.hellstorm.wave.WaveTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A special round in the game loop, against a fake world. */
class WaveGameTest {
    private static final ResourceLocation ZOMBIE = ResourceLocation.tryBuild("test", "zombie");
    private static final ResourceLocation HOUND = ResourceLocation.tryBuild("test", "hound");
    private static final ResourceLocation HOUNDS = ResourceLocation.tryBuild("test", "hounds");
    private static final ResourceLocation MAX_AMMO = ResourceLocation.tryBuild("hellstorm", "max_ammo");

    @Test
    void aHoundsRoundSpawnsOnlyHoundsUnderFogAndItsLastKillDropsMaxAmmo() {
        GameSettings d = GameSettings.DEFAULT;
        GameSettings settings = new GameSettings(new GameSettings.Rounds(16, 0, 0.15, 0.004, 100, 100, 0.23, 0.23, 0.32, 24, 2, 1.0, 150, 2, 2),
                new GameSettings.Spawning(1, 24.0, 0.0), d.economy(), d.players(), d.windows(), d.doors(), d.box(), d.pap(), d.downs(),
                new GameSettings.PowerUps(0.0, 4, 600, 100, 400, 200, Integer.MAX_VALUE, 1.0, 0), d.traps(), d.waveEvents());
        WaveEventDef hounds = new WaveEventDef(new WaveTrigger.Rounds(List.of(2)), List.of(
                new WaveEffect.MobOverride(List.of(HOUND)), new WaveEffect.Count(0.5), new WaveEffect.Fog(0.6f),
                new WaveEffect.LastKillDrop(MAX_AMMO)), Optional.of("wave.test.hounds"));
        ZombiesMap map = ZombiesMap.EMPTY.withZone(new Zone(new ZoneId("a"), List.of(new BlockBox(0, 0, 0, 9, 3, 9)), true))
                .withFeature(new BlockPos(5, 0, 5), FeatureKind.SPAWN);
        List<GameEvent> seen = new ArrayList<>();
        GameEvents events = new GameEvents();
        events.subscribe(seen::add);
        FakeWorld world = new FakeWorld();
        UUID alice = UUID.randomUUID();
        world.positions.put(alice, new BlockPos(2, 0, 2));
        Game game = new Game(settings, GameMap.of(map, Set.of()), new MobRoster(List.of(new MobRoster.Entry(ZOMBIE, 1, 1, Optional.empty()))),
                RandomSource.create(5), events, new SurvivalContent(PowerUpDrops.Table.EMPTY, Map.of(HOUNDS, hounds)));
        game.join(alice);
        game.start();
        for (int i = 0; i < 30; i++) {
            game.tick(world);
        }
        assertTrue(world.specs.stream().allMatch(spec -> spec.profile().equals(ZOMBIE)), "round 1 is a normal round");
        assertEquals(Optional.empty(), game.waveEvent());
        game.mobs().forEach(mob -> game.onMobDied(mob, Optional.of(alice), false, false, BlockPos.ZERO));
        assertEquals(List.of(), world.drops, "a normal round's last kill drops nothing at drop chance 0");
        int before = world.specs.size();
        for (int i = 0; i < 40; i++) {
            game.tick(world);
        }
        assertEquals(Optional.of(HOUNDS), game.waveEvent().map(r -> r.event()));
        int start = seen.indexOf(new GameEvent.WaveEventStarted(HOUNDS, 2));
        assertTrue(start >= 0 && start < seen.indexOf(new GameEvent.RoundStart(2)), "wave_event_started comes before round_start");
        assertTrue(world.announcements.contains("wave.test.hounds"), "the wave announces itself");
        assertEquals(List.of(Optional.of(0.6f)), world.fog);
        List<GameWorld.ZombieSpec> round2 = world.specs.subList(before, world.specs.size());
        assertEquals(2, round2.size(), "half of round 2's 4 zombies");
        assertTrue(round2.stream().allMatch(spec -> spec.profile().equals(HOUND)), "only hounds spawn: " + round2);

        List<UUID> hounds2 = new ArrayList<>(game.mobs());
        game.onMobDied(hounds2.get(0), Optional.of(alice), false, false, BlockPos.ZERO);
        game.tick(world);
        assertEquals(List.of(), world.drops, "not the last kill yet");
        game.onMobDied(hounds2.get(1), Optional.empty(), false, false, new BlockPos(3, 0, 3));
        game.tick(world);
        assertEquals(List.of(MAX_AMMO), world.drops, "the last kill drops Max Ammo, whoever made it");
        assertEquals(List.of(Optional.of(0.6f), Optional.empty()), world.fog, "the fog lifts when the round ends");
        assertEquals(Optional.empty(), game.waveEvent());
    }
}
