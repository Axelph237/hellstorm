package dev.candycanecazoo.hellstorm.tacz;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.AttachmentPropertyEvent;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.util.AttachmentDataUtils;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.pap.PunchResolver;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The upgrader's default TacZ upgrade. The stat multipliers live on the stack ({@code pap.damage_multiplier} and so on, one
 * factor per tier multiplied in), so they work on both sides and outside a game. Each hook multiplies what TacZ and other
 * hooks (perks) already set, rather than overwriting it.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class PapGunStats {
    public static final String DAMAGE = "damage_multiplier";
    public static final String MAGAZINE = "magazine_multiplier";
    public static final String RPM = "rpm_multiplier";

    private PapGunStats() {}

    /** A copy one tier up: stats multiplied once more, a bigger reserve, full ammo, and "Upgraded" in its name. */
    public static ItemStack upgrade(ItemStack gun, GameSettings.Pap settings) {
        ItemStack stack = gun.copy();
        IGun iGun = IGun.getIGunOrNull(stack);
        CompoundTag pap = stack.getOrCreateTagElement(ItemSpec.PAP);
        int tier = pap.getInt(PunchResolver.TIER) + 1;
        pap.putInt(PunchResolver.TIER, tier);
        pap.putDouble(DAMAGE, multiplier(pap, DAMAGE) * settings.damageMultiplier());
        pap.putDouble(MAGAZINE, multiplier(pap, MAGAZINE) * settings.magazineMultiplier());
        pap.putDouble(RPM, multiplier(pap, RPM) * settings.rpmMultiplier());
        if (tier >= settings.maxTier()) {
            pap.remove(ItemSpec.PUNCHABLE);
        }
        if (iGun != null) {
            iGun.setMaxDummyAmmoAmount(stack, (int) Math.round(iGun.getMaxDummyAmmoAmount(stack) * settings.magazineMultiplier()));
        }
        TaczBridge.refillAmmo(stack);
        stack.setHoverName(Component.translatable("item.hellstorm.upgraded", baseName(stack)));
        return stack;
    }

    /** Gives {@code to} the attachments and/or ammo of {@code from}, when both are TacZ guns. */
    public static void carryOver(ItemStack from, ItemStack to, boolean ammo, boolean attachments) {
        IGun fromGun = IGun.getIGunOrNull(from);
        IGun toGun = IGun.getIGunOrNull(to);
        if (fromGun == null || toGun == null) {
            return;
        }
        if (attachments) {
            for (AttachmentType type : AttachmentType.values()) {
                ItemStack attachment = type == AttachmentType.NONE ? ItemStack.EMPTY : fromGun.getAttachment(from, type);
                if (!attachment.isEmpty()) {
                    toGun.installAttachment(to, attachment);
                }
            }
        }
        if (ammo) {
            int magazine = TaczBridge.inspect(to).map(TaczBridge.GunInfo::magazineSize).orElse(0);
            toGun.setCurrentAmmoCount(to, Math.min(fromGun.getCurrentAmmoCount(from), magazine));
            toGun.setDummyAmmoAmount(to, Math.min(fromGun.getDummyAmmoAmount(from), toGun.getMaxDummyAmmoAmount(to)));
        }
    }

    /** The gun's own name, ignoring any custom name, so a second upgrade isn't "Upgraded Upgraded". */
    private static Component baseName(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun == null ? stack.getItem().getName(stack) : TimelessAPI.getCommonGunIndex(gun.getGunId(stack))
                .<Component>map(index -> Component.translatable(index.getPojo().getName()))
                .orElse(stack.getItem().getName(stack));
    }

    private static double multiplier(CompoundTag pap, String key) {
        return pap.contains(key, Tag.TAG_ANY_NUMERIC) ? pap.getDouble(key) : 1.0;
    }

    public static double multiplier(ItemStack stack, String key) {
        CompoundTag pap = stack.getTagElement(ItemSpec.PAP);
        return pap == null ? 1.0 : multiplier(pap, key);
    }

    /** Called from {@link AttachmentDataUtils#getAmmoCountWithAttachment} (see the mixin): TacZ has no event for capacity. */
    public static int magazineSize(ItemStack stack, int base) {
        double multiplier = multiplier(stack, MAGAZINE);
        return multiplier == 1.0 ? base : Math.max(1, (int) Math.round(base * multiplier));
    }

    @SubscribeEvent
    public static void onProperties(AttachmentPropertyEvent event) {
        double multiplier = multiplier(event.getGunItem(), RPM);
        if (multiplier != 1.0) {
            AttachmentCacheProperty cache = event.getCacheProperty();
            int rpm = cache.getCache(GunProperties.ROUNDS_PER_MINUTE);
            cache.setCache(GunProperties.ROUNDS_PER_MINUTE, Math.max(1, (int) Math.round(rpm * multiplier)));
        }
    }

    /**
     * Not limited to the server: each side's event carries its own amount, so nothing is counted twice. The event names the
     * gun but not the stack, so the held gun counts only if it is the same gun.
     */
    @SubscribeEvent
    public static void onGunHit(EntityHurtByGunEvent.Pre event) {
        LivingEntity attacker = event.getAttacker();
        if (attacker == null) {
            return;
        }
        ItemStack held = attacker.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(held);
        if (gun != null && gun.getGunId(held).equals(event.getGunId())) {
            double multiplier = multiplier(held, DAMAGE);
            if (multiplier != 1.0) {
                event.setBaseAmount((float) (event.getBaseAmount() * multiplier));
            }
        }
    }
}
