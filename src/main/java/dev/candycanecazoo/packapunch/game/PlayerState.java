package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.map.ZoneId;

import java.util.Optional;
import java.util.UUID;

public final class PlayerState {
    private final UUID id;
    private LifeState life = new LifeState.Alive();
    private int points;
    private int kills;
    private int headshots;
    private Optional<ZoneId> lastZone = Optional.empty();

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

    void countKill(boolean headshot) {
        kills++;
        if (headshot) {
            headshots++;
        }
    }

    void setLastZone(ZoneId zone) {
        lastZone = Optional.of(zone);
    }
}
