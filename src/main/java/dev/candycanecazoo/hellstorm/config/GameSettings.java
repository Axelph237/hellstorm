package dev.candycanecazoo.hellstorm.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Every tunable gameplay value, resolved once per game (see {@link SettingsResolver}). Each field has a codec name,
 * so a map can override any value by name with partial JSON. To make something configurable, add a field here,
 * a line in {@link GameSettingsConfig}, and a default.
 */
public record GameSettings(Rounds rounds, Spawning spawning, Economy economy, Players players, Windows windows, Doors doors, Box box, Pap pap,
                           Downs downs, PowerUps powerUps, Traps traps, WaveEvents waveEvents) {
    /**
     * Black Ops' round scaling (see {@link dev.candycanecazoo.hellstorm.game.RoundPlan}). {@code baseCount} and
     * {@code aiPerPlayer} are CoD's {@code zombie_max_ai} (24) and {@code zombie_ai_per_player} (6).
     */
    public record Rounds(
            int baseCount, int aiPerPlayer, double healthScale, double healthGrowth,
            int runFromRound, int sprintFromRound, double walkSpeed, double runSpeed, double sprintSpeed,
            int maxAlive, int spawnDelayTicks, double spawnDelayFactor, int intermissionMs, int countdownTicks, int gameOverTicks,
            double runnerChance) {
        public static final Codec<Rounds> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("base_count").forGetter(Rounds::baseCount),
                Codec.INT.fieldOf("ai_per_player").forGetter(Rounds::aiPerPlayer),
                Codec.DOUBLE.fieldOf("health_scale").forGetter(Rounds::healthScale),
                Codec.DOUBLE.fieldOf("health_growth").forGetter(Rounds::healthGrowth),
                Codec.INT.fieldOf("run_from_round").forGetter(Rounds::runFromRound),
                Codec.INT.fieldOf("sprint_from_round").forGetter(Rounds::sprintFromRound),
                Codec.DOUBLE.fieldOf("walk_speed").forGetter(Rounds::walkSpeed),
                Codec.DOUBLE.fieldOf("run_speed").forGetter(Rounds::runSpeed),
                Codec.DOUBLE.fieldOf("sprint_speed").forGetter(Rounds::sprintSpeed),
                Codec.INT.fieldOf("max_alive").forGetter(Rounds::maxAlive),
                Codec.INT.fieldOf("spawn_delay_ticks").forGetter(Rounds::spawnDelayTicks),
                Codec.DOUBLE.fieldOf("spawn_delay_factor").forGetter(Rounds::spawnDelayFactor),
                Codec.INT.fieldOf("intermission_ms").forGetter(Rounds::intermissionMs),
                Codec.INT.fieldOf("countdown_ticks").forGetter(Rounds::countdownTicks),
                Codec.INT.fieldOf("game_over_ticks").forGetter(Rounds::gameOverTicks),
                Codec.DOUBLE.fieldOf("runner_chance").forGetter(Rounds::runnerChance)
        ).apply(i, Rounds::new));

        /** The break between rounds in whole ticks, rounded up so a short break is never skipped. */
        public int intermissionTicks() {
            return (intermissionMs + 49) / 50;
        }
    }

    public record Spawning(double falloffBlocks, double minDistance) {
        public static final Codec<Spawning> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("falloff_blocks").forGetter(Spawning::falloffBlocks),
                Codec.DOUBLE.fieldOf("min_distance").forGetter(Spawning::minDistance)
        ).apply(i, Spawning::new));
    }

    /** {@code repairCapPerWindow}: the most repair points a player earns at any one window in a round, so moving between windows pays. */
    public record Economy(int startingPoints, int hit, int kill, int headshotKill, int meleeKill, int repair, int repairCapPerRound,
                          int repairCapPerWindow) {
        public static final Codec<Economy> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("starting_points").forGetter(Economy::startingPoints),
                Codec.INT.fieldOf("hit").forGetter(Economy::hit),
                Codec.INT.fieldOf("kill").forGetter(Economy::kill),
                Codec.INT.fieldOf("headshot_kill").forGetter(Economy::headshotKill),
                Codec.INT.fieldOf("melee_kill").forGetter(Economy::meleeKill),
                Codec.INT.fieldOf("repair").forGetter(Economy::repair),
                Codec.INT.fieldOf("repair_cap_per_round").forGetter(Economy::repairCapPerRound),
                Codec.INT.fieldOf("repair_cap_per_window").forGetter(Economy::repairCapPerWindow)
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

    /**
     * {@code weaponSlots}: TacZ guns a player can carry before buying one replaces the held gun (Off-Hand adds one).
     * {@code knockbackReduction}: how much of the knockback a player in a game takes away, 0 (all of it kept) to 1 (none).
     * {@code friendlyFire}: whether players in a game can hurt each other.
     */
    public record Players(List<ItemSpec.Gun> startingGuns, List<ItemSpec.Plain> startingItems, int reserveMagazines,
                          boolean startingGunsUnlimitedAmmo, int weaponSlots, int perkLimit, int maxPlayers, int reconnectGraceTicks,
                          double knockbackReduction, boolean friendlyFire) {
        public static final Codec<Players> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemSpec.Gun.GIVE_CODEC.listOf().fieldOf("starting_guns").forGetter(Players::startingGuns),
                ItemSpec.Plain.GIVE_CODEC.listOf().fieldOf("starting_items").forGetter(Players::startingItems),
                Codec.INT.fieldOf("reserve_magazines").forGetter(Players::reserveMagazines),
                Codec.BOOL.fieldOf("starting_guns_unlimited_ammo").forGetter(Players::startingGunsUnlimitedAmmo),
                Codec.INT.fieldOf("weapon_slots").forGetter(Players::weaponSlots),
                Codec.INT.fieldOf("perk_limit").forGetter(Players::perkLimit),
                Codec.INT.fieldOf("max_players").forGetter(Players::maxPlayers),
                Codec.INT.fieldOf("reconnect_grace_ticks").forGetter(Players::reconnectGraceTicks),
                Codec.doubleRange(0, 1).fieldOf("knockback_reduction").forGetter(Players::knockbackReduction),
                Codec.BOOL.fieldOf("friendly_fire").forGetter(Players::friendlyFire)
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

    /** The Mystery Crate. It moves after at least {@code minUsesBeforeMove} rolls, with {@code moveChance} per roll after that. */
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

    /** The upgrader (Dante's Toil). The multipliers are the default upgrade for a TacZ gun with no {@code punch_id}. */
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

    /**
     * Going down instead of dying. A teammate holding use within {@code reviveRange} for {@code reviveTicks} revives;
     * {@code bleedOutTicks} later a downed player spectates until the next round. Solo players with Saint's Brew get
     * {@code soloSelfRevives}. Vanilla regen is off in a game; health comes back at {@code regenPerSecond} once the player
     * has gone {@code regenDelayTicks} without damage.
     */
    public record Downs(int bleedOutTicks, int reviveTicks, double reviveRange, int soloSelfRevives, int regenDelayTicks, double regenPerSecond) {
        public static final Codec<Downs> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("bleed_out_ticks").forGetter(Downs::bleedOutTicks),
                Codec.INT.fieldOf("revive_ticks").forGetter(Downs::reviveTicks),
                Codec.DOUBLE.fieldOf("revive_range").forGetter(Downs::reviveRange),
                Codec.INT.fieldOf("solo_self_revives").forGetter(Downs::soloSelfRevives),
                Codec.INT.fieldOf("regen_delay_ticks").forGetter(Downs::regenDelayTicks),
                Codec.DOUBLE.fieldOf("regen_per_second").forGetter(Downs::regenPerSecond)
        ).apply(i, Downs::new));
    }

    /** Power-up drops from zombie kills: a chance per kill, at most {@code maxPerRound} a round. */
    /**
     * {@code dropIncrement} and {@code dropIncrementGrowth}: CoD's points-triggered drops. Once the team has earned that
     * many points since the last such drop, the next kill drops a power-up, and the next threshold is that much further
     * on, times the growth. {@code repeatCooldownKills}: once a power-up drops, it can't drop again until this many more
     * zombies have died.
     */
    public record PowerUps(double dropChance, int maxPerRound, int lifetimeTicks, int blinkTicks, int nukeBonus, int carpenterBonus,
                           int dropIncrement, double dropIncrementGrowth, int repeatCooldownKills) {
        public static final Codec<PowerUps> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("drop_chance").forGetter(PowerUps::dropChance),
                Codec.INT.fieldOf("max_per_round").forGetter(PowerUps::maxPerRound),
                Codec.INT.fieldOf("lifetime_ticks").forGetter(PowerUps::lifetimeTicks),
                Codec.INT.fieldOf("blink_ticks").forGetter(PowerUps::blinkTicks),
                Codec.INT.fieldOf("nuke_bonus").forGetter(PowerUps::nukeBonus),
                Codec.INT.fieldOf("carpenter_bonus").forGetter(PowerUps::carpenterBonus),
                Codec.INT.fieldOf("drop_increment").forGetter(PowerUps::dropIncrement),
                Codec.DOUBLE.fieldOf("drop_increment_growth").forGetter(PowerUps::dropIncrementGrowth),
                Codec.INT.fieldOf("repeat_cooldown_kills").forGetter(PowerUps::repeatCooldownKills)
        ).apply(i, PowerUps::new));
    }

    /** Defaults for new trap controllers; each trap stores its own values. */
    public record Traps(int defaultCost, int defaultActiveTicks, int defaultCooldownTicks) {
        public static final Codec<Traps> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("default_cost").forGetter(Traps::defaultCost),
                Codec.INT.fieldOf("default_active_ticks").forGetter(Traps::defaultActiveTicks),
                Codec.INT.fieldOf("default_cooldown_ticks").forGetter(Traps::defaultCooldownTicks)
        ).apply(i, Traps::new));
    }

    /** Special rounds from datapack wave events. {@code disabled} turns off single events by id, for example on one map. */
    public record WaveEvents(boolean enabled, List<ResourceLocation> disabled) {
        public static final Codec<WaveEvents> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.fieldOf("enabled").forGetter(WaveEvents::enabled),
                ResourceLocation.CODEC.listOf().fieldOf("disabled").forGetter(WaveEvents::disabled)
        ).apply(i, WaveEvents::new));

        public WaveEvents {
            disabled = List.copyOf(disabled);
        }
    }

    public static final Codec<GameSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Rounds.CODEC.fieldOf("rounds").forGetter(GameSettings::rounds),
            Spawning.CODEC.fieldOf("spawning").forGetter(GameSettings::spawning),
            Economy.CODEC.fieldOf("economy").forGetter(GameSettings::economy),
            Players.CODEC.fieldOf("players").forGetter(GameSettings::players),
            Windows.CODEC.fieldOf("windows").forGetter(GameSettings::windows),
            Doors.CODEC.fieldOf("doors").forGetter(GameSettings::doors),
            Box.CODEC.fieldOf("box").forGetter(GameSettings::box),
            Pap.CODEC.fieldOf("pap").forGetter(GameSettings::pap),
            Downs.CODEC.fieldOf("downs").forGetter(GameSettings::downs),
            PowerUps.CODEC.fieldOf("powerups").forGetter(GameSettings::powerUps),
            Traps.CODEC.fieldOf("traps").forGetter(GameSettings::traps),
            WaveEvents.CODEC.fieldOf("wave_events").forGetter(GameSettings::waveEvents)
    ).apply(i, GameSettings::new));

    public static final GameSettings DEFAULT = new GameSettings(
            new Rounds(24, 6, 0.15, 0.1, 5, 9, 0.23, 0.28, 0.32, 24, 40, 0.95, 5000, 100, 200, 0.1),
            new Spawning(24.0, 6.0),
            new Economy(500, 10, 60, 100, 130, 10, 500, 100),
            new Players(List.of(ItemSpec.Gun.of(ResourceLocation.tryBuild("tacz", "m1911"))), List.of(new ItemSpec.Plain(ResourceLocation.tryBuild("minecraft", "iron_sword"), 1, java.util.Optional.empty())), 4, true, 2, 5, 4, 1200, 0.85, false),
            new Windows(5, 4, 30, 15, 6.0),
            new Doors(750),
            new Box(950, 10, 80, 240, 4, 0.15),
            new Pap(5000, 60, 240, 1, 2.0, 1.5, 1.2),
            new Downs(600, 60, 2.5, 3, 80, 4.0),
            new PowerUps(0.02, 4, 600, 100, 400, 200, 2000, 1.14, 25),
            new Traps(1000, 200, 600),
            new WaveEvents(true, List.of()));
}
