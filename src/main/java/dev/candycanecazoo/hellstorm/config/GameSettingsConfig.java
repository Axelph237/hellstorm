package dev.candycanecazoo.hellstorm.config;

import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.feature.TrapControllerBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** The server-config side of {@link GameSettings}: one TOML entry per field, defaulting to {@link GameSettings#DEFAULT}. */
public final class GameSettingsConfig {
    private final ForgeConfigSpec.IntValue baseCount;
    private final ForgeConfigSpec.IntValue aiPerPlayer;
    private final ForgeConfigSpec.DoubleValue healthScale;
    private final ForgeConfigSpec.DoubleValue healthGrowth;
    private final ForgeConfigSpec.IntValue runFromRound;
    private final ForgeConfigSpec.IntValue sprintFromRound;
    private final ForgeConfigSpec.DoubleValue walkSpeed;
    private final ForgeConfigSpec.DoubleValue runSpeed;
    private final ForgeConfigSpec.DoubleValue sprintSpeed;
    private final ForgeConfigSpec.IntValue maxAlive;
    private final ForgeConfigSpec.IntValue spawnDelayTicks;
    private final ForgeConfigSpec.DoubleValue spawnDelayFactor;
    private final ForgeConfigSpec.IntValue dropIncrement;
    private final ForgeConfigSpec.DoubleValue dropIncrementGrowth;
    private final ForgeConfigSpec.IntValue repeatCooldownKills;
    private final ForgeConfigSpec.IntValue intermissionMs;
    private final ForgeConfigSpec.IntValue countdownTicks;
    private final ForgeConfigSpec.IntValue gameOverTicks;
    private final ForgeConfigSpec.DoubleValue runnerChance;
    private final ForgeConfigSpec.DoubleValue falloffBlocks;
    private final ForgeConfigSpec.DoubleValue minDistance;
    private final ForgeConfigSpec.IntValue startingPoints;
    private final ForgeConfigSpec.IntValue hit;
    private final ForgeConfigSpec.IntValue kill;
    private final ForgeConfigSpec.IntValue headshotKill;
    private final ForgeConfigSpec.IntValue meleeKill;
    private final ForgeConfigSpec.IntValue repair;
    private final ForgeConfigSpec.IntValue repairCapPerRound;
    private final ForgeConfigSpec.IntValue repairCapPerWindow;
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
    private final ForgeConfigSpec.IntValue maxPlayers;
    private final ForgeConfigSpec.IntValue reconnectGraceTicks;
    private final ForgeConfigSpec.DoubleValue knockbackReduction;
    private final ForgeConfigSpec.BooleanValue friendlyFire;
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
        baseCount = b.comment("Black Ops' zombie count: this plus an extra for players that grows with the round (see ai_per_player), then 25/30/50/70/90% of it in rounds 1 to 5. CoD's zombie_max_ai, 24.")
                .defineInRange("base_count", d.rounds().baseCount(), 1, 1000);
        aiPerPlayer = b.comment("CoD's zombie_ai_per_player (6): each player past the first adds this times the round multiplier; alone, half of it.")
                .defineInRange("ai_per_player", d.rounds().aiPerPlayer(), 0, 100);
        healthScale = b.comment("Zombie health follows CoD's curve (150, +100 per round to round 9) times this, in Minecraft HP.")
                .defineInRange("health_scale", d.rounds().healthScale(), 0.001, 100.0);
        healthGrowth = b.comment("After round 9, CoD's health grows by this fraction each round, compounding (CoD's own is 0.1).")
                .defineInRange("health_growth", d.rounds().healthGrowth(), 0.0, 10.0);
        runFromRound = b.comment("First round zombies run instead of walk (CoD: 5).").defineInRange("run_from_round", d.rounds().runFromRound(), 1, 1000);
        sprintFromRound = b.comment("First round zombies sprint (CoD: 9).").defineInRange("sprint_from_round", d.rounds().sprintFromRound(), 1, 1000);
        walkSpeed = b.defineInRange("walk_speed", d.rounds().walkSpeed(), 0.01, 2.0);
        runSpeed = b.defineInRange("run_speed", d.rounds().runSpeed(), 0.01, 2.0);
        sprintSpeed = b.defineInRange("sprint_speed", d.rounds().sprintSpeed(), 0.01, 2.0);
        maxAlive = b.comment("Most zombies alive at once.").defineInRange("max_alive", d.rounds().maxAlive(), 1, 500);
        spawnDelayTicks = b.comment("Ticks between spawns in round 1 (CoD: 2 seconds, 40 ticks).")
                .defineInRange("spawn_delay_ticks", d.rounds().spawnDelayTicks(), 1, 1200);
        spawnDelayFactor = b.comment("The spawn delay is multiplied by this each round, down to 2 ticks (CoD: 0.95, to 0.08 seconds).")
                .defineInRange("spawn_delay_factor", d.rounds().spawnDelayFactor(), 0.01, 1.0);
        intermissionMs = b.comment("The break between rounds, in milliseconds.").defineInRange("intermission_ms", d.rounds().intermissionMs(), 0, 600_000);
        countdownTicks = b.defineInRange("countdown_ticks", d.rounds().countdownTicks(), 0, 12000);
        gameOverTicks = b.defineInRange("game_over_ticks", d.rounds().gameOverTicks(), 0, 12000);
        runnerChance = b.comment("The chance each zombie is a runner, moving at least as fast as a walking player. The last zombie of a round always runs.")
                .defineInRange("runner_chance", d.rounds().runnerChance(), 0.0, 1.0);
        b.pop();
        b.push("spawning");
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
        repairCapPerWindow = b.comment("Most repair points one player can earn at any one window per round. Past it, boards still go on there but pay",
                        "nothing until the player repairs a different window or the next round starts. The default, 100, is ten boards at 10 points: a full window and four more.")
                .defineInRange("repair_cap_per_window", d.economy().repairCapPerWindow(), 0, 1_000_000);
        b.pop();
        b.push("windows");
        windowMaxWidth = b.comment("Widest window opening allowed, in blocks.").defineInRange("max_width", d.windows().maxWidth(), 1, 16);
        windowMaxHeight = b.comment("Tallest window opening allowed, in blocks.").defineInRange("max_height", d.windows().maxHeight(), 1, 16);
        tearTicks = b.comment("Ticks a zombie needs to tear off one board.").defineInRange("tear_ticks", d.windows().tearTicks(), 1, 1200);
        repairTicks = b.comment("Ticks between boards a player puts back while holding use.").defineInRange("repair_ticks", d.windows().repairTicks(), 1, 1200);
        linkRange = b.comment("A spawn marker outside every room links to the nearest window facing it within this many blocks.")
                .defineInRange("link_range", d.windows().linkRange(), 1.0, 64.0);
        b.pop();
        b.push("players");
        startingGuns = b.comment("TacZ guns every player starts with: a gun id, with NBT after it to merge onto the gun if needed, written the way /give takes an item, such as tacz:glock_17{GunFireMode:\"SEMI\"}.")
                .defineListAllowEmpty("starting_guns", d.players().startingGuns().stream().map(ItemSpec.Gun::asGive).toList(), GameSettingsConfig::isGun);
        startingItems = b.comment("Items every player starts with, written as /give takes them: an item id, with NBT after it if needed, such as lrtactical:melee{MeleeWeaponId:\"lrtactical:dagger\"}.")
                .defineListAllowEmpty("starting_items", d.players().startingItems().stream().map(ItemSpec.Plain::asGive).toList(), GameSettingsConfig::isItem);
        reserveMagazines = b.comment("Reserve ammo, in magazines, for every gun Hellstorm hands out: starting guns, wall-buys, the Mystery Crate and upgrades. A gun that feeds from the inventory (the minigun) gets one more.").defineInRange("reserve_magazines", d.players().reserveMagazines(), 0, 100);
        startingGunsUnlimitedAmmo = b.comment("Give starting guns an endless reserve. The magazine still needs reloading.")
                .define("starting_guns_unlimited_ammo", d.players().startingGunsUnlimitedAmmo());
        weaponSlots = b.comment("TacZ guns a player can carry. Buying another replaces the held gun. Off-Hand adds one.")
                .defineInRange("weapon_slots", d.players().weaponSlots(), 1, 9);
        perkLimit = b.comment("Most perks a player can hold at once.").defineInRange("perk_limit", d.players().perkLimit(), 0, 64);
        maxPlayers = b.comment("Most players in one game.").defineInRange("max_players", d.players().maxPlayers(), 1, 64);
        reconnectGraceTicks = b.comment("A player who disconnects mid-game keeps their place, points, and perks this long (20 ticks = 1 second).")
                .defineInRange("reconnect_grace_ticks", d.players().reconnectGraceTicks(), 0, 72_000);
        knockbackReduction = b.comment("How much knockback a player in a game is spared: 0 keeps all of it, 1 none (0.85 leaves 15%).")
                .defineInRange("knockback_reduction", d.players().knockbackReduction(), 0.0, 1.0);
        friendlyFire = b.comment("Let players in a game hurt each other: shots, melee, and anything else a player causes. Off by default.")
                .define("friendly_fire", d.players().friendlyFire());
        b.pop();
        b.push("doors");
        doorDefaultCost = b.comment("What a new door costs until set otherwise.").defineInRange("default_cost", d.doors().defaultCost(), 0, 1_000_000);
        b.pop();
        b.push("box");
        boxCost = b.defineInRange("cost", d.box().cost(), 0, 1_000_000);
        boxFireSaleCost = b.comment("What a roll costs during a Fire Sale.").defineInRange("fire_sale_cost", d.box().fireSaleCost(), 0, 1_000_000);
        boxRollTicks = b.comment("How long the Mystery Crate cycles items before showing the result.").defineInRange("roll_ticks", d.box().rollTicks(), 1, 1200);
        boxCollectTicks = b.comment("How long the roller has to take the result.").defineInRange("collect_ticks", d.box().collectTicks(), 1, 12000);
        boxMinUses = b.comment("Rolls at one location before the Mystery Crate can move.").defineInRange("min_uses_before_move", d.box().minUsesBeforeMove(), 0, 1000);
        boxMoveChance = b.comment("Chance per roll, after the minimum, that the Mystery Crate moves instead.").defineInRange("move_chance", d.box().moveChance(), 0.0, 1.0);
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
        soloSelfRevives = b.comment("Self-revives Saint's Brew gives a player playing alone.").defineInRange("solo_self_revives", d.downs().soloSelfRevives(), 0, 100);
        regenDelayTicks = b.comment("Ticks without damage before health starts coming back.").defineInRange("regen_delay_ticks", d.downs().regenDelayTicks(), 0, 1200);
        regenPerSecond = b.comment("Health regained per second once regen starts.").defineInRange("regen_per_second", d.downs().regenPerSecond(), 0.0, 100.0);
        b.pop();
        b.push("powerups");
        dropChance = b.comment("Chance any zombie kill drops a power-up, besides the points-triggered drops (CoD: 0.02).").defineInRange("drop_chance", d.powerUps().dropChance(), 0.0, 1.0);
        maxPowerUpsPerRound = b.defineInRange("max_per_round", d.powerUps().maxPerRound(), 0, 100);
        powerUpLifetimeTicks = b.comment("How long a dropped power-up waits to be picked up.").defineInRange("lifetime_ticks", d.powerUps().lifetimeTicks(), 20, 12000);
        powerUpBlinkTicks = b.comment("It blinks for this long before it disappears.").defineInRange("blink_ticks", d.powerUps().blinkTicks(), 0, 12000);
        nukeBonus = b.comment("Points each player gets from a Nuke.").defineInRange("nuke_bonus", d.powerUps().nukeBonus(), 0, 1_000_000);
        carpenterBonus = b.comment("Points each player gets from a Carpenter.").defineInRange("carpenter_bonus", d.powerUps().carpenterBonus(), 0, 1_000_000);
        dropIncrement = b.comment("CoD's points-triggered drops: the next kill drops a power-up once the team has earned this many points since the last one.")
                .defineInRange("drop_increment", d.powerUps().dropIncrement(), 1, Integer.MAX_VALUE);
        dropIncrementGrowth = b.comment("Each points-triggered drop makes the next threshold this much bigger (CoD: 1.14).")
                .defineInRange("drop_increment_growth", d.powerUps().dropIncrementGrowth(), 1.0, 10.0);
        repeatCooldownKills = b.comment("Once a power-up drops, it can't drop again until this many more zombies have died, so the same one doesn't come up twice in quick succession.")
                .defineInRange("repeat_cooldown_kills", d.powerUps().repeatCooldownKills(), 0, 10_000);
        b.pop();
        b.push("traps");
        trapDefaultCost = b.defineInRange("default_cost", d.traps().defaultCost(), 0, 1_000_000);
        trapDefaultActiveTicks = b.comment("How long a new trap's redstone signal stays on.").defineInRange("default_active_ticks", d.traps().defaultActiveTicks(), 1, TrapControllerBlock.Config.MAX_TICKS);
        trapDefaultCooldownTicks = b.defineInRange("default_cooldown_ticks", d.traps().defaultCooldownTicks(), 0, TrapControllerBlock.Config.MAX_TICKS);
        b.pop();
        b.push("wave_events");
        waveEventsEnabled = b.comment("Special rounds from datapack wave events.").define("enabled", d.waveEvents().enabled());
        waveEventsDisabled = b.comment("Wave event ids to leave out.").defineListAllowEmpty("disabled", List.of(), GameSettingsConfig::isId);
        b.pop();
    }

    private static boolean isId(Object o) {
        return o instanceof String s && ResourceLocation.tryParse(s) != null;
    }

    private static boolean isGun(Object o) {
        return o instanceof String s && ItemSpec.Gun.parse(s).result().isPresent();
    }

    private static List<ItemSpec.Gun> guns(List<? extends String> raw) {
        return raw.stream().map(s -> ItemSpec.Gun.parse(s).result().orElseThrow()).toList();
    }

    private static boolean isItem(Object o) {
        return o instanceof String s && ItemSpec.Plain.parse(s).result().isPresent();
    }

    private static List<ItemSpec.Plain> items(List<? extends String> raw) {
        return raw.stream().map(s -> ItemSpec.Plain.parse(s).result().orElseThrow()).toList();
    }

    private static List<ResourceLocation> ids(List<? extends String> raw) {
        return raw.stream().map(ResourceLocation::tryParse).toList();
    }

    public GameSettings get() {
        return read(ConfigValueReader.CURRENT);
    }

    /** For GameTests, which turn it on for one game and back off. */
    public ForgeConfigSpec.BooleanValue friendlyFire() {
        return friendlyFire;
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
                new GameSettings.Rounds(r.read(baseCount), r.read(aiPerPlayer), r.read(healthScale), r.read(healthGrowth), r.read(runFromRound), r.read(sprintFromRound),
                        r.read(walkSpeed), r.read(runSpeed), r.read(sprintSpeed), r.read(maxAlive), r.read(spawnDelayTicks), r.read(spawnDelayFactor), r.read(intermissionMs),
                        r.read(countdownTicks), r.read(gameOverTicks), r.read(runnerChance)),
                new GameSettings.Spawning(r.read(falloffBlocks), r.read(minDistance)),
                new GameSettings.Economy(r.read(startingPoints), r.read(hit), r.read(kill), r.read(headshotKill), r.read(meleeKill),
                        r.read(repair), r.read(repairCapPerRound), r.read(repairCapPerWindow)),
                new GameSettings.Players(guns(r.read(startingGuns)), items(r.read(startingItems)), r.read(reserveMagazines),
                        r.read(startingGunsUnlimitedAmmo), r.read(weaponSlots), r.read(perkLimit), r.read(maxPlayers), r.read(reconnectGraceTicks), r.read(knockbackReduction),
                        r.read(friendlyFire)),
                new GameSettings.Windows(r.read(windowMaxWidth), r.read(windowMaxHeight), r.read(tearTicks), r.read(repairTicks), r.read(linkRange)),
                new GameSettings.Doors(r.read(doorDefaultCost)),
                new GameSettings.Box(r.read(boxCost), r.read(boxFireSaleCost), r.read(boxRollTicks), r.read(boxCollectTicks), r.read(boxMinUses),
                        r.read(boxMoveChance)),
                new GameSettings.Pap(r.read(papDefaultCost), r.read(papProcessTicks), r.read(papCollectTicks), r.read(papMaxTier), r.read(papDamage),
                        r.read(papMagazine), r.read(papRpm)),
                new GameSettings.Downs(r.read(bleedOutTicks), r.read(reviveTicks), r.read(reviveRange), r.read(soloSelfRevives),
                        r.read(regenDelayTicks), r.read(regenPerSecond)),
                new GameSettings.PowerUps(r.read(dropChance), r.read(maxPowerUpsPerRound), r.read(powerUpLifetimeTicks), r.read(powerUpBlinkTicks),
                        r.read(nukeBonus), r.read(carpenterBonus), r.read(dropIncrement), r.read(dropIncrementGrowth), r.read(repeatCooldownKills)),
                new GameSettings.Traps(r.read(trapDefaultCost), r.read(trapDefaultActiveTicks), r.read(trapDefaultCooldownTicks)),
                new GameSettings.WaveEvents(r.read(waveEventsEnabled), ids(r.read(waveEventsDisabled))));
    }
}
