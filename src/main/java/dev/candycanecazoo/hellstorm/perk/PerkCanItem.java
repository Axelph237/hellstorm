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
 * A can of one perk, drunk like a potion for the perk with no time limit, and drawn with the GeckoLib model named after its
 * item id ({@code geo/item/<id>.geo.json}, see {@link PerkCanRenderer}). Nothing is left once it's drunk. Its item color is the perk's, or the stack's
 * {@code CustomPotionColor} if it has one, so a vending machine selling it takes that color.
 */
public final class PerkCanItem extends Item implements GeoItem {
    private static final int DRINK_TICKS = 32;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<MobEffect> perk;

    public PerkCanItem(Properties properties, Supplier<MobEffect> perk) {
        super(properties);
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

    /**
     * What the can's perk does, from the lang key {@code item.<ns>.<id>.lore}, in lore style. The text can name the perk's
     * boost as a share of what the player has without it ({@code %s}): Load-Up's +1 reload speed on a base of 1 is "100%".
     */
    public Component lore() {
        return Component.translatable(getDescriptionId() + ".lore", boost()).withStyle(dev.candycanecazoo.hellstorm.util.ItemLore.STYLE);
    }

    private String boost() {
        return perk.get().getAttributeModifiers().entrySet().stream().findFirst().map(e -> {
            double base = e.getKey().getDefaultValue();
            double amount = e.getValue().getAmount();
            double share = e.getValue().getOperation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION && base != 0 ? amount / base : amount;
            return Math.round(share * 100) + "%";
        }).orElse("");
    }

    /** Its own blurb, unless a {@code Lore} tag says something else (vanilla shows that one). */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (dev.candycanecazoo.hellstorm.util.ItemLore.tagged(stack).isEmpty()) {
            tooltip.add(lore());
        }
        PotionUtils.addPotionTooltip(List.of(effect()), tooltip, 1.0f);
    }

    /** Called from {@link Item}'s constructor, before this class's fields are set, so the renderer must not take them now. */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(PerkCanRenderer.extensions(this));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
