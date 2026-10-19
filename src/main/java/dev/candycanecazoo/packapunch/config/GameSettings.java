package dev.candycanecazoo.packapunch.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Every tunable gameplay value, resolved once per game (see {@link SettingsResolver}). Each field has a codec name,
 * so a map can override any value by name with partial JSON. To make something configurable, add a field here,
 * a line in {@link GameSettingsConfig}, and a default.
 */
public record GameSettings(Rounds rounds, Spawning spawning, Economy economy, Players players) {
    public record Rounds(
            int baseCount, double countPerRound, double playerFactor,
            double healthScale, int sprintFromRound, double walkSpeed, double sprintSpeed,
            int maxAlive, int spawnDelayTicks, int intermissionTicks, int countdownTicks, int gameOverTicks) {
        public static final Codec<Rounds> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("base_count").forGetter(Rounds::baseCount),
                Codec.DOUBLE.fieldOf("count_per_round").forGetter(Rounds::countPerRound),
                Codec.DOUBLE.fieldOf("player_factor").forGetter(Rounds::playerFactor),
                Codec.DOUBLE.fieldOf("health_scale").forGetter(Rounds::healthScale),
                Codec.INT.fieldOf("sprint_from_round").forGetter(Rounds::sprintFromRound),
                Codec.DOUBLE.fieldOf("walk_speed").forGetter(Rounds::walkSpeed),
                Codec.DOUBLE.fieldOf("sprint_speed").forGetter(Rounds::sprintSpeed),
                Codec.INT.fieldOf("max_alive").forGetter(Rounds::maxAlive),
                Codec.INT.fieldOf("spawn_delay_ticks").forGetter(Rounds::spawnDelayTicks),
                Codec.INT.fieldOf("intermission_ticks").forGetter(Rounds::intermissionTicks),
                Codec.INT.fieldOf("countdown_ticks").forGetter(Rounds::countdownTicks),
                Codec.INT.fieldOf("game_over_ticks").forGetter(Rounds::gameOverTicks)
        ).apply(i, Rounds::new));
    }

    public record Spawning(int adjacentHops, double falloffBlocks, double minDistance) {
        public static final Codec<Spawning> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("adjacent_hops").forGetter(Spawning::adjacentHops),
                Codec.DOUBLE.fieldOf("falloff_blocks").forGetter(Spawning::falloffBlocks),
                Codec.DOUBLE.fieldOf("min_distance").forGetter(Spawning::minDistance)
        ).apply(i, Spawning::new));
    }

    public record Economy(int startingPoints, int hit, int kill, int headshotKill, int meleeKill) {
        public static final Codec<Economy> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("starting_points").forGetter(Economy::startingPoints),
                Codec.INT.fieldOf("hit").forGetter(Economy::hit),
                Codec.INT.fieldOf("kill").forGetter(Economy::kill),
                Codec.INT.fieldOf("headshot_kill").forGetter(Economy::headshotKill),
                Codec.INT.fieldOf("melee_kill").forGetter(Economy::meleeKill)
        ).apply(i, Economy::new));
    }

    public record Players(List<ResourceLocation> startingGuns, List<ResourceLocation> startingItems, int reserveMagazines,
                          boolean startingGunsUnlimitedAmmo) {
        public static final Codec<Players> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf().fieldOf("starting_guns").forGetter(Players::startingGuns),
                ResourceLocation.CODEC.listOf().fieldOf("starting_items").forGetter(Players::startingItems),
                Codec.INT.fieldOf("reserve_magazines").forGetter(Players::reserveMagazines),
                Codec.BOOL.fieldOf("starting_guns_unlimited_ammo").forGetter(Players::startingGunsUnlimitedAmmo)
        ).apply(i, Players::new));

        public Players {
            startingGuns = List.copyOf(startingGuns);
            startingItems = List.copyOf(startingItems);
        }
    }

    public static final Codec<GameSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Rounds.CODEC.fieldOf("rounds").forGetter(GameSettings::rounds),
            Spawning.CODEC.fieldOf("spawning").forGetter(GameSettings::spawning),
            Economy.CODEC.fieldOf("economy").forGetter(GameSettings::economy),
            Players.CODEC.fieldOf("players").forGetter(GameSettings::players)
    ).apply(i, GameSettings::new));

    public static final GameSettings DEFAULT = new GameSettings(
            new Rounds(6, 2.5, 0.5, 0.1, 6, 0.23, 0.32, 24, 40, 20, 100, 200),
            new Spawning(1, 24.0, 6.0),
            new Economy(500, 10, 60, 100, 130),
            new Players(List.of(ResourceLocation.tryBuild("tacz", "m1911")), List.of(ResourceLocation.tryBuild("minecraft", "iron_sword")), 4, false));
}
