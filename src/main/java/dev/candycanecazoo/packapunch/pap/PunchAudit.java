package dev.candycanecazoo.packapunch.pap;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.config.GameSettings;
import dev.candycanecazoo.packapunch.config.ServerConfig;
import dev.candycanecazoo.packapunch.data.ItemSpec;
import dev.candycanecazoo.packapunch.map.validate.Problem;
import dev.candycanecazoo.packapunch.tacz.TaczBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Finds punchable items the machine would refuse (a {@code punch_id} with no {@link PunchDef}, or a non-gun with no
 * {@code punch_id}) before anyone plays. Runs on server start and after /reload (ERROR log lines), and in map validation.
 * Items from elsewhere (chests, other mods) can't be scanned; the machine still hands them back unchanged.
 */
@Mod.EventBusSubscriber(modid = PackAPunch.MOD_ID)
public final class PunchAudit {
    public static final String UNKNOWN_PUNCH_ID = "pap_unknown_punch_id";
    public static final String NO_UPGRADE_PATH = "pap_no_upgrade_path";

    /** One item the mod hands out. {@code pos} is the block it comes from, for items sold by a machine. */
    public record Entry(String source, Optional<BlockPos> pos, ItemSpec spec) {}

    public record Finding(Entry entry, PunchOutcome.RejectReason reason) {
        public String message() {
            String why = reason == PunchOutcome.RejectReason.UNKNOWN_PUNCH_ID
                    ? "its punch_id names no Pack-a-Punch definition"
                    : "it is not a TacZ gun and has no punch_id";
            return entry.source() + ": " + entry.spec().describe() + " is punchable, but " + why;
        }
    }

    /** Somewhere the mod hands items out from. */
    @FunctionalInterface
    public interface Source {
        List<Entry> entries(ServerLevel level);
    }

    /** Every source the audit scans. */
    private static final List<Source> SOURCES = List.of(PunchAudit::punchDefResults, PunchAudit::startingLoadout, PunchAudit::wallBuys,
            PunchAudit::boxPools);

    /** Each wall-buy in the map, read from its block. */
    private static List<Entry> wallBuys(ServerLevel level) {
        List<Entry> entries = new ArrayList<>();
        for (BlockPos pos : dev.candycanecazoo.packapunch.map.MapSavedData.get(level).map().features(dev.candycanecazoo.packapunch.map.FeatureKind.WALL_BUY)) {
            if (level.getBlockEntity(pos) instanceof dev.candycanecazoo.packapunch.feature.WallBuyBlock.Entity wallBuy) {
                entries.add(new Entry("wall-buy", Optional.of(pos), wallBuy.config().item()));
            }
        }
        return entries;
    }

    private static List<Entry> boxPools(ServerLevel level) {
        List<Entry> entries = new ArrayList<>();
        dev.candycanecazoo.packapunch.data.BoxPools.all().forEach((id, pool) ->
                pool.entries().forEach(e -> entries.add(new Entry("Mystery Box pool " + id, Optional.empty(), e.item()))));
        return entries;
    }

    private PunchAudit() {}

    /** The refusals that point at a mistake. Unpunchable and fully upgraded items are fine. */
    public static List<Finding> check(List<Entry> entries, Map<ResourceLocation, PunchDef> defs, Predicate<ItemSpec> isTaczGun) {
        List<Finding> findings = new ArrayList<>();
        for (Entry entry : entries) {
            PunchOutcome outcome = PunchResolver.resolve(tag(entry.spec()), isTaczGun.test(entry.spec()), defs, 0, Integer.MAX_VALUE);
            if (outcome instanceof PunchOutcome.Rejected rejected && (rejected.reason() == PunchOutcome.RejectReason.UNKNOWN_PUNCH_ID
                    || rejected.reason() == PunchOutcome.RejectReason.NO_UPGRADE_PATH)) {
                findings.add(new Finding(entry, rejected.reason()));
            }
        }
        return findings;
    }

    /** The NBT the spec's item would carry, as far as Pack-a-Punch cares. */
    static @Nullable CompoundTag tag(ItemSpec spec) {
        if (spec instanceof ItemSpec.Gun gun) {
            CompoundTag pap = new CompoundTag();
            pap.putBoolean(ItemSpec.PUNCHABLE, gun.punchable());
            CompoundTag tag = new CompoundTag();
            tag.put(ItemSpec.PAP, pap);
            return tag;
        }
        return ((ItemSpec.Plain) spec).nbt().orElse(null);
    }

    /** Against the loaded definitions and TacZ guns, for example to check one wall-buy as it is set. */
    public static List<Finding> check(List<Entry> entries) {
        return check(entries, PunchDefs.all(), PunchAudit::isTaczGun);
    }

    public static List<Finding> run(ServerLevel level) {
        List<Entry> entries = new ArrayList<>();
        SOURCES.forEach(source -> entries.addAll(source.entries(level)));
        return check(entries);
    }

    /** Warnings, not errors: the machine refuses these items safely, so they shouldn't stop a game. */
    public static List<Problem> problems(ServerLevel level) {
        return run(level).stream()
                .map(f -> Problem.warning(f.reason() == PunchOutcome.RejectReason.UNKNOWN_PUNCH_ID ? UNKNOWN_PUNCH_ID : NO_UPGRADE_PATH,
                        f.message(), f.entry().pos().orElse(null)))
                .toList();
    }

    private static boolean isTaczGun(ItemSpec spec) {
        return spec instanceof ItemSpec.Gun || spec.build(1).flatMap(TaczBridge::inspect).isPresent();
    }

    private static List<Entry> punchDefResults(ServerLevel level) {
        return PunchDefs.all().entrySet().stream()
                .map(e -> new Entry("Pack-a-Punch definition " + e.getKey(), Optional.empty(), e.getValue().result()))
                .toList();
    }

    private static List<Entry> startingLoadout(ServerLevel level) {
        GameSettings.Players players = ServerConfig.GAME.get().players();
        List<Entry> entries = new ArrayList<>();
        players.startingGuns().forEach(gun -> entries.add(new Entry("players.starting_guns", Optional.empty(),
                new ItemSpec.Gun(gun, players.startingGunsUnlimitedAmmo(), true))));
        players.startingItems().forEach(item -> entries.add(new Entry("players.starting_items", Optional.empty(),
                new ItemSpec.Plain(item, 1, Optional.empty()))));
        return entries;
    }

    private static void log(ServerLevel level) {
        for (Finding finding : run(level)) {
            PackAPunch.LOG.error("Pack-a-Punch: {}{}", finding.message(), finding.entry().pos().map(p -> " at " + p.toShortString()).orElse(""));
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        log(event.getServer().overworld());
    }

    /** Fired with no player once /reload has finished. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            log(event.getPlayerList().getServer().overworld());
        }
    }
}
