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
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(profile.entity());
        if (type == null || !ForgeRegistries.ENTITY_TYPES.containsKey(profile.entity())) {
            Hellstorm.LOG.error("Mob profile {} names unknown entity {}", profileId, profile.entity());
            return Optional.empty();
        }
        Entity entity;
        if (profile.nbt().isPresent()) {
            CompoundTag tag;
            try {
                tag = TagParser.parseTag(profile.nbt().get());
            } catch (CommandSyntaxException e) {
                Hellstorm.LOG.error("Mob profile {} has bad nbt: {}", profileId, e.getMessage());
                return Optional.empty();
            }
            tag.putString("id", profile.entity().toString());
            entity = EntityType.loadEntityRecursive(tag, level, e -> e);
        } else {
            entity = type.create(level);
        }
        if (!(entity instanceof Mob mob)) {
            Hellstorm.LOG.error("Mob profile {} names {}, which is not a mob", profileId, profile.entity());
            return Optional.empty();
        }
        if (profile.scale() != 1.0) {
            MobScale.set(mob, (float) profile.scale());
        }
        return Optional.of(mob);
    }
}
