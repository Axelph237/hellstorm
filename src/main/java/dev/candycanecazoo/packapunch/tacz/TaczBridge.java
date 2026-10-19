package dev.candycanecazoo.packapunch.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The only place that touches TacZ. */
public final class TaczBridge {
    private TaczBridge() {}

    public record GunInfo(ResourceLocation gunId, String type, int magazineSize) {}

    public static List<ResourceLocation> allGuns() {
        return TimelessAPI.getAllCommonGunIndex().stream()
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    public static boolean gunExists(ResourceLocation gunId) {
        return TimelessAPI.getCommonGunIndex(gunId).isPresent();
    }

    /** A gun with a full magazine and its reserve stored as TacZ dummy ammo, so no ammo items are needed. */
    public static Optional<ItemStack> createGun(ResourceLocation gunId, int reserveMagazines) {
        return TimelessAPI.getCommonGunIndex(gunId).map(index -> {
            GunData data = index.getGunData();
            int magazine = data.getAmmoAmount();
            // Gun scripts (e.g. tacz:hk_mk23) pick reload timings by fire mode, so an unset mode can stall reloads.
            ItemStack stack = GunItemBuilder.create()
                    .setId(gunId)
                    .setFireMode(data.getFireModeSet().get(0))
                    .setAmmoCount(magazine)
                    .setAmmoInBarrel(true)
                    .build();
            IGun gun = IGun.getIGunOrNull(stack);
            int reserve = magazine * reserveMagazines;
            gun.setMaxDummyAmmoAmount(stack, reserve);
            gun.setDummyAmmoAmount(stack, reserve);
            return stack;
        });
    }

    public static Optional<GunInfo> inspect(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return Optional.empty();
        }
        ResourceLocation gunId = gun.getGunId(stack);
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> new GunInfo(gunId, index.getType(), magazineSize(stack, index)));
    }

    /** Fills the magazine and the dummy reserve. Returns false if the stack is not a known TacZ gun. */
    public static boolean refillAmmo(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return false;
        }
        Optional<CommonGunIndex> index = TimelessAPI.getCommonGunIndex(gun.getGunId(stack));
        if (index.isEmpty()) {
            return false;
        }
        gun.setCurrentAmmoCount(stack, magazineSize(stack, index.get()));
        gun.setDummyAmmoAmount(stack, gun.getMaxDummyAmmoAmount(stack));
        return true;
    }

    private static final String TAG = "packapunch";
    private static final String UNLIMITED_AMMO = "unlimited_ammo";

    /**
     * Gives the gun an endless reserve: the magazine still empties and needs reloading, but the reserve is topped up
     * every tick the gun is held (see {@link TaczEvents}). Stored on the stack, so it survives being dropped and picked up.
     */
    public static void setUnlimitedAmmo(ItemStack stack, boolean unlimited) {
        if (unlimited) {
            stack.getOrCreateTagElement(TAG).putBoolean(UNLIMITED_AMMO, true);
        } else if (stack.getTagElement(TAG) != null) {
            stack.getTagElement(TAG).remove(UNLIMITED_AMMO);
        }
    }

    public static boolean hasUnlimitedAmmo(ItemStack stack) {
        return stack.getTagElement(TAG) != null && stack.getTagElement(TAG).getBoolean(UNLIMITED_AMMO);
    }

    /** Fills an unlimited gun's reserve back to its maximum (at least one magazine). No-op for other stacks. */
    static void topUpUnlimitedReserve(ItemStack stack) {
        if (!hasUnlimitedAmmo(stack)) {
            return;
        }
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return;
        }
        int max = gun.getMaxDummyAmmoAmount(stack);
        if (max <= 0) {
            max = TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).map(index -> magazineSize(stack, index)).orElse(1);
            gun.setMaxDummyAmmoAmount(stack, max);
        }
        gun.setDummyAmmoAmount(stack, max);
    }

    private static int magazineSize(ItemStack stack, CommonGunIndex index) {
        return AttachmentDataUtils.getAmmoCountWithAttachment(stack, index.getGunData());
    }
}
