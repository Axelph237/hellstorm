package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.perk.PerkDef;
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

    public Game(GameSettings settings, GameMap map, MobRoster roster, RandomSource random, GameEvents events) {
        this.settings = settings;
        this.map = map;
        this.roster = roster;
        this.random = random;
        this.events = events;
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

    /** Records a bought perk and tells scripts. Applying its effects to the player is up to the caller. */
    public void givePerk(UUID buyer, ResourceLocation perk, PerkDef def) {
        PlayerState player = players.get(buyer);
        if (player != null) {
            player.addPerk(perk, def);
            events.emit(new GameEvent.PerkBought(buyer, perk));
        }
    }

    /** Takes every perk from a player and returns them, so the caller can undo their effects. */
    public Map<ResourceLocation, PerkDef> takePerks(UUID id) {
        PlayerState player = players.get(id);
        return player == null ? Map.of() : player.takePerks();
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
        if (players.values().stream().noneMatch(PlayerState::alive)) {
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
            if (!player.alive()) {
                player.setLife(new LifeState.Alive());
                world.respawn(player.id());
            }
        }
        events.emit(new GameEvent.RoundStart(round));
        world.announce("Round " + round);
        return new GamePhase.RoundActive(round, RoundPlan.zombieCount(settings.rounds(), round, players.size()), 0);
    }

    private GamePhase tickRound(GameWorld world, GamePhase.RoundActive round) {
        updatePlayerZones(world);
        if (round.toSpawn() == 0 && mobs.isEmpty()) {
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
        int cooldown = RoundPlan.spawnDelayTicks(settings.rounds(), round.round()) - 1;
        return spawned.isPresent()
                ? new GamePhase.RoundActive(round.round(), round.toSpawn() - 1, cooldown)
                : new GamePhase.RoundActive(round.round(), round.toSpawn(), cooldown);
    }

    private Optional<GameWorld.ZombieSpec> zombieSpec(int round, BlockPos marker, GameWorld world) {
        GameSettings.Rounds r = settings.rounds();
        boolean sprints = random.nextDouble() < RoundPlan.sprintChance(r, round);
        return roster.pick(round, random, profile -> world.canSpawn(profile, marker)).map(profile ->
                new GameWorld.ZombieSpec(profile, RoundPlan.zombieHealth(r, round), RoundPlan.zombieSpeed(r, round, sprints)));
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
        remaining.forEach(world::removeMob);
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
        int award = Economy.award(settings.economy(), new PointSource.Repair(), false);
        int room = settings.economy().repairCapPerRound() - player.repairPointsThisRound();
        if (room > 0) {
            player.addRepairPoints(Math.min(award, room));
        }
    }

    /** A game zombie took non-lethal damage from a player. */
    public void onMobHit(UUID mob, UUID attacker) {
        PlayerState player = players.get(attacker);
        if (mobs.contains(mob) && player != null && player.alive()) {
            player.addPoints(Economy.award(settings.economy(), new PointSource.Hit(), false));
        }
    }

    /** A game zombie died. {@code killer} is empty for deaths no player caused (traps, falling). */
    public void onMobDied(UUID mob, Optional<UUID> killer, boolean headshot, boolean melee, BlockPos pos) {
        if (!mobs.remove(mob)) {
            return;
        }
        Optional<PlayerState> player = killer.map(players::get).filter(PlayerState::alive);
        player.ifPresent(p -> {
            p.addPoints(Economy.award(settings.economy(), new PointSource.Kill(headshot, melee), false));
            p.countKill(headshot);
        });
        events.emit(new GameEvent.ZombieKilled(mob, player.map(PlayerState::id), headshot, melee, pos));
    }

    /** A game zombie left the world without dying (unloaded, discarded). It is spawned again. */
    public void onMobLost(UUID mob) {
        if (mobs.remove(mob) && phase instanceof GamePhase.RoundActive round) {
            phase = new GamePhase.RoundActive(round.round(), round.toSpawn() + 1, round.spawnCooldown());
        }
    }

    public void onPlayerDied(UUID id, GameWorld world) {
        PlayerState player = players.get(id);
        if (player != null && player.alive()) {
            player.setLife(new LifeState.Spectating());
            world.spectate(id);
        }
    }

    private List<GameWorld.Standing> standings() {
        return players.values().stream().map(p -> new GameWorld.Standing(p.id(), p.points(), p.alive())).toList();
    }

    public GameWorld.Status status(UUID id) {
        PlayerState player = players.get(id);
        int points = player == null ? 0 : player.points();
        if (phase instanceof GamePhase.Countdown c) {
            return new GameWorld.Status(0, points, 0, "Starting in " + (c.ticksLeft() / 20 + 1), powerOn, standings());
        }
        if (phase instanceof GamePhase.RoundActive r) {
            return new GameWorld.Status(r.round(), points, r.toSpawn() + mobs.size(), "Round " + r.round(), powerOn, standings());
        }
        if (phase instanceof GamePhase.Intermission i) {
            return new GameWorld.Status(i.nextRound() - 1, points, 0, "Round " + (i.nextRound() - 1), powerOn, standings());
        }
        if (phase instanceof GamePhase.GameOver o) {
            return new GameWorld.Status(o.roundReached(), points, 0, "Game over", powerOn, standings());
        }
        return new GameWorld.Status(0, points, 0, "Waiting to start", powerOn, standings());
    }
}
