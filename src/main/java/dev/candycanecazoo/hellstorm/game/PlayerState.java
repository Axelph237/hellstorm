package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.map.ZoneId;

import java.util.Optional;
import java.util.UUID;

public final class PlayerState {
    private final UUID id;
    private LifeState life = new LifeState.Alive();
    private int points;
    private int kills;
    private int headshots;
    private Optional<ZoneId> lastZone = Optional.empty();
    private int repairPointsThisRound;
    private int selfRevivesUsed;
    /** The game tick they disconnected at, or -1 while connected. */
    private long disconnectedAt = -1;
    private long lastHurtTick;

    PlayerState(UUID id, int points) {
        this.id = id;
        this.points = points;
    }

    public UUID id() {
        return id;
    }

    public LifeState life() {
        return life;
    }

    public boolean alive() {
        return life instanceof LifeState.Alive;
    }

    /** Alive or down: still shooting, still earning points. */
    public boolean inPlay() {
        return !(life instanceof LifeState.Spectating);
    }

    public boolean connected() {
        return disconnectedAt < 0;
    }

    public long disconnectedAt() {
        return disconnectedAt;
    }

    void disconnect(long tick) {
        disconnectedAt = tick;
    }

    void reconnect() {
        disconnectedAt = -1;
    }

    public int selfRevivesUsed() {
        return selfRevivesUsed;
    }

    void useSelfRevive() {
        selfRevivesUsed++;
    }

    long lastHurtTick() {
        return lastHurtTick;
    }

    void hurtAt(long tick) {
        lastHurtTick = tick;
    }

    public int points() {
        return points;
    }

    public int kills() {
        return kills;
    }

    public int headshots() {
        return headshots;
    }

    public Optional<ZoneId> lastZone() {
        return lastZone;
    }

    void setLife(LifeState life) {
        this.life = life;
    }

    void addPoints(int amount) {
        points += amount;
    }

    void spend(int amount) {
        points -= amount;
    }

    void countKill(boolean headshot) {
        kills++;
        if (headshot) {
            headshots++;
        }
    }

    int repairPointsThisRound() {
        return repairPointsThisRound;
    }

    void addRepairPoints(int points) {
        repairPointsThisRound += points;
        this.points += points;
    }

    void resetRoundCounters() {
        repairPointsThisRound = 0;
    }

    void setLastZone(ZoneId zone) {
        lastZone = Optional.of(zone);
    }
}
