package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.powerup.PowerUpDrops;
import dev.candycanecazoo.hellstorm.wave.RoundRules;
import dev.candycanecazoo.hellstorm.wave.WaveSchedule;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One running game: its phase, players, and zombies. All rules live here and are pure apart from {@link GameWorld}.
 * Driven by the server thread through {@link #tick} and the {@code on...} callbacks.
 */
public final class Game {
    private static final int STATUS_INTERVAL = 4;
    private static final int REVIVE_COLOR = 0xFFFFFF;
    private static final int BLEED_COLOR = 0xC81E1E;
    private static final int REPAIR_COLOR = 0xFFFFFF;

    private final GameSettings settings;
    private final GameMap map;
    private final MobRoster roster;
    private final RandomSource random;
    private final GameEvents events;
    private final Map<UUID, PlayerState> players = new LinkedHashMap<>();
    private final Set<UUID> mobs = new LinkedHashSet<>();
    /** The round's last zombie, once it has been made to run. */
    private UUID lastRunner;
    private GamePhase phase = new GamePhase.Lobby();
    private long ticks;
    private boolean warnedNoSpawns;
    /** The last round that had no mob profile it could spawn, said once. */
    private int warnedNoProfile;
    private boolean finished;
    private boolean powerOn;
    private Set<ZoneId> unlocked;
    private final Set<ZoneId> discovered = new LinkedHashSet<>();
    private final Set<String> openDoors = new LinkedHashSet<>();
    private final Map<ResourceLocation, GameWorld.Timer> timed = new LinkedHashMap<>();
    /** Downed player to the teammate holding use on them and the last tick they did. */
    private final Map<UUID, ReviveHold> reviveHolds = new LinkedHashMap<>();

    /** A revive survives this many ticks between presses, so right click's repeat rate (every 4 ticks) counts as holding. */
    private static final int HOLD_GRACE_TICKS = 5;

    private record ReviveHold(UUID reviver, long tick) {}

    /** Player to the window they're holding use on. A hold survives {@link #HOLD_GRACE_TICKS} between presses, like a revive. */
    private final Map<UUID, RepairHold> repairHolds = new LinkedHashMap<>();

    /** {@code started} is the tick the board going on now was started, {@code heard} the last tick use was held. */
    private record RepairHold(String window, long started, long heard) {}
    private final SurvivalContent content;
    private int dropsThisRound;
    /** Points the whole team has earned this game, and where the next points-triggered drop is due (CoD's score_to_drop). */
    private long earned;
    private long nextDropAt;
    private double dropIncrement;
    private final List<Drop> pendingDrops = new ArrayList<>();
    /** Power-ups picked up lately, and how many more zombie deaths until each can drop again. */
    /** Power-ups that dropped lately, with how many more zombie deaths they sit out. */
    private final Map<ResourceLocation, Integer> repeatCooldowns = new HashMap<>();
    private final WaveSchedule waves;
    /** The current round's wave event, if it is a special round. */
    private Optional<RoundRules> rules = Optional.empty();

    /** A power-up waiting for the next tick to be put in the world. */
    private record Drop(ResourceLocation powerUp, BlockPos pos) {}

    public Game(GameSettings settings, GameMap map, MobRoster roster, RandomSource random, GameEvents events) {
        this(settings, map, roster, random, events, SurvivalContent.NONE);
    }

    public Game(GameSettings settings, GameMap map, MobRoster roster, RandomSource random, GameEvents events, SurvivalContent content) {
        this.settings = settings;
        this.map = map;
        this.roster = roster;
        this.random = random;
        this.events = events;
        this.content = content;
        this.waves = new WaveSchedule(content.waveEvents(), settings.waveEvents(), random);
        // A map without a power switch is powered from the start.
        this.powerOn = map.map().features(dev.candycanecazoo.hellstorm.map.FeatureKind.POWER_SWITCH).isEmpty();
        this.unlocked = Set.copyOf(map.unlocked());
        this.discovered.addAll(map.startZones());
        this.dropIncrement = settings.powerUps().dropIncrement();
        this.nextDropAt = Math.round(dropIncrement);
    }

    /** Zones players can get to right now: reachable from a start zone without going through a closed door. */
    public Set<ZoneId> unlocked() {
        return unlocked;
    }

    /** The start zones and every zone an alive player has stood in this game. Only their markers spawn zombies. */
    public Set<ZoneId> discovered() {
        return java.util.Collections.unmodifiableSet(discovered);
    }

    public boolean doorOpen(String door) {
        return openDoors.contains(door);
    }

    /**
     * A door was bought and its blocks are gone. {@code graph} is the zone graph found in the world now, with the door's
     * opening walkable. Unlocks whatever became reachable and returns those zones. Doors stay open until the game ends.
     */
    public Set<ZoneId> openDoor(String door, Optional<UUID> by, dev.candycanecazoo.hellstorm.map.ZoneGraph graph) {
        if (!openDoors.add(door)) {
            return Set.of();
        }
        Set<ZoneId> next = new LinkedHashSet<>(unlocked);
        next.addAll(graph.reachableFrom(map.startZones()));
        Set<ZoneId> added = new LinkedHashSet<>(next);
        added.removeAll(unlocked);
        unlocked = Set.copyOf(next);
        events.emit(new GameEvent.DoorOpened(door, by, List.copyOf(added)));
        return added;
    }

    public boolean powerOn() {
        return powerOn;
    }

    /** Turns the power on for the rest of the game. Returns false if it already was. */
    public boolean turnPowerOn(Optional<UUID> by) {
        if (powerOn) {
            return false;
        }
        powerOn = true;
        events.emit(new GameEvent.PowerOn(by));
        return true;
    }

    /**
     * Charges a player for a purchase, or says why not. Only alive players in this game can buy, so nobody can before it
     * starts. Features check their own
     * rules (already owned, a full inventory) before calling this, and {@link #refund} if delivery then fails.
     */
    public Optional<Refusal> charge(UUID buyer, int cost, boolean needsPower) {
        PlayerState player = players.get(buyer);
        if (player != null && player.waiting()) {
            return Optional.of(new Refusal.NotStarted());
        }
        if (player == null || !player.alive() || phase instanceof GamePhase.GameOver) {
            return Optional.of(new Refusal.NotPlaying());
        }
        if (needsPower && !powerOn) {
            return Optional.of(new Refusal.NeedsPower());
        }
        if (Cheats.has(buyer, Cheats.Cheat.INFINITE_MONEY)) {
            return Optional.empty();
        }
        if (player.points() < cost) {
            return Optional.of(new Refusal.NotEnoughPoints(cost, player.points()));
        }
        player.spend(cost);
        return Optional.empty();
    }

    /**
     * Starts a team-wide timed effect (Insta-Kill, Double Points, Fire Sale), or restarts it if it's already running.
     * {@code name} is the lang key the HUD shows, beside {@code icon}. When it runs out the world hears {@link GameWorld#timedEffectEnded}.
     */
    public void startTimed(ResourceLocation effect, String name, dev.candycanecazoo.hellstorm.data.ItemSpec.Plain icon, int ticks) {
        timed.put(effect, new GameWorld.Timer(name, icon, ticks, ticks));
    }

    public boolean timedActive(ResourceLocation effect) {
        return timed.containsKey(effect);
    }

    private void tickTimed(GameWorld world) {
        List<ResourceLocation> ended = new ArrayList<>();
        timed.replaceAll((effect, timer) -> new GameWorld.Timer(timer.name(), timer.icon(), timer.ticksLeft() - 1, timer.totalTicks()));
        timed.forEach((effect, timer) -> {
            if (timer.ticksLeft() <= 0) {
                ended.add(effect);
            }
        });
        ended.forEach(effect -> {
            timed.remove(effect);
            world.timedEffectEnded(effect);
        });
    }

    public void refund(UUID buyer, int cost) {
        PlayerState player = players.get(buyer);
        if (player != null && !Cheats.has(buyer, Cheats.Cheat.INFINITE_MONEY)) {
            player.addPoints(cost);
        }
    }

    public GamePhase phase() {
        return phase;
    }

    public boolean finished() {
        return finished;
    }

    public GameSettings settings() {
        return settings;
    }

    public GameMap map() {
        return map;
    }

    public GameEvents events() {
        return events;
    }

    public Collection<PlayerState> players() {
        return players.values();
    }

    public Optional<PlayerState> player(UUID id) {
        return Optional.ofNullable(players.get(id));
    }

    public boolean hasMob(UUID mob) {
        return mobs.contains(mob);
    }

    public Set<UUID> mobs() {
        return Set.copyOf(mobs);
    }

    public boolean full() {
        return players.size() >= settings.players().maxPlayers();
    }

    /** Joining before the first round waits for the countdown to end; joining later spectates until the next round. */
    public boolean join(UUID player) {
        if (players.containsKey(player) || phase instanceof GamePhase.GameOver || full()) {
            return false;
        }
        boolean started = phase instanceof GamePhase.RoundActive || phase instanceof GamePhase.Intermission;
        players.put(player, new PlayerState(player, settings.economy().startingPoints(),
                started ? new LifeState.Spectating() : new LifeState.Waiting()));
        return true;
    }

    public void leave(UUID player) {
        players.remove(player);
        reviveHolds.remove(player);
        reviveHolds.values().removeIf(hold -> hold.reviver().equals(player));
    }

    /**
     * The player's connection dropped. While waiting for the start or after game over they simply leave: there's nothing of
     * theirs to keep, and the start can't put someone offline in play. Otherwise their place, points,
     * and perks wait {@code players.reconnect_grace_ticks} for them: someone down is out until the next round instead of
     * bleeding out unseen, and someone up still counts as fighting, so a dropped connection isn't a lost game.
     */
    public void disconnect(UUID id) {
        PlayerState player = players.get(id);
        if (player == null) {
            return;
        }
        if (player.waiting() || phase instanceof GamePhase.GameOver) {
            leave(id);
            return;
        }
        reviveHolds.remove(id);
        reviveHolds.values().removeIf(hold -> hold.reviver().equals(id));
        if (player.life() instanceof LifeState.Downed) {
            player.setLife(new LifeState.Spectating());
        }
        player.disconnect(ticks);
    }

    /** Back within the grace time: true if they were waiting, and they carry on as they left. */
    public boolean reconnect(UUID id) {
        PlayerState player = players.get(id);
        if (player == null || player.connected()) {
            return false;
        }
        player.reconnect();
        return true;
    }

    private void dropExpiredDisconnects() {
        int grace = settings.players().reconnectGraceTicks();
        List.copyOf(players.values()).stream().filter(p -> !p.connected() && ticks - p.disconnectedAt() >= grace)
                .forEach(p -> leave(p.id()));
    }

    /** Starts the countdown. Only from the lobby, with at least one player. */
    public boolean start() {
        if (!(phase instanceof GamePhase.Lobby) || players.isEmpty()) {
            return false;
        }
        phase = new GamePhase.Countdown(settings.rounds().countdownTicks());
        return true;
    }

    /** Ends the game now, as if everyone went down. */
    public void stop(GameWorld world) {
        if (!(phase instanceof GamePhase.GameOver)) {
            enterGameOver(world, 0);
        }
    }

    public void tick(GameWorld world) {
        ticks++;
        dropExpiredDisconnects();
        if (!(phase instanceof GamePhase.Lobby)) {
            discoverZones(world);
            phase = advance(world);
        }
        if (phase instanceof GamePhase.RoundActive || phase instanceof GamePhase.Intermission) {
            tickTimed(world);
            tickDowns(world);
            tickRegen(world);
        }
        tickRepairs(world);
        spawnDrops(world);
        if (ticks % STATUS_INTERVAL == 0) {
            players.keySet().forEach(id -> world.showStatus(id, status(id)));
        }
    }

    private GamePhase advance(GameWorld world) {
        if (phase instanceof GamePhase.Countdown countdown) {
            if (countdown.ticksLeft() > 0) {
                return new GamePhase.Countdown(countdown.ticksLeft() - 1);
            }
            // Everyone is waiting: anyone who joins later spectates, and a waiting player who drops leaves.
            for (PlayerState player : players.values()) {
                player.setLife(new LifeState.Alive());
                world.enterGame(player.id());
                world.respawn(player.id());
            }
            events.emit(new GameEvent.GameStart(List.copyOf(players.keySet())));
            return beginRound(world, 1);
        }
        if (phase instanceof GamePhase.GameOver over) {
            if (over.ticksLeft() > 0) {
                return new GamePhase.GameOver(over.roundReached(), over.ticksLeft() - 1);
            }
            finished = true;
            return over;
        }
        if (players.values().stream().noneMatch(Game::stillFighting)) {
            int reached = phase instanceof GamePhase.RoundActive active ? active.round()
                    : ((GamePhase.Intermission) phase).nextRound() - 1;
            return enterGameOver(world, reached);
        }
        if (phase instanceof GamePhase.Intermission intermission) {
            if (intermission.ticksLeft() > 0) {
                return new GamePhase.Intermission(intermission.nextRound(), intermission.ticksLeft() - 1);
            }
            return beginRound(world, intermission.nextRound());
        }
        return tickRound(world, (GamePhase.RoundActive) phase);
    }

    private GamePhase beginRound(GameWorld world, int round) {
        for (PlayerState player : players.values()) {
            player.resetRoundCounters();
            // Someone away keeps spectating: respawning them would put an empty body in play.
            if (player.life() instanceof LifeState.Spectating && player.connected()) {
                player.setLife(new LifeState.Alive());
                world.respawn(player.id());
                world.giveStartingLoadout(player.id());
            }
        }
        dropsThisRound = 0;
        rules = waves.forRound(round);
        rules.ifPresent(r -> {
            events.emit(new GameEvent.WaveEventStarted(r.event(), round));
            r.announce().ifPresent(world::announceKey);
            r.fog().ifPresent(density -> world.setFog(Optional.of(density)));
        });
        events.emit(new GameEvent.RoundStart(round));
        world.announce("Round " + round);
        int count = RoundPlan.zombieCount(settings.rounds(), round, players.size());
        return new GamePhase.RoundActive(round, rules.map(r -> Math.max(1, (int) Math.round(count * r.count()))).orElse(count), 0);
    }

    private GamePhase tickRound(GameWorld world, GamePhase.RoundActive round) {
        if (round.toSpawn() == 0 && mobs.isEmpty()) {
            endWave(world);
            events.emit(new GameEvent.RoundEnd(round.round()));
            world.announce("Round " + round.round() + " survived");
            return new GamePhase.Intermission(round.round() + 1, settings.rounds().intermissionTicks());
        }
        if (round.toSpawn() == 0 && mobs.size() == 1 && !mobs.contains(lastRunner)) {
            lastRunner = mobs.iterator().next();
            world.makeRunner(lastRunner);
        }
        if (round.spawnCooldown() > 0) {
            return new GamePhase.RoundActive(round.round(), round.toSpawn(), round.spawnCooldown() - 1);
        }
        if (round.toSpawn() == 0 || mobs.size() >= settings.rounds().maxAlive()) {
            return round;
        }
        Optional<BlockPos> marker = pickMarker(world);
        Optional<GameWorld.ZombieSpec> spec = marker.flatMap(m -> zombieSpec(round.round(), m, world));
        if (marker.isPresent() && spec.isEmpty() && warnedNoProfile != round.round()) {
            warnedNoProfile = round.round();
            Hellstorm.LOG.warn("Round {}: no mob profile can spawn (all weight 0, outside min_round/max_round, or broken); /hs map validate says which", round.round());
        }
        Optional<UUID> spawned = marker.flatMap(m -> spec.flatMap(s -> world.spawnZombie(m, s)));
        spawned.ifPresent(mob -> {
            mobs.add(mob);
            if (settings.rounds().runnerChance() > 0 && random.nextDouble() < settings.rounds().runnerChance()) {
                world.makeRunner(mob);
            }
            events.emit(new GameEvent.ZombieSpawned(mob, marker.get()));
        });
        // The spawn tick itself counts toward the delay, so a delay of N puts the next spawn N ticks later.
        int delay = RoundPlan.spawnDelayTicks(settings.rounds(), round.round());
        int cooldown = rules.map(r -> Math.max(1, (int) Math.round(delay / r.spawnRate()))).orElse(delay) - 1;
        return spawned.isPresent()
                ? new GamePhase.RoundActive(round.round(), round.toSpawn() - 1, cooldown)
                : new GamePhase.RoundActive(round.round(), round.toSpawn(), cooldown);
    }

    private Optional<GameWorld.ZombieSpec> zombieSpec(int round, BlockPos marker, GameWorld world) {
        GameSettings.Rounds r = settings.rounds();
        return pickProfile(round, marker, world).map(profile ->
                new GameWorld.ZombieSpec(profile, RoundPlan.zombieHealth(r, round), RoundPlan.zombieSpeed(r, round)));
    }

    /** A special round's own profiles, if it has any that fit this marker, else the roster's pick. */
    private Optional<ResourceLocation> pickProfile(int round, BlockPos marker, GameWorld world) {
        List<ResourceLocation> special = rules.map(RoundRules::profiles).orElse(List.of()).stream()
                .filter(profile -> world.canSpawn(profile, marker)).toList();
        if (!special.isEmpty()) {
            return Optional.of(special.get(random.nextInt(special.size())));
        }
        return roster.pick(round, random, profile -> world.canSpawn(profile, marker));
    }

    private void endWave(GameWorld world) {
        if (rules.flatMap(RoundRules::fog).isPresent()) {
            world.setFog(Optional.empty());
        }
        rules = Optional.empty();
    }

    /** The current round's wave event, if any. */
    public Optional<RoundRules> waveEvent() {
        return rules;
    }

    /** Players zombies may target: in this game and alive. */
    public boolean targetable(UUID player) {
        PlayerState state = players.get(player);
        return state != null && state.alive();
    }

    private void discoverZones(GameWorld world) {
        for (PlayerState player : players.values()) {
            if (player.alive()) {
                world.playerPosition(player.id()).flatMap(map.index()::zoneOfBody).ifPresent(discovered::add);
            }
        }
    }

    private Optional<BlockPos> pickMarker(GameWorld world) {
        List<BlockPos> positions = new ArrayList<>();
        for (PlayerState player : players.values()) {
            if (player.alive()) {
                world.playerPosition(player.id()).ifPresent(positions::add);
            }
        }
        Optional<BlockPos> picked = SpawnDirector.pick(
                SpawnDirector.candidates(markerZones(), discovered, positions, settings.spawning()), random);
        if (picked.isEmpty() && !warnedNoSpawns) {
            warnedNoSpawns = true;
            Hellstorm.LOG.warn("No spawn marker feeds a room the players have discovered; zombies can't spawn");
        }
        return picked;
    }

    private Map<BlockPos, ZoneId> markerZones() {
        Map<BlockPos, ZoneId> zones = new LinkedHashMap<>();
        map.spawnLinks().forEach((marker, link) -> zones.put(marker, link.zone()));
        return zones;
    }

    private GamePhase enterGameOver(GameWorld world, int roundReached) {
        List<UUID> remaining = List.copyOf(mobs);
        mobs.clear();
        List.copyOf(timed.keySet()).forEach(effect -> {
            timed.remove(effect);
            world.timedEffectEnded(effect);
        });
        remaining.forEach(world::removeMob);
        endWave(world);
        // Nobody is down once the game is lost: otherwise they crawl, glow, and see in grey through the game-over pause.
        for (PlayerState player : players.values()) {
            if (player.life() instanceof LifeState.Downed) {
                player.setLife(new LifeState.Alive());
                world.standUp(player.id());
            }
        }
        events.emit(new GameEvent.GameEnd(roundReached));
        world.announce("Game over. You survived " + roundReached + (roundReached == 1 ? " round" : " rounds"));
        phase = new GamePhase.GameOver(roundReached, settings.rounds().gameOverTicks());
        return phase;
    }

    /**
     * {@code player} is holding use on {@code window} this tick. Held for {@code windows.repair_ticks}, one board goes back
     * and the next is started, so holding on rebuilds board after board (see {@link #tickRepairs}). Turning to another
     * window, or letting go for longer than right click's repeat, starts the board over.
     */
    public void holdRepair(UUID player, String window) {
        if (!targetable(player) || phase instanceof GamePhase.GameOver) {
            return;
        }
        RepairHold current = repairHolds.get(player);
        boolean continuing = current != null && current.window().equals(window) && ticks - current.heard() <= HOLD_GRACE_TICKS;
        repairHolds.put(player, new RepairHold(window, continuing ? current.started() : ticks, ticks));
    }

    /** Puts a board back for every hold that has lasted a board's time, and drops holds let go of or with nothing left to fix. */
    private void tickRepairs(GameWorld world) {
        int perBoard = settings.windows().repairTicks();
        var holds = repairHolds.entrySet().iterator();
        while (holds.hasNext()) {
            Map.Entry<UUID, RepairHold> entry = holds.next();
            RepairHold hold = entry.getValue();
            if (ticks - hold.heard() > HOLD_GRACE_TICKS || !targetable(entry.getKey()) || world.boardsMissing(hold.window()) == 0) {
                holds.remove();
                continue;
            }
            if (ticks - hold.started() < perBoard) {
                continue;
            }
            world.addBoard(hold.window());
            if (onRepair(entry.getKey(), hold.window()) > 0) {
                world.chaChing(entry.getKey());
            }
            if (world.boardsMissing(hold.window()) == 0) {
                holds.remove();
            } else {
                entry.setValue(new RepairHold(hold.window(), hold.started() + perBoard, hold.heard()));
            }
        }
    }

    /**
     * A player put a board back on {@code window}. Pays until the player hits this round's repair cap, overall or at this
     * window, and returns what it paid.
     */
    public int onRepair(UUID repairer, String window) {
        PlayerState player = players.get(repairer);
        if (player == null || !player.alive()) {
            return 0;
        }
        int award = Economy.award(settings.economy(), new PointSource.Repair(), doublePoints());
        int paid = Economy.repairPay(settings.economy(), award, player.repairPointsThisRound(), player.repairPointsThisRound(window));
        if (paid > 0) {
            player.addRepairPoints(window, paid);
            earned += paid;
        }
        return paid;
    }

    /** A game zombie took non-lethal damage from a player. */
    public void onMobHit(UUID mob, UUID attacker) {
        PlayerState player = players.get(attacker);
        if (mobs.contains(mob) && player != null && player.inPlay()) {
            earn(player, Economy.award(settings.economy(), new PointSource.Hit(), doublePoints()));
        }
    }

    /** A game zombie died. {@code killer} is empty for deaths no player caused (traps, falling). */
    public void onMobDied(UUID mob, Optional<UUID> killer, boolean headshot, boolean melee, BlockPos pos) {
        if (!mobs.remove(mob)) {
            return;
        }
        Optional<PlayerState> player = killer.map(players::get).filter(PlayerState::inPlay);
        player.ifPresent(p -> {
            earn(p, Economy.award(settings.economy(), new PointSource.Kill(headshot, melee), doublePoints()));
            p.countKill(headshot);
        });
        events.emit(new GameEvent.ZombieKilled(mob, player.map(PlayerState::id), headshot, melee, pos));
        // Every zombie death counts the cooldowns down before its own drops.
        repeatCooldowns.replaceAll((powerUp, left) -> left - 1);
        repeatCooldowns.values().removeIf(left -> left <= 0);
        if (phase instanceof GamePhase.RoundActive round && round.toSpawn() == 0 && mobs.isEmpty()) {
            rules.flatMap(RoundRules::lastKillDrop).ifPresent(powerUp -> drop(powerUp, pos));
        }
        // Only kills a player makes drop power-ups: not traps, falls, or a Nuke.
        if (player.isPresent()) {
            boolean pointsDue = earned >= nextDropAt;
            PowerUpDrops.Table eligible = content.powerUps().without(repeatCooldowns.keySet());
            PowerUpDrops.roll(settings.powerUps(), dropsThisRound, pointsDue, eligible, random).ifPresent(powerUp -> {
                dropsThisRound++;
                if (pointsDue) {
                    // As CoD does: grow the increment, then measure the next threshold from here with it.
                    dropIncrement *= settings.powerUps().dropIncrementGrowth();
                    nextDropAt = earned + Math.round(dropIncrement);
                }
                drop(powerUp, pos);
            });
        }
    }

    /** Drops {@code powerUp} at {@code pos} next tick, and starts its repeat cooldown. */
    private void drop(ResourceLocation powerUp, BlockPos pos) {
        pendingDrops.add(new Drop(powerUp, pos));
        if (settings.powerUps().repeatCooldownKills() > 0) {
            // One more than the deaths it sits out: the count goes down at the start of each, and is gone at the first that can drop it.
            repeatCooldowns.put(powerUp, settings.powerUps().repeatCooldownKills() + 1);
        }
    }

    private boolean doublePoints() {
        return timedActive(TimedEffects.DOUBLE_POINTS);
    }

    /** Puts this tick's dropped power-ups in the world, a tick after the kills that dropped them. */
    private void spawnDrops(GameWorld world) {
        if (phase instanceof GamePhase.GameOver) {
            pendingDrops.clear();
            return;
        }
        for (Drop drop : pendingDrops) {
            world.dropPowerUp(drop.powerUp(), drop.pos());
            events.emit(new GameEvent.PowerUpSpawned(drop.powerUp(), drop.pos()));
        }
        pendingDrops.clear();
    }

    /** A player in this game touched a power-up. True if it counts: the player is alive and the game isn't over. */
    public boolean collectPowerUp(ResourceLocation powerUp, UUID collector) {
        PlayerState player = players.get(collector);
        if (player == null || !player.alive() || phase instanceof GamePhase.GameOver) {
            return false;
        }
        events.emit(new GameEvent.PowerUpCollected(powerUp, collector));
        return true;
    }

    /** Points for every player in the game, such as a Nuke's or a Carpenter's bonus. Never doubled. */
    public void awardEveryone(int points) {
        players.values().forEach(p -> earn(p, points));
    }

    /** Points a player earned, as opposed to a refund: they count toward the points-triggered power-up drops. */
    private void earn(PlayerState player, int points) {
        player.addPoints(points);
        earned += points;
    }

    /** A game zombie left the world without dying (unloaded, discarded). It is spawned again. */
    public void onMobLost(UUID mob) {
        if (mobs.remove(mob) && phase instanceof GamePhase.RoundActive round) {
            phase = new GamePhase.RoundActive(round.round(), round.toSpawn() + 1, round.spawnCooldown());
        }
    }

    /** A death that skipped going down (the void, /kill): the player spectates until the next round. */
    public void onPlayerDied(UUID id, GameWorld world) {
        PlayerState player = players.get(id);
        if (player != null && player.inPlay()) {
            reviveHolds.remove(id);
            player.setLife(new LifeState.Spectating());
            world.spectate(id);
        }
    }

    /**
     * Damage that would kill a player in this game. Returns true if the game takes it instead (the caller cancels the
     * damage): an alive player goes down, and a downed one takes no more. A solo player with Saint's Brew and self-revives
     * left starts reviving themselves at once.
     */
    /** Before the first round and once the game is over, nothing hurts the players: there's no fight to be hurt in. */
    public boolean protectsPlayers() {
        return phase instanceof GamePhase.Lobby || phase instanceof GamePhase.Countdown || phase instanceof GamePhase.GameOver;
    }

    public boolean onLethalDamage(UUID id, GameWorld world) {
        PlayerState player = players.get(id);
        if (player == null || !player.inPlay() || phase instanceof GamePhase.Lobby || phase instanceof GamePhase.GameOver) {
            return false;
        }
        if (!player.alive()) {
            return true;
        }
        boolean selfRevive = players.size() == 1 && world.canSelfRevive(id) &&player.selfRevivesUsed() < settings.downs().soloSelfRevives();
        if (selfRevive) {
            player.useSelfRevive();
        }
        player.setLife(new LifeState.Downed(settings.downs().bleedOutTicks(), Optional.empty(), 0, 0, selfRevive));
        world.down(id);
        events.emit(new GameEvent.PlayerDowned(id, world.playerPosition(id).orElse(BlockPos.ZERO)));
        return true;
    }

    /** Any damage to a player: it stops their regen for a while, and stops a revive they're doing. */
    public void onPlayerHurt(UUID id) {
        PlayerState player = players.get(id);
        if (player != null) {
            player.hurtAt(ticks);
            reviveHolds.values().removeIf(hold -> hold.reviver().equals(id));
        }
    }

    /** {@code reviver} is holding use on {@code downed} this tick. Ignored unless both are in this game and it makes sense. */
    public void holdRevive(UUID reviver, UUID downed) {
        PlayerState helper = players.get(reviver);
        PlayerState target = players.get(downed);
        if (helper == null || !helper.alive() || target == null || !(target.life() instanceof LifeState.Downed down) || down.selfReviving()) {
            return;
        }
        ReviveHold current = reviveHolds.get(downed);
        if (current == null || current.reviver().equals(reviver) || ticks - current.tick() > HOLD_GRACE_TICKS) {
            reviveHolds.put(downed, new ReviveHold(reviver, ticks));
        }
    }

    private static boolean stillFighting(PlayerState player) {
        return player.alive() || player.life() instanceof LifeState.Downed down && down.selfReviving();
    }

    private void tickDowns(GameWorld world) {
        for (PlayerState player : players.values()) {
            if (!(player.life() instanceof LifeState.Downed down)) {
                continue;
            }
            LifeState.Downed next;
            if (down.selfReviving()) {
                next = down.revivedBy(Optional.empty(), settings.downs().reviveTicks());
            } else {
                Optional<UUID> reviver = Optional.ofNullable(reviveHolds.get(player.id()))
                        .filter(hold -> ticks - hold.tick() <= HOLD_GRACE_TICKS)
                        .map(ReviveHold::reviver)
                        .filter(id -> players.get(id) != null && players.get(id).alive()
                                && world.distance(id, player.id()) <= settings.downs().reviveRange());
                if (reviver.isPresent() && down.reviver().filter(r -> !r.equals(reviver.get())).isEmpty()) {
                    next = down.revivedBy(reviver, reviveNeeded(world, reviver.get()));
                } else if (reviver.isPresent()) {
                    next = new LifeState.Downed(down.bleedTicksLeft(), reviver, 1, reviveNeeded(world, reviver.get()), false);
                } else {
                    reviveHolds.remove(player.id());
                    next = down.bleed();
                }
            }
            if (next.revived()) {
                reviveHolds.remove(player.id());
                player.setLife(new LifeState.Alive());
                player.hurtAt(ticks);
                world.revive(player.id());
                events.emit(new GameEvent.PlayerRevived(player.id(), next.reviver()));
            } else if (next.bleedTicksLeft() <= 0) {
                reviveHolds.remove(player.id());
                player.setLife(new LifeState.Spectating());
                world.spectate(player.id());
                events.emit(new GameEvent.PlayerBledOut(player.id()));
            } else {
                player.setLife(next);
            }
        }
    }

    /** {@code downs.revive_ticks}, divided by the reviver's revive speed (Saint's Brew doubles it). */
    private int reviveNeeded(GameWorld world, UUID reviver) {
        return Math.max(1, (int) Math.ceil(settings.downs().reviveTicks() / Math.max(0.01, world.reviveSpeed(reviver))));
    }

    private void tickRegen(GameWorld world) {
        float perTick = (float) (settings.downs().regenPerSecond() / 20.0);
        for (PlayerState player : players.values()) {
            if (player.alive() && perTick > 0 && ticks - player.lastHurtTick() >= settings.downs().regenDelayTicks()) {
                world.heal(player.id(), perTick);
            }
        }
    }

    /** Waiting players are listed as up: before the start the roster is everyone who's in, and nobody is out yet. */
    private List<GameWorld.Standing> standings() {
        return players.values().stream()
                .map(p -> new GameWorld.Standing(p.id(), p.points(), p.alive() || p.waiting(), p.life() instanceof LifeState.Downed)).toList();
    }

    /**
     * Downed players see how long they have or how far their revive is; a reviver sees their progress; a player rebuilding
     * a window sees the board going on, which is in place the moment the bar fills.
     */
    private Optional<GameWorld.Bar> bar(UUID id) {
        PlayerState player = players.get(id);
        if (player != null && player.life() instanceof LifeState.Downed down) {
            if (down.reviveTicks() > 0) {
                return Optional.of(new GameWorld.Bar(down.selfReviving() ? "Reviving" : "Being revived", progress(down), REVIVE_COLOR, perTick(down)));
            }
            int bleedOut = settings.downs().bleedOutTicks();
            return Optional.of(new GameWorld.Bar("Bleeding out", down.bleedTicksLeft() / (float) bleedOut, BLEED_COLOR, -1f / bleedOut));
        }
        int perBoard = settings.windows().repairTicks();
        return players.values().stream()
                .filter(p -> p.life() instanceof LifeState.Downed d && d.reviver().filter(id::equals).isPresent() && d.reviveTicks() > 0)
                .findFirst()
                .map(p -> new GameWorld.Bar("Reviving", progress((LifeState.Downed) p.life()), REVIVE_COLOR, perTick((LifeState.Downed) p.life())))
                .or(() -> Optional.ofNullable(repairHolds.get(id))
                        .map(hold -> new GameWorld.Bar("Rebuilding", (ticks - hold.started()) / (float) perBoard, REPAIR_COLOR, 1f / perBoard)));
    }

    private static float progress(LifeState.Downed down) {
        return down.reviveNeeded() == 0 ? 0 : down.reviveTicks() / (float) down.reviveNeeded();
    }

    private static float perTick(LifeState.Downed down) {
        return down.reviveNeeded() == 0 ? 0 : 1f / down.reviveNeeded();
    }

    public GameWorld.Status status(UUID id) {
        PlayerState player = players.get(id);
        int points = player == null ? 0 : player.points();
        if (phase instanceof GamePhase.Countdown c) {
            return status(id, 0, points, 0, "Starting in " + (c.ticksLeft() / 20 + 1));
        }
        if (phase instanceof GamePhase.RoundActive r) {
            return status(id, r.round(), points, r.toSpawn() + mobs.size(), "Round " + r.round());
        }
        if (phase instanceof GamePhase.Intermission i) {
            return status(id, i.nextRound() - 1, points, 0, "Round " + (i.nextRound() - 1));
        }
        if (phase instanceof GamePhase.GameOver o) {
            return status(id, o.roundReached(), points, 0, "Game over");
        }
        return status(id, 0, points, 0, "Waiting to start");
    }

    private GameWorld.Status status(UUID id, int round, int points, int zombiesLeft, String phase) {
        return new GameWorld.Status(round, points, zombiesLeft, phase, powerOn, standings(), List.copyOf(timed.values()), bar(id));
    }
}
