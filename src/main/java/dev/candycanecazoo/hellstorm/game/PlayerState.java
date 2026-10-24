package dev.candycanecazoo.hellstorm.game;

import java.util.UUID;

public final class PlayerState {
    private final UUID id;
    private LifeState life;
    private int points;
    private int kills;
    private int headshots;
    private int repairPointsThisRound;
    private int selfRevivesUsed;
    /** The game tick they disconnected at, or -1 while connected. */
    private long disconnectedAt = -1;
    private long lastHurtTick;

    PlayerState(UUID id, int points, LifeState life) {
        this.id = id;
        this.points = points;
        this.life = life;
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
        return life instanceof LifeState.Alive || life instanceof LifeState.Downed;
    }

    /** In the game's lobby or countdown, not yet playing. See {@link LifeState.Waiting}. */
    public boolean waiting() {
        return life instanceof LifeState.Waiting;
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
}
