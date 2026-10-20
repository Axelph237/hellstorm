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
public record GameSettings(Rounds rounds, Spawning spawning, Economy economy, Players players, Windows windows, Doors doors, Box box, Pap pap) {
    public record Rounds(
            int baseCount, double countPerRound, double playerFactor,
            double healthScale, int sprintFromRound, double walkSpeed, double sprintSpeed, double healthGrowth, double speedGrowth,
            int maxAlive, int spawnDelayTicks, int intermissionTicks, int countdownTicks, int gameOverTicks) {
        public static final Codec<Rounds> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("base_count").forGetter(Rounds::baseCount),
                Codec.DOUBLE.fieldOf("count_per_round").forGetter(Rounds::countPerRound),
                Codec.DOUBLE.fieldOf("player_factor").forGetter(Rounds::playerFactor),
                Codec.DOUBLE.fieldOf("health_scale").forGetter(Rounds::healthScale),
                Codec.INT.fieldOf("sprint_from_round").forGetter(Rounds::sprintFromRound),
                Codec.DOUBLE.fieldOf("walk_speed").forGetter(Rounds::walkSpeed),
                Codec.DOUBLE.fieldOf("sprint_speed").forGetter(Rounds::sprintSpeed),
                Codec.DOUBLE.fieldOf("health_growth").forGetter(Rounds::healthGrowth),
                Codec.DOUBLE.fieldOf("speed_growth").forGetter(Rounds::speedGrowth),
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

    public record Economy(int startingPoints, int hit, int kill, int headshotKill, int meleeKill, int repair, int repairCapPerRound) {
        public static final Codec<Economy> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("starting_points").forGetter(Economy::startingPoints),
                Codec.INT.fieldOf("hit").forGetter(Economy::hit),
                Codec.INT.fieldOf("kill").forGetter(Economy::kill),
                Codec.INT.fieldOf("headshot_kill").forGetter(Economy::headshotKill),
                Codec.INT.fieldOf("melee_kill").forGetter(Economy::meleeKill),
                Codec.INT.fieldOf("repair").forGetter(Economy::repair),
                Codec.INT.fieldOf("repair_cap_per_round").forGetter(Economy::repairCapPerRound)
        ).apply(i, Economy::new));
    }

    /** Window sizes are checked when windows are made; the timings drive tearing and repairing boards. */
    public record Windows(int maxWidth, int maxHeight, int tearTicks, int repairTicks, double linkRange) {
        public static final Codec<Windows> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("max_width").forGetter(Windows::maxWidth),
                Codec.INT.fieldOf("max_height").forGetter(Windows::maxHeight),
                Codec.INT.fieldOf("tear_ticks").forGetter(Windows::tearTicks),
                Codec.INT.fieldOf("repair_ticks").forGetter(Windows::repairTicks),
                Codec.DOUBLE.fieldOf("link_range").forGetter(Windows::linkRange)
        ).apply(i, Windows::new));
    }

    /** {@code weaponSlots}: TacZ guns a player can carry before buying one replaces the held gun (Mule Kick adds one). */
    public record Players(List<ResourceLocation> startingGuns, List<ResourceLocation> startingItems, int reserveMagazines,
                          boolean startingGunsUnlimitedAmmo, int weaponSlots, int perkLimit) {
        public static final Codec<Players> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf().fieldOf("starting_guns").forGetter(Players::startingGuns),
                ResourceLocation.CODEC.listOf().fieldOf("starting_items").forGetter(Players::startingItems),
                Codec.INT.fieldOf("reserve_magazines").forGetter(Players::reserveMagazines),
                Codec.BOOL.fieldOf("starting_guns_unlimited_ammo").forGetter(Players::startingGunsUnlimitedAmmo),
                Codec.INT.fieldOf("weapon_slots").forGetter(Players::weaponSlots),
                Codec.INT.fieldOf("perk_limit").forGetter(Players::perkLimit)
        ).apply(i, Players::new));

        public Players {
            startingGuns = List.copyOf(startingGuns);
            startingItems = List.copyOf(startingItems);
        }
    }

    public record Doors(int defaultCost) {
        public static final Codec<Doors> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("default_cost").forGetter(Doors::defaultCost)
        ).apply(i, Doors::new));
    }

    /** The Mystery Box. It moves after at least {@code minUsesBeforeMove} rolls, with {@code moveChance} per roll after that. */
    public record Box(int cost, int fireSaleCost, int rollTicks, int collectTicks, int minUsesBeforeMove, double moveChance) {
        public static final Codec<Box> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("cost").forGetter(Box::cost),
                Codec.INT.fieldOf("fire_sale_cost").forGetter(Box::fireSaleCost),
                Codec.INT.fieldOf("roll_ticks").forGetter(Box::rollTicks),
                Codec.INT.fieldOf("collect_ticks").forGetter(Box::collectTicks),
                Codec.INT.fieldOf("min_uses_before_move").forGetter(Box::minUsesBeforeMove),
                Codec.DOUBLE.fieldOf("move_chance").forGetter(Box::moveChance)
        ).apply(i, Box::new));
    }

    /** Pack-a-Punch. The multipliers are the default upgrade for a TacZ gun with no {@code punch_id}. */
    public record Pap(int defaultCost, int processTicks, int collectTicks, int maxTier, double damageMultiplier,
                      double magazineMultiplier, double rpmMultiplier) {
        public static final Codec<Pap> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("default_cost").forGetter(Pap::defaultCost),
                Codec.INT.fieldOf("process_ticks").forGetter(Pap::processTicks),
                Codec.INT.fieldOf("collect_ticks").forGetter(Pap::collectTicks),
                Codec.INT.fieldOf("max_tier").forGetter(Pap::maxTier),
                Codec.DOUBLE.fieldOf("damage_multiplier").forGetter(Pap::damageMultiplier),
                Codec.DOUBLE.fieldOf("magazine_multiplier").forGetter(Pap::magazineMultiplier),
                Codec.DOUBLE.fieldOf("rpm_multiplier").forGetter(Pap::rpmMultiplier)
        ).apply(i, Pap::new));
    }

    public static final Codec<GameSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Rounds.CODEC.fieldOf("rounds").forGetter(GameSettings::rounds),
            Spawning.CODEC.fieldOf("spawning").forGetter(GameSettings::spawning),
            Economy.CODEC.fieldOf("economy").forGetter(GameSettings::economy),
            Players.CODEC.fieldOf("players").forGetter(GameSettings::players),
            Windows.CODEC.fieldOf("windows").forGetter(GameSettings::windows),
            Doors.CODEC.fieldOf("doors").forGetter(GameSettings::doors),
            Box.CODEC.fieldOf("box").forGetter(GameSettings::box),
            Pap.CODEC.fieldOf("pap").forGetter(GameSettings::pap)
    ).apply(i, GameSettings::new));

    public static final GameSettings DEFAULT = new GameSettings(
            new Rounds(6, 2.5, 0.5, 0.15, 6, 0.23, 0.32, 0.004, 0.0001, 24, 40, 20, 100, 200),
            new Spawning(1, 24.0, 6.0),
            new Economy(500, 10, 60, 100, 130, 10, 500),
            new Players(List.of(ResourceLocation.tryBuild("tacz", "m1911")), List.of(ResourceLocation.tryBuild("minecraft", "iron_sword")), 4, false, 2, 4),
            new Windows(5, 4, 30, 15, 6.0),
            new Doors(750),
            new Box(950, 10, 80, 240, 4, 0.15),
            new Pap(5000, 60, 240, 1, 2.0, 1.5, 1.2));
}
