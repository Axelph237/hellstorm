package dev.candycanecazoo.hellstorm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * The downed teammate under the crosshair, within reach. Unlike vanilla's crosshair target, only blocks you'd bump into
 * stand in the way: grass, flowers, and torches have an outline but nothing to collide with, so a teammate lying in them
 * can still be revived.
 */
final class ReviveTarget {
    private ReviveTarget() {}

    static Optional<Entity> find() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        if (self == null || mc.level == null) {
            return Optional.empty();
        }
        Vec3 eye = self.getEyePosition(1.0f);
        Vec3 look = self.getViewVector(1.0f);
        double reach = self.getEntityReach();
        Vec3 end = eye.add(look.scale(reach));
        BlockHitResult wall = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self));
        if (wall.getType() != HitResult.Type.MISS) {
            end = wall.getLocation();
        }
        AABB area = self.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(self, eye, end, area,
                entity -> entity instanceof Player && entity != self && HudState.downed(entity), eye.distanceToSqr(end));
        return Optional.ofNullable(hit).map(EntityHitResult::getEntity);
    }
}
