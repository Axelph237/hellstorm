package dev.candycanecazoo.hellstorm.perk;

import dev.candycanecazoo.hellstorm.client.PerkCanRenderer;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A can of one perk, drunk like a potion for the perk with no time limit, and drawn with its own GeckoLib model
 * ({@code geo/item/<id>.geo.json}, see {@link PerkCanRenderer}). Nothing is left once it's drunk. Its item color is the perk's, or the stack's
 * {@code CustomPotionColor} if it has one, so a vending machine selling it takes that color.
 */
public final class PerkCanItem extends Item implements GeoItem {
    private static final int DRINK_TICKS = 32;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String model;
    private final Supplier<MobEffect> perk;

    public PerkCanItem(Properties properties, String model, Supplier<MobEffect> perk) {
        super(properties);
        this.model = model;
        this.perk = perk;
    }

    /** The perk with no time limit and no particles, its icon still shown. */
    public MobEffectInstance effect() {
        return new MobEffectInstance(perk.get(), MobEffectInstance.INFINITE_DURATION, 0, false, false, true);
    }

    public int color(ItemStack stack) {
        if (stack.getTag() != null && stack.getTag().contains(PotionUtils.TAG_CUSTOM_POTION_COLOR, 99)) {
            return stack.getTag().getInt(PotionUtils.TAG_CUSTOM_POTION_COLOR);
        }
        return perk.get().getColor();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return DRINK_TICKS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        Player player = entity instanceof Player p ? p : null;
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
        }
        if (!level.isClientSide) {
            entity.addEffect(effect());
        }
        if (player != null) {
            player.awardStat(Stats.ITEM_USED.get(this));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        entity.gameEvent(GameEvent.DRINK);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        PotionUtils.addPotionTooltip(List.of(effect()), tooltip, 1.0f);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(PerkCanRenderer.extensions(model));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
