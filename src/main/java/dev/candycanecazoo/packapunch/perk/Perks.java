package dev.candycanecazoo.packapunch.perk;

import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.game.PlayerState;
import dev.candycanecazoo.packapunch.game.Refusal;
import dev.candycanecazoo.packapunch.game.Shop;
import dev.candycanecazoo.packapunch.net.PapNetwork;
import dev.candycanecazoo.packapunch.net.PerkSyncPacket;
import dev.candycanecazoo.packapunch.tacz.PerkGunEffects;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/** Buying perks, and putting their effects on a player and taking them off again. */
public final class Perks {
    private Perks() {}

    /** The perk rules checked before charging: one of each perk, and at most {@code limit} perks. */
    public static Optional<Refusal> refusal(Collection<ResourceLocation> owned, ResourceLocation perk, String name, int limit) {
        if (owned.contains(perk)) {
            return Optional.of(new Refusal.Because("You already have " + name));
        }
        if (owned.size() >= limit) {
            return Optional.of(new Refusal.Because("You can only hold " + limit + (limit == 1 ? " perk" : " perks")));
        }
        return Optional.empty();
    }

    /** Every effect of type {@code type} across {@code perks}, multiplied together. 1 when there are none. */
    public static <E extends PerkEffect> double product(Collection<PerkDef> perks, Class<E> type, ToDoubleFunction<E> factor) {
        double product = 1;
        for (PerkDef perk : perks) {
            for (PerkEffect effect : perk.effects()) {
                if (type.isInstance(effect)) {
                    product *= factor.applyAsDouble(type.cast(effect));
                }
            }
        }
        return product;
    }

    /** The perks a player holds in their game, or none outside a game. */
    public static Collection<PerkDef> held(ServerPlayer player) {
        return GameManager.forPlayer(player.getUUID()).flatMap(a -> a.game().player(player.getUUID()))
                .map(state -> state.perks().values()).orElse(List.of());
    }

    /** How long the player's reloads take, as a multiple of normal (Speed Cola's 0.5 is twice as fast). 1 outside a game. */
    public static double reloadMultiplier(ServerPlayer player) {
        return product(held(player), PerkEffect.ReloadSpeed.class, PerkEffect.ReloadSpeed::multiplier);
    }

    public static String displayName(PerkDef def) {
        return Language.getInstance().getOrDefault(def.name());
    }

    public static boolean buy(ServerPlayer player, GameManager.Active game, ResourceLocation id, PerkDef def, int cost) {
        return Shop.buy(player, game, new Shop.Sale() {
            @Override
            public int cost() {
                return cost;
            }

            @Override
            public boolean needsPower() {
                return def.requiresPower();
            }

            @Override
            public Optional<Refusal> check(GameManager.Active g, ServerPlayer p) {
                return g.game().player(p.getUUID()).flatMap(state ->
                        refusal(state.perks().keySet(), id, displayName(def), g.game().settings().players().perkLimit()));
            }

            @Override
            public Optional<Refusal> deliver(GameManager.Active g, ServerPlayer p) {
                PlayerState state = g.game().player(p.getUUID()).orElseThrow();
                apply(p, state, id, def);
                g.game().givePerk(p.getUUID(), id, def);
                PerkGunEffects.refresh(p);
                sync(p, state);
                return Optional.empty();
            }
        });
    }

    /** Takes every perk from the player and undoes their effects: on going down, dying, leaving, and when the game ends. */
    public static void removeAll(GameManager.Active game, ServerPlayer player) {
        Optional<PlayerState> state = game.game().player(player.getUUID());
        if (state.isEmpty()) {
            return;
        }
        Map<ResourceLocation, PerkDef> taken = game.game().takePerks(player.getUUID());
        taken.forEach((id, def) -> remove(player, state.get(), id, def));
        if (!taken.isEmpty()) {
            PerkGunEffects.refresh(player);
        }
        sync(player, state.get());
    }

    static void apply(ServerPlayer player, PlayerState state, ResourceLocation id, PerkDef def) {
        float maxHealth = player.getMaxHealth();
        for (int i = 0; i < def.effects().size(); i++) {
            PerkEffect effect = def.effects().get(i);
            if (effect instanceof PerkEffect.Attribute attribute) {
                AttributeInstance instance = instance(player, attribute);
                if (instance != null) {
                    instance.removeModifier(PerkDef.modifierId(id, i));
                    // Transient, so a modifier is never saved with the player if they disconnect mid-game.
                    instance.addTransientModifier(new AttributeModifier(PerkDef.modifierId(id, i), "packapunch perk " + id,
                            attribute.amount(), attribute.operation()));
                }
            } else if (effect instanceof PerkEffect.WeaponSlots slots) {
                state.setBonusWeaponSlots(state.bonusWeaponSlots() + slots.slots());
            }
        }
        if (player.getMaxHealth() > maxHealth) {
            player.heal(player.getMaxHealth() - maxHealth);
        }
    }

    static void remove(ServerPlayer player, PlayerState state, ResourceLocation id, PerkDef def) {
        for (int i = 0; i < def.effects().size(); i++) {
            PerkEffect effect = def.effects().get(i);
            if (effect instanceof PerkEffect.Attribute attribute) {
                AttributeInstance instance = instance(player, attribute);
                if (instance != null) {
                    instance.removeModifier(PerkDef.modifierId(id, i));
                }
            } else if (effect instanceof PerkEffect.WeaponSlots slots) {
                state.setBonusWeaponSlots(Math.max(0, state.bonusWeaponSlots() - slots.slots()));
            }
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static AttributeInstance instance(ServerPlayer player, PerkEffect.Attribute effect) {
        net.minecraft.world.entity.ai.attributes.Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(effect.attribute());
        return attribute == null ? null : player.getAttribute(attribute);
    }

    /** Tells the player's client which perks they hold (the HUD row) and their fire rate multiplier (TacZ fires client side). */
    public static void sync(ServerPlayer player, PlayerState state) {
        List<PerkSyncPacket.Badge> badges = state.perks().entrySet().stream()
                .map(e -> new PerkSyncPacket.Badge(e.getKey(), e.getValue().name(), e.getValue().color())).toList();
        float rpm = (float) product(state.perks().values(), PerkEffect.RpmMultiplier.class, PerkEffect.RpmMultiplier::multiplier);
        float reload = (float) product(state.perks().values(), PerkEffect.ReloadSpeed.class, PerkEffect.ReloadSpeed::multiplier);
        PapNetwork.send(player, new PerkSyncPacket(badges, rpm, reload));
    }
}
