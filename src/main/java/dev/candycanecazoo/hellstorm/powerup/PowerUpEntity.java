package dev.candycanecazoo.hellstorm.powerup;

import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.PlayerState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.UUID;

/**
 * A dropped power-up, floating where its zombie died. Any player of the level's game who touches it collects it (found
 * through the game, not the level's entity list). It
 * blinks for its last {@code blink} ticks and is gone after its lifetime, or as soon as the level has no game.
 */
public final class PowerUpEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(PowerUpEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> TICKS_LEFT = SynchedEntityData.defineId(PowerUpEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BLINK_TICKS = SynchedEntityData.defineId(PowerUpEntity.class, EntityDataSerializers.INT);
    private static final double REACH = 0.4;
    private static final String POWER_UP = "power_up";

    private ResourceLocation powerUp = PowerUpDef.DEFAULT_ITEM;

    public PowerUpEntity(EntityType<? extends PowerUpEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static PowerUpEntity create(ServerLevel level, ResourceLocation powerUp, PowerUpDef def, Vec3 pos, int lifetimeTicks, int blinkTicks) {
        PowerUpEntity entity = new PowerUpEntity(PowerUpRegistries.POWER_UP.get(), level);
        entity.powerUp = powerUp;
        entity.setPos(pos);
        entity.entityData.set(ITEM, new ItemStack(ForgeRegistries.ITEMS.getValue(def.item())));
        entity.entityData.set(TICKS_LEFT, lifetimeTicks);
        entity.entityData.set(BLINK_TICKS, blinkTicks);
        return entity;
    }

    public ResourceLocation powerUp() {
        return powerUp;
    }

    public ItemStack item() {
        return entityData.get(ITEM);
    }

    public int ticksLeft() {
        return entityData.get(TICKS_LEFT);
    }

    /** True on the blink's dark beats, in its last {@code blink} ticks. */
    public boolean blinkedOut() {
        int left = ticksLeft();
        return left < entityData.get(BLINK_TICKS) && (left / 4) % 2 == 0;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(ITEM, ItemStack.EMPTY);
        entityData.define(TICKS_LEFT, 0);
        entityData.define(BLINK_TICKS, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        int left = ticksLeft() - 1;
        entityData.set(TICKS_LEFT, left);
        Optional<GameManager.Active> game = GameManager.get(level);
        if (left <= 0 || game.isEmpty()) {
            discard();
            return;
        }
        AABB reach = getBoundingBox().inflate(REACH);
        for (UUID id : game.get().game().players().stream().map(PlayerState::id).toList()) {
            Optional<ServerPlayer> player = game.get().world().entity(id).filter(p -> p.level() == level && !p.isSpectator());
            if (player.isPresent() && player.get().getBoundingBox().intersects(reach) && PowerUps.collect(game.get(), player.get(), powerUp, position())) {
                discard();
                return;
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        Optional.ofNullable(ResourceLocation.tryParse(tag.getString(POWER_UP))).ifPresent(id -> powerUp = id);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString(POWER_UP, powerUp.toString());
    }
}
