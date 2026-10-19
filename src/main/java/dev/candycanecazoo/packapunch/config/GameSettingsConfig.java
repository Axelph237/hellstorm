package dev.candycanecazoo.packapunch.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** The server-config side of {@link GameSettings}: one TOML entry per field, defaulting to {@link GameSettings#DEFAULT}. */
public final class GameSettingsConfig {
    private final ForgeConfigSpec.IntValue baseCount;
    private final ForgeConfigSpec.DoubleValue countPerRound;
    private final ForgeConfigSpec.DoubleValue playerFactor;
    private final ForgeConfigSpec.DoubleValue healthScale;
    private final ForgeConfigSpec.IntValue sprintFromRound;
    private final ForgeConfigSpec.DoubleValue walkSpeed;
    private final ForgeConfigSpec.DoubleValue sprintSpeed;
    private final ForgeConfigSpec.IntValue maxAlive;
    private final ForgeConfigSpec.IntValue spawnDelayTicks;
    private final ForgeConfigSpec.IntValue intermissionTicks;
    private final ForgeConfigSpec.IntValue countdownTicks;
    private final ForgeConfigSpec.IntValue gameOverTicks;
    private final ForgeConfigSpec.IntValue adjacentHops;
    private final ForgeConfigSpec.DoubleValue falloffBlocks;
    private final ForgeConfigSpec.DoubleValue minDistance;
    private final ForgeConfigSpec.IntValue startingPoints;
    private final ForgeConfigSpec.IntValue hit;
    private final ForgeConfigSpec.IntValue kill;
    private final ForgeConfigSpec.IntValue headshotKill;
    private final ForgeConfigSpec.IntValue meleeKill;
    private final ForgeConfigSpec.ConfigValue<List<? extends String>> startingGuns;
    private final ForgeConfigSpec.ConfigValue<List<? extends String>> startingItems;
    private final ForgeConfigSpec.IntValue reserveMagazines;

    GameSettingsConfig(ForgeConfigSpec.Builder b) {
        GameSettings d = GameSettings.DEFAULT;
        b.push("rounds");
        baseCount = b.comment("Zombies in round 1 for one player.").defineInRange("base_count", d.rounds().baseCount(), 1, 1000);
        countPerRound = b.comment("Extra zombies per round.").defineInRange("count_per_round", d.rounds().countPerRound(), 0.0, 100.0);
        playerFactor = b.comment("Each extra player multiplies the count by (1 + this).").defineInRange("player_factor", d.rounds().playerFactor(), 0.0, 10.0);
        healthScale = b.comment("Zombie health is the CoD curve (150 + 100 per round, x1.1 from round 10) times this, in Minecraft HP.")
                .defineInRange("health_scale", d.rounds().healthScale(), 0.001, 100.0);
        sprintFromRound = b.comment("First round where some zombies sprint.").defineInRange("sprint_from_round", d.rounds().sprintFromRound(), 1, 1000);
        walkSpeed = b.defineInRange("walk_speed", d.rounds().walkSpeed(), 0.01, 2.0);
        sprintSpeed = b.defineInRange("sprint_speed", d.rounds().sprintSpeed(), 0.01, 2.0);
        maxAlive = b.comment("Most zombies alive at once.").defineInRange("max_alive", d.rounds().maxAlive(), 1, 500);
        spawnDelayTicks = b.comment("Ticks between spawns in round 1. Shrinks by 2 per round, to a minimum of 5.")
                .defineInRange("spawn_delay_ticks", d.rounds().spawnDelayTicks(), 1, 1200);
        intermissionTicks = b.defineInRange("intermission_ticks", d.rounds().intermissionTicks(), 0, 12000);
        countdownTicks = b.defineInRange("countdown_ticks", d.rounds().countdownTicks(), 0, 12000);
        gameOverTicks = b.defineInRange("game_over_ticks", d.rounds().gameOverTicks(), 0, 12000);
        b.pop();
        b.push("spawning");
        adjacentHops = b.comment("Spawn markers are active in each player's zone and zones this many connections away.")
                .defineInRange("adjacent_hops", d.spawning().adjacentHops(), 0, 10);
        falloffBlocks = b.comment("Markers this far from the nearest player are half as likely to be picked.")
                .defineInRange("falloff_blocks", d.spawning().falloffBlocks(), 1.0, 512.0);
        minDistance = b.comment("Markers closer than this to any player are skipped.").defineInRange("min_distance", d.spawning().minDistance(), 0.0, 64.0);
        b.pop();
        b.push("economy");
        startingPoints = b.defineInRange("starting_points", d.economy().startingPoints(), 0, 1_000_000);
        hit = b.defineInRange("hit", d.economy().hit(), 0, 100_000);
        kill = b.defineInRange("kill", d.economy().kill(), 0, 100_000);
        headshotKill = b.defineInRange("headshot_kill", d.economy().headshotKill(), 0, 100_000);
        meleeKill = b.defineInRange("melee_kill", d.economy().meleeKill(), 0, 100_000);
        b.pop();
        b.push("players");
        startingGuns = b.comment("TacZ gun ids every player starts with.").defineListAllowEmpty("starting_guns",
                d.players().startingGuns().stream().map(ResourceLocation::toString).toList(), GameSettingsConfig::isId);
        startingItems = b.comment("Item ids every player starts with.").defineListAllowEmpty("starting_items",
                d.players().startingItems().stream().map(ResourceLocation::toString).toList(), GameSettingsConfig::isId);
        reserveMagazines = b.comment("Reserve ammo for starting guns, in magazines.").defineInRange("reserve_magazines", d.players().reserveMagazines(), 0, 100);
        b.pop();
    }

    private static boolean isId(Object o) {
        return o instanceof String s && ResourceLocation.tryParse(s) != null;
    }

    private static List<ResourceLocation> ids(List<? extends String> raw) {
        return raw.stream().map(ResourceLocation::tryParse).toList();
    }

    public GameSettings get() {
        return read(ConfigValueReader.CURRENT);
    }

    /** The values a fresh config file would have. Must equal {@link GameSettings#DEFAULT}. */
    public GameSettings defaults() {
        return read(ConfigValueReader.DEFAULT);
    }

    private enum ConfigValueReader {
        CURRENT, DEFAULT;

        <T> T read(ForgeConfigSpec.ConfigValue<T> value) {
            return this == CURRENT ? value.get() : value.getDefault();
        }
    }

    private GameSettings read(ConfigValueReader r) {
        return new GameSettings(
                new GameSettings.Rounds(r.read(baseCount), r.read(countPerRound), r.read(playerFactor), r.read(healthScale), r.read(sprintFromRound),
                        r.read(walkSpeed), r.read(sprintSpeed), r.read(maxAlive), r.read(spawnDelayTicks), r.read(intermissionTicks),
                        r.read(countdownTicks), r.read(gameOverTicks)),
                new GameSettings.Spawning(r.read(adjacentHops), r.read(falloffBlocks), r.read(minDistance)),
                new GameSettings.Economy(r.read(startingPoints), r.read(hit), r.read(kill), r.read(headshotKill), r.read(meleeKill)),
                new GameSettings.Players(ids(r.read(startingGuns)), ids(r.read(startingItems)), r.read(reserveMagazines)));
    }
}
