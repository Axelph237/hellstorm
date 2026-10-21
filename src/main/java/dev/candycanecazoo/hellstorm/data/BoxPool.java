package dev.candycanecazoo.hellstorm.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.util.Codecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * What a Mystery Crate can give, from {@code data/<ns>/hellstorm/box_pools/<id>.json}:
 * <pre>
 * { "include_all_tacz_guns": true, "exclude_wall_buys": true,
 *   "entries": [ { "item": { "tacz_gun": "tacz:m249" }, "weight": 2 } ],
 *   "overrides": { "tacz:glock_17": { "weight": 0 } } }
 * </pre>
 * Overrides are keyed by gun id or item id and apply to every pool item with that id; weight 0 removes it.
 */
public record BoxPool(boolean includeAllTaczGuns, boolean excludeWallBuys, List<Entry> entries, Map<ResourceLocation, WeightOverride> overrides) {
    public static final ResourceLocation DEFAULT_ID = Hellstorm.id("default");
    public static final BoxPool DEFAULT = new BoxPool(true, true, List.of(), Map.of());

    public record Entry(ItemSpec item, int weight) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemSpec.CODEC.fieldOf("item").forGetter(Entry::item),
                Codecs.strictOptional(Codec.intRange(0, Integer.MAX_VALUE), "weight", 1).forGetter(Entry::weight)
        ).apply(i, Entry::new));
    }

    public record WeightOverride(int weight) {
        static final Codec<WeightOverride> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("weight").forGetter(WeightOverride::weight)
        ).apply(i, WeightOverride::new));
    }

    public static final Codec<BoxPool> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codecs.strictOptional(Codec.BOOL, "include_all_tacz_guns", false).forGetter(BoxPool::includeAllTaczGuns),
            Codecs.strictOptional(Codec.BOOL, "exclude_wall_buys", false).forGetter(BoxPool::excludeWallBuys),
            Codecs.strictOptional(Entry.CODEC.listOf(), "entries", List.of()).forGetter(BoxPool::entries),
            Codecs.strictOptional(Codec.unboundedMap(ResourceLocation.CODEC, WeightOverride.CODEC), "overrides", Map.of()).forGetter(BoxPool::overrides)
    ).apply(i, BoxPool::new));

    public BoxPool {
        entries = List.copyOf(entries);
        overrides = Map.copyOf(overrides);
    }

    public record Weighted(ItemSpec item, int weight) {}

    /**
     * Every TacZ gun at weight 1 if asked, minus the map's wall-buy items if asked, then the entries (an entry for an item
     * already in the pool replaces it), then the overrides.
     */
    public List<Weighted> build(Collection<ResourceLocation> allGuns, Collection<ItemSpec> wallBuys) {
        List<Weighted> pool = new ArrayList<>();
        if (includeAllTaczGuns) {
            allGuns.forEach(gun -> pool.add(new Weighted(ItemSpec.Gun.of(gun), 1)));
        }
        if (excludeWallBuys) {
            pool.removeIf(w -> wallBuys.stream().anyMatch(sold -> sameItem(sold, w.item())));
        }
        for (Entry entry : entries) {
            Weighted weighted = new Weighted(entry.item(), entry.weight());
            int existing = indexOf(pool, entry.item());
            if (existing >= 0) {
                pool.set(existing, weighted);
            } else {
                pool.add(weighted);
            }
        }
        pool.replaceAll(w -> Optional.ofNullable(overrides.get(id(w.item()))).map(o -> new Weighted(w.item(), o.weight())).orElse(w));
        pool.removeIf(w -> w.weight() <= 0);
        return List.copyOf(pool);
    }

    /** A weighted pick among the items {@code skip} lets through. Empty when none are left. */
    public static Optional<ItemSpec> pick(List<Weighted> pool, Predicate<ItemSpec> skip, RandomSource random) {
        List<Weighted> open = pool.stream().filter(w -> !skip.test(w.item())).toList();
        int total = open.stream().mapToInt(Weighted::weight).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (Weighted w : open) {
            roll -= w.weight();
            if (roll < 0) {
                return Optional.of(w.item());
            }
        }
        throw new IllegalStateException("weights changed during a pick");
    }

    public static ResourceLocation id(ItemSpec item) {
        return item instanceof ItemSpec.Gun gun ? gun.gunId() : ((ItemSpec.Plain) item).item();
    }

    /** Guns are the same item whatever their flags; other items must match exactly, NBT and all. */
    public static boolean sameItem(ItemSpec a, ItemSpec b) {
        if (a instanceof ItemSpec.Gun ga && b instanceof ItemSpec.Gun gb) {
            return ga.gunId().equals(gb.gunId());
        }
        return a.equals(b);
    }

    private static int indexOf(List<Weighted> pool, ItemSpec item) {
        for (int i = 0; i < pool.size(); i++) {
            if (sameItem(pool.get(i).item(), item)) {
                return i;
            }
        }
        return -1;
    }
}
