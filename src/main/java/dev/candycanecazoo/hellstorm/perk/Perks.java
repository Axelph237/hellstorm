package dev.candycanecazoo.hellstorm.perk;

import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.Refusal;
import dev.candycanecazoo.hellstorm.game.Shop;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Perks are mob effects in the {@code hellstorm:perks} tag, usually drunk from a potion bought at a vending machine. A player
 * in a game holds at most {@code players.perk_limit} of them, and loses every beneficial effect on going down.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class Perks {
    private Perks() {}

    public static boolean is(MobEffect effect, TagKey<MobEffect> tag) {
        return ForgeRegistries.MOB_EFFECTS.getHolder(effect).map(holder -> holder.is(tag)).orElse(false);
    }

    public static boolean hasAny(LivingEntity entity, TagKey<MobEffect> tag) {
        return entity.getActiveEffects().stream().anyMatch(instance -> is(instance.getEffect(), tag));
    }

    /** The perks the entity has active. */
    public static List<MobEffect> held(LivingEntity entity) {
        return entity.getActiveEffects().stream().map(MobEffectInstance::getEffect).filter(effect -> is(effect, PerkEffects.PERKS)).toList();
    }

    /** The perks drinking {@code stack} would give. None for anything that isn't a potion. */
    public static List<MobEffect> given(ItemStack stack) {
        return PotionUtils.getMobEffects(stack).stream().map(MobEffectInstance::getEffect).filter(effect -> is(effect, PerkEffects.PERKS)).distinct().toList();
    }

    /** Refuses gaining perks from {@code adding} that aren't {@code held} if that would take the player past {@code limit}. */
    public static <P> Optional<Refusal> limitRefusal(Collection<P> held, Collection<P> adding, int limit) {
        long gained = adding.stream().distinct().filter(perk -> !held.contains(perk)).count();
        if (gained == 0 || held.size() + gained <= limit) {
            return Optional.empty();
        }
        return Optional.of(new Refusal.Because("You can only hold " + limit + (limit == 1 ? " perk" : " perks")));
    }

    /** {@link #limitRefusal} for drinking {@code stack}, at the limit of the player's game. Never refused outside a game or before it starts. */
    public static Optional<Refusal> limitRefusal(ServerPlayer player, ItemStack stack) {
        return GameManager.playing(player.getUUID())
                .flatMap(active -> limitRefusal(held(player), given(stack), active.game().settings().players().perkLimit()));
    }

    /** "You already have Vigorade" when {@code stack} is a potion and every one of its effects is already active. */
    public static Optional<String> alreadyHas(LivingEntity entity, ItemStack stack) {
        List<MobEffectInstance> effects = PotionUtils.getMobEffects(stack);
        if (effects.isEmpty() || !effects.stream().allMatch(instance -> entity.hasEffect(instance.getEffect()))) {
            return Optional.empty();
        }
        return Optional.of("You already have " + effects.get(0).getEffect().getDisplayName().getString());
    }

    /** Takes every beneficial effect off the player: on going down, dying, leaving, and when the game ends. */
    public static void clearBuffs(LivingEntity entity) {
        for (MobEffect effect : List.copyOf(entity.getActiveEffectsMap().keySet())) {
            if (effect.getCategory() == MobEffectCategory.BENEFICIAL) {
                entity.removeEffect(effect);
            }
        }
    }

    /** Refused before anything is drunk. The client keeps its drinking pose until the player lets go of use. */
    @SubscribeEvent
    public static void onStartUsing(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            limitRefusal(player, event.getItem()).ifPresent(refusal -> {
                event.setCanceled(true);
                Shop.refuse(player, refusal);
            });
        }
    }
}
