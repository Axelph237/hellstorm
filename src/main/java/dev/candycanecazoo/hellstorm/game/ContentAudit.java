package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.data.BoxPool;
import dev.candycanecazoo.hellstorm.data.BoxPools;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.feature.ConfigBlockEntity;
import dev.candycanecazoo.hellstorm.feature.MysteryBoxBlock;
import dev.candycanecazoo.hellstorm.feature.WallBuyBlock;
import dev.candycanecazoo.hellstorm.map.FeatureKind;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.map.validate.Problem;
import dev.candycanecazoo.hellstorm.mob.MobFactory;
import dev.candycanecazoo.hellstorm.mob.MobProfile;
import dev.candycanecazoo.hellstorm.mob.MobProfiles;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import dev.candycanecazoo.hellstorm.util.DataFiles;
import dev.candycanecazoo.hellstorm.util.Highlight;
import dev.candycanecazoo.hellstorm.wave.WaveEffect;
import dev.candycanecazoo.hellstorm.wave.WaveEventDefs;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks the loaded content a game on this map depends on, for map validation: mob profiles that can't make their mob,
 * rounds no profile can spawn in (which would never end), wave overrides naming profiles that can't spawn, Mystery Crates
 * whose pool is empty, machines whose settings are no longer valid (a wall-buy for a gun that's gone), and datapack files
 * skipped at the last reload.
 */
public final class ContentAudit {
    public static final String NO_MOB_FOR_ROUND = "no_mob_for_round";
    public static final String MOB_PROFILE_BROKEN = "mob_profile_broken";
    public static final String WAVE_OVERRIDE_UNSPAWNABLE = "wave_override_unspawnable";
    public static final String BOX_POOL_EMPTY = "box_pool_empty";
    public static final String FEATURE_CONFIG = "feature_config";
    public static final String BAD_DATA_FILE = "bad_data_file";

    private ContentAudit() {}

    public static List<Problem> problems(ServerLevel level, ZombiesMap map) {
        List<Problem> problems = new ArrayList<>();
        DataFiles.errors().forEach((kind, errors) -> errors.forEach(error ->
                problems.add(Problem.warning(BAD_DATA_FILE, Highlight.of("Bad {} {}: the file was skipped", kind, error), null))));
        Map<ResourceLocation, MobProfile> profiles = MobProfiles.all();
        Set<ResourceLocation> broken = new HashSet<>();
        profiles.forEach((id, profile) -> MobFactory.problem(level, profile).ifPresent(why -> {
            broken.add(id);
            problems.add(Problem.warning(MOB_PROFILE_BROKEN, Highlight.of("Mob profile {} can't spawn: it {}", id, why), null));
        }));
        problems.addAll(spawnReadiness(roster(profiles, broken), overrides(), id -> profiles.containsKey(id) && !broken.contains(id)));
        problems.addAll(emptyCrates(level, map));
        problems.addAll(featureSettings(level, map));
        return problems;
    }

    /** The roster a game would build, from the profiles that can spawn. */
    static MobRoster roster(Map<ResourceLocation, MobProfile> profiles, Set<ResourceLocation> broken) {
        return new MobRoster(profiles.entrySet().stream().filter(e -> !broken.contains(e.getKey()))
                .map(e -> new MobRoster.Entry(e.getKey(), e.getValue().weight(), e.getValue().minRound(), e.getValue().maxRound()))
                .toList());
    }

    private static Map<ResourceLocation, List<ResourceLocation>> overrides() {
        Map<ResourceLocation, List<ResourceLocation>> overrides = new LinkedHashMap<>();
        WaveEventDefs.all().forEach((id, def) -> def.effects().forEach(effect -> {
            if (effect instanceof WaveEffect.MobOverride override) {
                overrides.computeIfAbsent(id, key -> new ArrayList<>()).addAll(override.profiles());
            }
        }));
        return overrides;
    }

    /**
     * An ERROR for the first round no profile that can spawn is allowed in: nothing would spawn and the round would never
     * end. A WARNING for each wave event override naming a profile that can't spawn (the round falls back to the usual
     * zombies if none of its profiles can).
     */
    static List<Problem> spawnReadiness(MobRoster roster, Map<ResourceLocation, List<ResourceLocation>> overrides,
                                        java.util.function.Predicate<ResourceLocation> spawnable) {
        List<Problem> problems = new ArrayList<>();
        roster.firstEmptyRound().ifPresent(round -> problems.add(Problem.error(NO_MOB_FOR_ROUND, Highlight.of(
                "No mob profile can spawn in round {}: every profile there has weight 0, is outside its min_round/max_round, or is broken",
                round), null)));
        overrides.forEach((event, named) -> named.stream().filter(id -> !spawnable.test(id)).distinct().forEach(id ->
                problems.add(Problem.warning(WAVE_OVERRIDE_UNSPAWNABLE,
                        Highlight.of("Wave event {} names mob profile {}, which isn't loaded or can't spawn", event, id), null))));
        return problems;
    }

    /** A WARNING for each loaded pool a crate on this map uses that builds empty: the crate would refuse every roll. */
    private static List<Problem> emptyCrates(ServerLevel level, ZombiesMap map) {
        List<ItemSpec> wallBuys = map.features(FeatureKind.WALL_BUY).stream()
                .flatMap(pos -> WallBuyBlock.entity(level, pos).stream()).map(entity -> entity.config().item()).toList();
        Map<ResourceLocation, BlockPos> pools = new LinkedHashMap<>();
        for (BlockPos pos : map.features(FeatureKind.MYSTERY_BOX)) {
            MysteryBoxBlock.entity(level, pos).ifPresent(entity -> pools.putIfAbsent(entity.config().pool(), pos));
        }
        List<Problem> problems = new ArrayList<>();
        pools.forEach((id, pos) -> {
            Optional<BoxPool> pool = BoxPools.get(id);
            if (pool.isPresent() && pool.get().build(TaczBridge.allGuns(), wallBuys).isEmpty()) {
                problems.add(Problem.warning(BOX_POOL_EMPTY, Highlight.of(
                        "Mystery Crate pool {} has nothing to give: it sets neither include_all_tacz_guns nor entries, or every gun in it is a wall-buy or has weight 0", id), pos));
            }
        });
        return problems;
    }

    /** A WARNING for each machine whose settings are no longer valid, such as a wall-buy for a gun that isn't installed. */
    private static List<Problem> featureSettings(ServerLevel level, ZombiesMap map) {
        List<Problem> problems = new ArrayList<>();
        map.features().forEach((pos, kind) -> {
            if (kind != FeatureKind.SPAWN && level.getBlockEntity(pos) instanceof ConfigBlockEntity<?> entity) {
                check(entity).ifPresent(why -> problems.add(Problem.warning(FEATURE_CONFIG,
                        Highlight.of("The machine at {}: {}", pos.toShortString(), why), pos)));
            }
        });
        return problems;
    }

    private static <C> Optional<String> check(ConfigBlockEntity<C> entity) {
        return entity.check(entity.config());
    }
}
