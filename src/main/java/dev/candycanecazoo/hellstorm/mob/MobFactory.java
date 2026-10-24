package dev.candycanecazoo.hellstorm.mob;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.candycanecazoo.hellstorm.Hellstorm;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/** Creates the entity a profile names, applying its NBT and scale. Profiles that name a missing or non-mob entity are logged and skipped. */
public final class MobFactory {
    private MobFactory() {}

    public static Optional<Mob> create(ServerLevel level, MobProfile profile, ResourceLocation profileId) {
        Built built = build(level, profile);
        built.error().ifPresent(error -> Hellstorm.LOG.error("Mob profile {} {}", profileId, error));
        return built.mob();
    }

    /** Why {@code profile} can't make a mob, or empty if it can. The mob it makes to find out is thrown away. */
    public static Optional<String> problem(ServerLevel level, MobProfile profile) {
        Built built = build(level, profile);
        built.mob().ifPresent(Entity::discard);
        return built.error();
    }

    private record Built(Optional<Mob> mob, Optional<String> error) {
        static Built failed(String error) {
            return new Built(Optional.empty(), Optional.of(error));
        }
    }

    private static Built build(ServerLevel level, MobProfile profile) {
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(profile.entity())) {
            return Built.failed("names unknown entity " + profile.entity());
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(profile.entity());
        Entity entity;
        if (profile.nbt().isPresent()) {
            CompoundTag tag;
            try {
                tag = TagParser.parseTag(profile.nbt().get());
            } catch (CommandSyntaxException e) {
                return Built.failed("has bad nbt: " + e.getMessage());
            }
            tag.putString("id", profile.entity().toString());
            entity = EntityType.loadEntityRecursive(tag, level, e -> e);
        } else {
            entity = type.create(level);
        }
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return Built.failed("names " + profile.entity() + ", which is not a mob");
        }
        if (profile.scale() != 1.0) {
            MobScale.set(mob, (float) profile.scale());
        }
        return new Built(Optional.of(mob), Optional.empty());
    }
}
