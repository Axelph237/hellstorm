package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.perk.PerkDef;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private int bonusWeaponSlots;
    private int selfRevivesUsed;
    private long lastHurtTick;
    private final Map<ResourceLocation, PerkDef> perks = new LinkedHashMap<>();

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

    /** Extra TacZ gun slots on top of {@code players.weapon_slots}, from perks such as Mule Kick. */
    public int bonusWeaponSlots() {
        return bonusWeaponSlots;
    }

    public void setBonusWeaponSlots(int slots) {
        bonusWeaponSlots = slots;
    }

    /** Perks held, in the order bought, each as defined when bought, so a /reload mid-game can't change what removal undoes. */
    public Map<ResourceLocation, PerkDef> perks() {
        return Collections.unmodifiableMap(perks);
    }

    void addPerk(ResourceLocation id, PerkDef def) {
        perks.put(id, def);
    }

    Map<ResourceLocation, PerkDef> takePerks() {
        Map<ResourceLocation, PerkDef> taken = new LinkedHashMap<>(perks);
        perks.clear();
        return taken;
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
