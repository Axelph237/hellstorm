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
    private final ForgeConfigSpec.DoubleValue healthGrowth;
    private final ForgeConfigSpec.DoubleValue speedGrowth;
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
    private final ForgeConfigSpec.IntValue repair;
    private final ForgeConfigSpec.IntValue repairCapPerRound;
    private final ForgeConfigSpec.IntValue windowMaxWidth;
    private final ForgeConfigSpec.IntValue windowMaxHeight;
    private final ForgeConfigSpec.IntValue tearTicks;
    private final ForgeConfigSpec.IntValue repairTicks;
    private final ForgeConfigSpec.DoubleValue linkRange;
    private final ForgeConfigSpec.ConfigValue<List<? extends String>> startingGuns;
    private final ForgeConfigSpec.ConfigValue<List<? extends String>> startingItems;
    private final ForgeConfigSpec.IntValue reserveMagazines;
    private final ForgeConfigSpec.BooleanValue startingGunsUnlimitedAmmo;
    private final ForgeConfigSpec.IntValue weaponSlots;
    private final ForgeConfigSpec.IntValue perkLimit;
    private final ForgeConfigSpec.IntValue doorDefaultCost;
    private final ForgeConfigSpec.IntValue boxCost;
    private final ForgeConfigSpec.IntValue boxFireSaleCost;
    private final ForgeConfigSpec.IntValue boxRollTicks;
    private final ForgeConfigSpec.IntValue boxCollectTicks;
    private final ForgeConfigSpec.IntValue boxMinUses;
    private final ForgeConfigSpec.DoubleValue boxMoveChance;
    private final ForgeConfigSpec.IntValue papDefaultCost;
    private final ForgeConfigSpec.IntValue papProcessTicks;
    private final ForgeConfigSpec.IntValue papCollectTicks;
    private final ForgeConfigSpec.IntValue papMaxTier;
    private final ForgeConfigSpec.DoubleValue papDamage;
    private final ForgeConfigSpec.DoubleValue papMagazine;
    private final ForgeConfigSpec.DoubleValue papRpm;
    private final ForgeConfigSpec.IntValue bleedOutTicks;
    private final ForgeConfigSpec.IntValue reviveTicks;
    private final ForgeConfigSpec.DoubleValue reviveRange;
    private final ForgeConfigSpec.IntValue soloSelfRevives;
    private final ForgeConfigSpec.IntValue regenDelayTicks;
    private final ForgeConfigSpec.DoubleValue regenPerSecond;
    private final ForgeConfigSpec.DoubleValue dropChance;
    private final ForgeConfigSpec.IntValue maxPowerUpsPerRound;
    private final ForgeConfigSpec.IntValue powerUpLifetimeTicks;
    private final ForgeConfigSpec.IntValue powerUpBlinkTicks;
    private final ForgeConfigSpec.IntValue nukeBonus;
    private final ForgeConfigSpec.IntValue carpenterBonus;
    private final ForgeConfigSpec.IntValue trapDefaultCost;
    private final ForgeConfigSpec.IntValue trapDefaultActiveTicks;
    private final ForgeConfigSpec.IntValue trapDefaultCooldownTicks;
    private final ForgeConfigSpec.BooleanValue waveEventsEnabled;
    private final ForgeConfigSpec.ConfigValue<List<? extends String>> waveEventsDisabled;

    GameSettingsConfig(ForgeConfigSpec.Builder b) {
        GameSettings d = GameSettings.DEFAULT;
        b.push("rounds");
        baseCount = b.comment("Zombies in round 1 for one player.").defineInRange("base_count", d.rounds().baseCount(), 1, 1000);
        countPerRound = b.comment("Extra zombies per round.").defineInRange("count_per_round", d.rounds().countPerRound(), 0.0, 100.0);
        playerFactor = b.comment("Each extra player multiplies the count by (1 + this).").defineInRange("player_factor", d.rounds().playerFactor(), 0.0, 10.0);
        healthScale = b.comment("Zombie health follows CoD's curve (150, +100 per round to round 9) times this, in Minecraft HP.")
                .defineInRange("health_scale", d.rounds().healthScale(), 0.001, 100.0);
        healthGrowth = b.comment("After round 9, CoD's health grows by this fraction each round, compounding (0.004 = 0.4%; CoD itself uses 0.1).")
                .defineInRange("health_growth", d.rounds().healthGrowth(), 0.0, 10.0);
        speedGrowth = b.comment("Zombie walk and sprint speed grow by this fraction each round, compounding (0.0001 = 0.01% per round).")
                .defineInRange("speed_growth", d.rounds().speedGrowth(), 0.0, 10.0);
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
        repair = b.comment("Points per window board repaired.").defineInRange("repair", d.economy().repair(), 0, 100_000);
        repairCapPerRound = b.comment("Most repair points one player can earn per round.")
                .defineInRange("repair_cap_per_round", d.economy().repairCapPerRound(), 0, 1_000_000);
        b.pop();
        b.push("windows");
        windowMaxWidth = b.comment("Widest window opening allowed, in blocks.").defineInRange("max_width", d.windows().maxWidth(), 1, 16);
        windowMaxHeight = b.comment("Tallest window opening allowed, in blocks.").defineInRange("max_height", d.windows().maxHeight(), 1, 16);
        tearTicks = b.comment("Ticks a zombie needs to tear off one board.").defineInRange("tear_ticks", d.windows().tearTicks(), 1, 1200);
        repairTicks = b.comment("Ticks between boards a player puts back while holding use.").defineInRange("repair_ticks", d.windows().repairTicks(), 1, 1200);
        linkRange = b.comment("A spawn marker outside every zone links to the nearest window facing it within this many blocks.")
                .defineInRange("link_range", d.windows().linkRange(), 1.0, 64.0);
        b.pop();
        b.push("players");
        startingGuns = b.comment("TacZ gun ids every player starts with.").defineListAllowEmpty("starting_guns",
                d.players().startingGuns().stream().map(ResourceLocation::toString).toList(), GameSettingsConfig::isId);
        startingItems = b.comment("Item ids every player starts with.").defineListAllowEmpty("starting_items",
                d.players().startingItems().stream().map(ResourceLocation::toString).toList(), GameSettingsConfig::isId);
        reserveMagazines = b.comment("Reserve ammo for starting guns, in magazines.").defineInRange("reserve_magazines", d.players().reserveMagazines(), 0, 100);
        startingGunsUnlimitedAmmo = b.comment("Give starting guns an endless reserve. The magazine still needs reloading.")
                .define("starting_guns_unlimited_ammo", d.players().startingGunsUnlimitedAmmo());
        weaponSlots = b.comment("TacZ guns a player can carry. Buying another replaces the held gun. Mule Kick adds one.")
                .defineInRange("weapon_slots", d.players().weaponSlots(), 1, 9);
        perkLimit = b.comment("Most perks a player can hold at once.").defineInRange("perk_limit", d.players().perkLimit(), 0, 64);
        b.pop();
        b.push("doors");
        doorDefaultCost = b.comment("What a new door costs until set otherwise.").defineInRange("default_cost", d.doors().defaultCost(), 0, 1_000_000);
        b.pop();
        b.push("box");
        boxCost = b.defineInRange("cost", d.box().cost(), 0, 1_000_000);
        boxFireSaleCost = b.comment("What a roll costs during a Fire Sale.").defineInRange("fire_sale_cost", d.box().fireSaleCost(), 0, 1_000_000);
        boxRollTicks = b.comment("How long the box cycles items before showing the result.").defineInRange("roll_ticks", d.box().rollTicks(), 1, 1200);
        boxCollectTicks = b.comment("How long the roller has to take the result.").defineInRange("collect_ticks", d.box().collectTicks(), 1, 12000);
        boxMinUses = b.comment("Rolls at one location before the box can move.").defineInRange("min_uses_before_move", d.box().minUsesBeforeMove(), 0, 1000);
        boxMoveChance = b.comment("Chance per roll, after the minimum, that the box moves instead.").defineInRange("move_chance", d.box().moveChance(), 0.0, 1.0);
        b.pop();
        b.push("pap");
        papDefaultCost = b.defineInRange("default_cost", d.pap().defaultCost(), 0, 1_000_000);
        papProcessTicks = b.comment("How long the machine works on an item.").defineInRange("process_ticks", d.pap().processTicks(), 1, 1200);
        papCollectTicks = b.comment("How long the owner has to take the upgraded item.").defineInRange("collect_ticks", d.pap().collectTicks(), 1, 12000);
        papMaxTier = b.comment("How many times a TacZ gun with no punch_id can be upgraded.").defineInRange("max_tier", d.pap().maxTier(), 1, 10);
        papDamage = b.comment("Damage multiplier per upgrade tier for TacZ guns.").defineInRange("damage_multiplier", d.pap().damageMultiplier(), 0.1, 100.0);
        papMagazine = b.comment("Magazine and reserve multiplier per upgrade tier.").defineInRange("magazine_multiplier", d.pap().magazineMultiplier(), 0.1, 100.0);
        papRpm = b.comment("Fire rate multiplier per upgrade tier.").defineInRange("rpm_multiplier", d.pap().rpmMultiplier(), 0.1, 100.0);
        b.pop();
        b.push("downs");
        bleedOutTicks = b.comment("How long a downed player has before bleeding out and spectating until the next round.")
                .defineInRange("bleed_out_ticks", d.downs().bleedOutTicks(), 20, 12000);
        reviveTicks = b.comment("How long a teammate holds use to revive.").defineInRange("revive_ticks", d.downs().reviveTicks(), 1, 1200);
        reviveRange = b.comment("How close a reviver must stay, in blocks.").defineInRange("revive_range", d.downs().reviveRange(), 0.5, 16.0);
        soloSelfRevives = b.comment("Self-revives Quick Revive gives a player playing alone.").defineInRange("solo_self_revives", d.downs().soloSelfRevives(), 0, 100);
        regenDelayTicks = b.comment("Ticks without damage before health starts coming back.").defineInRange("regen_delay_ticks", d.downs().regenDelayTicks(), 0, 1200);
        regenPerSecond = b.comment("Health regained per second once regen starts.").defineInRange("regen_per_second", d.downs().regenPerSecond(), 0.0, 100.0);
        b.pop();
        b.push("powerups");
        dropChance = b.comment("Chance a zombie kill drops a power-up.").defineInRange("drop_chance", d.powerUps().dropChance(), 0.0, 1.0);
        maxPowerUpsPerRound = b.defineInRange("max_per_round", d.powerUps().maxPerRound(), 0, 100);
        powerUpLifetimeTicks = b.comment("How long a dropped power-up waits to be picked up.").defineInRange("lifetime_ticks", d.powerUps().lifetimeTicks(), 20, 12000);
        powerUpBlinkTicks = b.comment("It blinks for this long before it disappears.").defineInRange("blink_ticks", d.powerUps().blinkTicks(), 0, 12000);
        nukeBonus = b.comment("Points each player gets from a Nuke.").defineInRange("nuke_bonus", d.powerUps().nukeBonus(), 0, 1_000_000);
        carpenterBonus = b.comment("Points each player gets from a Carpenter.").defineInRange("carpenter_bonus", d.powerUps().carpenterBonus(), 0, 1_000_000);
        b.pop();
        b.push("traps");
        trapDefaultCost = b.defineInRange("default_cost", d.traps().defaultCost(), 0, 1_000_000);
        trapDefaultActiveTicks = b.comment("How long a new trap's redstone signal stays on.").defineInRange("default_active_ticks", d.traps().defaultActiveTicks(), 1, 12000);
        trapDefaultCooldownTicks = b.defineInRange("default_cooldown_ticks", d.traps().defaultCooldownTicks(), 0, 72000);
        b.pop();
        b.push("wave_events");
        waveEventsEnabled = b.comment("Special rounds from datapack wave events.").define("enabled", d.waveEvents().enabled());
        waveEventsDisabled = b.comment("Wave event ids to leave out.").defineListAllowEmpty("disabled", List.of(), GameSettingsConfig::isId);
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
                        r.read(walkSpeed), r.read(sprintSpeed), r.read(healthGrowth), r.read(speedGrowth), r.read(maxAlive), r.read(spawnDelayTicks), r.read(intermissionTicks),
                        r.read(countdownTicks), r.read(gameOverTicks)),
                new GameSettings.Spawning(r.read(adjacentHops), r.read(falloffBlocks), r.read(minDistance)),
                new GameSettings.Economy(r.read(startingPoints), r.read(hit), r.read(kill), r.read(headshotKill), r.read(meleeKill),
                        r.read(repair), r.read(repairCapPerRound)),
                new GameSettings.Players(ids(r.read(startingGuns)), ids(r.read(startingItems)), r.read(reserveMagazines),
                        r.read(startingGunsUnlimitedAmmo), r.read(weaponSlots), r.read(perkLimit)),
                new GameSettings.Windows(r.read(windowMaxWidth), r.read(windowMaxHeight), r.read(tearTicks), r.read(repairTicks), r.read(linkRange)),
                new GameSettings.Doors(r.read(doorDefaultCost)),
                new GameSettings.Box(r.read(boxCost), r.read(boxFireSaleCost), r.read(boxRollTicks), r.read(boxCollectTicks), r.read(boxMinUses),
                        r.read(boxMoveChance)),
                new GameSettings.Pap(r.read(papDefaultCost), r.read(papProcessTicks), r.read(papCollectTicks), r.read(papMaxTier), r.read(papDamage),
                        r.read(papMagazine), r.read(papRpm)),
                new GameSettings.Downs(r.read(bleedOutTicks), r.read(reviveTicks), r.read(reviveRange), r.read(soloSelfRevives),
                        r.read(regenDelayTicks), r.read(regenPerSecond)),
                new GameSettings.PowerUps(r.read(dropChance), r.read(maxPowerUpsPerRound), r.read(powerUpLifetimeTicks), r.read(powerUpBlinkTicks),
                        r.read(nukeBonus), r.read(carpenterBonus)),
                new GameSettings.Traps(r.read(trapDefaultCost), r.read(trapDefaultActiveTicks), r.read(trapDefaultCooldownTicks)),
                new GameSettings.WaveEvents(r.read(waveEventsEnabled), ids(r.read(waveEventsDisabled))));
    }
}
