package dev.candycanecazoo.hellstorm.tacz;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Where each bullet last struck an entity, noted as TacZ handles the hit, so its hurt events can tell where. */
public final class BulletHits {
    private static final Map<Entity, Vec3> LAST = new WeakHashMap<>();

    private BulletHits() {}

    public static void note(Entity bullet, Vec3 at) {
        LAST.put(bullet, at);
    }

    static Optional<Vec3> of(Entity bullet) {
        return Optional.ofNullable(LAST.get(bullet));
    }
}
