package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.powerup.PowerUpDrops;
import dev.candycanecazoo.packapunch.wave.RoundRules;
import dev.candycanecazoo.packapunch.wave.WaveSchedule;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final GameSettings settings;
    private final GameMap map;
    private final MobRoster roster;
    private final RandomSource random;
    private final GameEvents events;
    private final Map<UUID, PlayerState> players = new LinkedHashMap<>();
    private final Set<UUID> mobs = new LinkedHashSet<>();
    private GamePhase phase = new GamePhase.Lobby();
    private long ticks;
    private boolean warnedNoSpawns;
    private boolean finished;
    private boolean powerOn;
    private dev.candycanecazoo.packapunch.map.ZoneGraph graph;
    private Set<ZoneId> unlocked;
    private final Set<String> openDoors = new LinkedHashSet<>();
    private final Map<ResourceLocation, GameWorld.Timer> timed = new LinkedHashMap<>();
    /** Downed player to the teammate holding use on them and the last tick they did. */
    private final Map<UUID, ReviveHold> reviveHolds = new LinkedHashMap<>();

    /** A revive survives this many ticks between presses, so right click's repeat rate (every 4 ticks) counts as holding. */
    private static final int HOLD_GRACE_TICKS = 5;

    private record ReviveHold(UUID reviver, long tick) {}
    private final SurvivalContent content;
    private int dropsThisRound;
    private final List<Drop> pendingDrops = new ArrayList<>();
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
        this.powerOn = map.map().features(dev.candycanecazoo.packapunch.map.FeatureKind.POWER_SWITCH).isEmpty();
        this.graph = map.graph();
        this.unlocked = Set.copyOf(map.unlocked());
    }

    /** Zones players can get to right now: reachable from a start zone without going through a closed door. */
    public Set<ZoneId> unlocked() {
        return unlocked;
    }

    public boolean doorOpen(String door) {
        return openDoors.contains(door);
    }

    /**
     * A door was bought and its blocks are gone. {@code graph} is the zone graph found in the world now, with the door's
     * opening walkable. Unlocks whatever became reachable and returns those zones. Doors stay open until the game ends.
     */
    public Set<ZoneId> openDoor(String door, Optional<UUID> by, dev.candycanecazoo.packapunch.map.ZoneGraph graph) {
        if (!openDoors.add(door)) {
            return Set.of();
        }
        this.graph = graph;
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
     * Charges a player for a purchase, or says why not. Only alive players in this game can buy. Features check their own
     * rules (already owned, a full inventory) before calling this, and {@link #refund} if delivery then fails.
     */
    public Optional<Refusal> charge(UUID buyer, int cost, boolean needsPower) {
        PlayerState player = players.get(buyer);
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
     * {@code name} is the lang key the HUD shows. When it runs out the world hears {@link GameWorld#timedEffectEnded}.
     */
    public void startTimed(ResourceLocation effect, String name, int ticks) {
        timed.put(effect, new GameWorld.Timer(name, ticks, ticks));
    }

    public boolean timedActive(ResourceLocation effect) {
        return timed.containsKey(effect);
    }

    private void tickTimed(GameWorld world) {
        List<ResourceLocation> ended = new ArrayList<>();
        timed.replaceAll((effect, timer) -> new GameWorld.Timer(timer.name(), timer.ticksLeft() - 1, timer.totalTicks()));
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

    /** Joining before the first round starts plays immediately; joining later spectates until the next round. */
    public boolean join(UUID player) {
        if (players.containsKey(player) || phase instanceof GamePhase.GameOver) {
            return false;
        }
        PlayerState state = new PlayerState(player, settings.economy().startingPoints());
        if (phase instanceof GamePhase.RoundActive || phase instanceof GamePhase.Intermission) {
            state.setLife(new LifeState.Spectating());
        }
        players.put(player, state);
        return true;
    }

    public void leave(UUID player) {
        players.remove(player);
        reviveHolds.remove(player);
        reviveHolds.values().removeIf(hold -> hold.reviver().equals(player));
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
        if (!(phase instanceof GamePhase.Lobby)) {
            phase = advance(world);
        }
        if (phase instanceof GamePhase.RoundActive || phase instanceof GamePhase.Intermission) {
            tickTimed(world);
            tickDowns(world);
            tickRegen(world);
        }
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
            players.keySet().forEach(world::respawn);
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
            if (player.life() instanceof LifeState.Spectating) {
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
        updatePlayerZones(world);
        if (round.toSpawn() == 0 && mobs.isEmpty()) {
            endWave(world);
            events.emit(new GameEvent.RoundEnd(round.round()));
            world.announce("Round " + round.round() + " survived");
            return new GamePhase.Intermission(round.round() + 1, settings.rounds().intermissionTicks());
        }
        if (round.spawnCooldown() > 0) {
            return new GamePhase.RoundActive(round.round(), round.toSpawn(), round.spawnCooldown() - 1);
        }
        if (round.toSpawn() == 0 || mobs.size() >= settings.rounds().maxAlive()) {
            return round;
        }
        Optional<BlockPos> marker = pickMarker(world);
        Optional<UUID> spawned = marker.flatMap(m -> zombieSpec(round.round(), m, world).flatMap(spec -> world.spawnZombie(m, spec)));
        spawned.ifPresent(mob -> {
            mobs.add(mob);
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
        boolean sprints = random.nextDouble() < RoundPlan.sprintChance(r, round);
        return pickProfile(round, marker, world).map(profile ->
                new GameWorld.ZombieSpec(profile, RoundPlan.zombieHealth(r, round), RoundPlan.zombieSpeed(r, round, sprints)));
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

    private void updatePlayerZones(GameWorld world) {
        for (PlayerState player : players.values()) {
            if (player.alive()) {
                world.playerPosition(player.id()).flatMap(map.index()::zoneAt).ifPresent(player::setLastZone);
            }
        }
    }

    private Optional<BlockPos> pickMarker(GameWorld world) {
        List<ZoneId> playerZones = new ArrayList<>();
        List<BlockPos> positions = new ArrayList<>();
        for (PlayerState player : players.values()) {
            if (player.alive()) {
                playerZones.add(player.lastZone().orElse(map.startZones().isEmpty() ? null : map.startZones().get(0)));
                world.playerPosition(player.id()).ifPresent(positions::add);
            }
        }
        playerZones.removeIf(Objects::isNull);
        Set<ZoneId> active = SpawnDirector.activeZones(playerZones, graph, unlocked, settings.spawning().adjacentHops());
        Optional<BlockPos> picked = SpawnDirector.pick(
                SpawnDirector.candidates(markerZones(), active, positions, settings.spawning()), random);
        if (picked.isPresent()) {
            return picked;
        }
        List<BlockPos> fallback = map.startZoneMarkers();
        if (fallback.isEmpty()) {
            if (!warnedNoSpawns) {
                warnedNoSpawns = true;
                PackAPunch.LOG.warn("No usable spawn markers near the players or in a start zone; zombies can't spawn");
            }
            return Optional.empty();
        }
        return Optional.of(fallback.get(random.nextInt(fallback.size())));
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
        events.emit(new GameEvent.GameEnd(roundReached));
        world.announce("Game over. You survived " + roundReached + (roundReached == 1 ? " round" : " rounds"));
        phase = new GamePhase.GameOver(roundReached, settings.rounds().gameOverTicks());
        return phase;
    }

    /** A player put a window board back. Pays until the player hits this round's repair cap. */
    public void onRepair(UUID repairer) {
        PlayerState player = players.get(repairer);
        if (player == null || !player.alive()) {
            return;
        }
        int award = Economy.award(settings.economy(), new PointSource.Repair(), doublePoints());
        int room = settings.economy().repairCapPerRound() - player.repairPointsThisRound();
        if (room > 0) {
            player.addRepairPoints(Math.min(award, room));
        }
    }

    /** A game zombie took non-lethal damage from a player. */
    public void onMobHit(UUID mob, UUID attacker) {
        PlayerState player = players.get(attacker);
        if (mobs.contains(mob) && player != null && player.inPlay()) {
            player.addPoints(Economy.award(settings.economy(), new PointSource.Hit(), doublePoints()));
        }
    }

    /** A game zombie died. {@code killer} is empty for deaths no player caused (traps, falling). */
    public void onMobDied(UUID mob, Optional<UUID> killer, boolean headshot, boolean melee, BlockPos pos) {
        if (!mobs.remove(mob)) {
            return;
        }
        Optional<PlayerState> player = killer.map(players::get).filter(PlayerState::inPlay);
        player.ifPresent(p -> {
            p.addPoints(Economy.award(settings.economy(), new PointSource.Kill(headshot, melee), doublePoints()));
            p.countKill(headshot);
        });
        events.emit(new GameEvent.ZombieKilled(mob, player.map(PlayerState::id), headshot, melee, pos));
        if (phase instanceof GamePhase.RoundActive round && round.toSpawn() == 0 && mobs.isEmpty()) {
            rules.flatMap(RoundRules::lastKillDrop).ifPresent(powerUp -> pendingDrops.add(new Drop(powerUp, pos)));
        }
        // Only kills a player makes drop power-ups: not traps, falls, or a Nuke.
        if (player.isPresent()) {
            PowerUpDrops.roll(settings.powerUps(), dropsThisRound, content.powerUps(), random).ifPresent(powerUp -> {
                dropsThisRound++;
                pendingDrops.add(new Drop(powerUp, pos));
            });
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
        players.values().forEach(p -> p.addPoints(points));
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
     * damage): an alive player goes down, and a downed one takes no more. A solo player with Quick Revive and self-revives
     * left starts reviving themselves at once.
     */
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

    /** {@code downs.revive_ticks}, divided by the reviver's revive speed (Quick Revive doubles it). */
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

    private List<GameWorld.Standing> standings() {
        return players.values().stream().map(p -> new GameWorld.Standing(p.id(), p.points(), p.alive(), p.life() instanceof LifeState.Downed)).toList();
    }

    /** Downed players see how long they have or how far their revive is; a reviver sees their progress. */
    private Optional<GameWorld.Bar> bar(UUID id) {
        PlayerState player = players.get(id);
        if (player != null && player.life() instanceof LifeState.Downed down) {
            if (down.reviveTicks() > 0) {
                return Optional.of(new GameWorld.Bar(down.selfReviving() ? "Reviving" : "Being revived", progress(down), REVIVE_COLOR));
            }
            return Optional.of(new GameWorld.Bar("Bleeding out", down.bleedTicksLeft() / (float) settings.downs().bleedOutTicks(), BLEED_COLOR));
        }
        return players.values().stream()
                .filter(p -> p.life() instanceof LifeState.Downed d && d.reviver().filter(id::equals).isPresent() && d.reviveTicks() > 0)
                .findFirst()
                .map(p -> new GameWorld.Bar("Reviving", progress((LifeState.Downed) p.life()), REVIVE_COLOR));
    }

    private static float progress(LifeState.Downed down) {
        return down.reviveNeeded() == 0 ? 0 : down.reviveTicks() / (float) down.reviveNeeded();
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
