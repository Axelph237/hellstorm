package dev.candycanecazoo.hellstorm.pap;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import dev.candycanecazoo.hellstorm.Hellstorm;
import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.config.ServerConfig;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.map.validate.Problem;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
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
 * {@code punch_id}) before anyone plays. Runs on server start and after /reload (ERROR log lines), in map validation,
 * and when an op joins (a chat summary).
 * Items from elsewhere (chests, other mods) can't be scanned; the machine still hands them back unchanged.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class PunchAudit {
    public static final String UNKNOWN_PUNCH_ID = "pap_unknown_punch_id";
    public static final String NO_UPGRADE_PATH = "pap_no_upgrade_path";
    private static final int NOTICE_LINES = 5;

    /** One item the mod hands out. {@code pos} is the block it comes from, for items sold by a machine. */
    public record Entry(String source, Optional<BlockPos> pos, ItemSpec spec) {}

    public record Finding(Entry entry, PunchOutcome.RejectReason reason) {
        public String message() {
            String why = reason == PunchOutcome.RejectReason.UNKNOWN_PUNCH_ID
                    ? "its punch_id names no upgrade definition"
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
        for (BlockPos pos : dev.candycanecazoo.hellstorm.map.MapSavedData.get(level).map().features(dev.candycanecazoo.hellstorm.map.FeatureKind.WALL_BUY)) {
            if (level.getBlockEntity(pos) instanceof dev.candycanecazoo.hellstorm.feature.WallBuyBlock.Entity wallBuy) {
                entries.add(new Entry("wall-buy", Optional.of(pos), wallBuy.config().item()));
            }
        }
        return entries;
    }

    private static List<Entry> boxPools(ServerLevel level) {
        List<Entry> entries = new ArrayList<>();
        dev.candycanecazoo.hellstorm.data.BoxPools.all().forEach((id, pool) ->
                pool.entries().forEach(e -> entries.add(new Entry("Mystery Crate pool " + id, Optional.empty(), e.item()))));
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

    /** The NBT the spec's item would carry, as far as the upgrader cares. */
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

    /** What an op sees in chat on joining: a count, the first few findings, and where to see the rest. Empty when all is well. */
    public static List<Component> opNotice(List<Finding> findings) {
        if (findings.isEmpty()) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("The upgrade check found " + findings.size() + (findings.size() == 1 ? " item" : " items")
                + " the upgrader would refuse:").withStyle(ChatFormatting.RED));
        findings.stream().limit(NOTICE_LINES).forEach(f -> lines.add(Component.literal(" - " + f.message()
                + f.entry().pos().map(p -> " at " + p.toShortString()).orElse("")).withStyle(ChatFormatting.GRAY)));
        int rest = findings.size() - NOTICE_LINES;
        lines.add(Component.literal((rest > 0 ? "...and " + rest + " more. " : "") + "See /hs map validate").withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private static boolean isTaczGun(ItemSpec spec) {
        return spec instanceof ItemSpec.Gun || spec.build(1).flatMap(TaczBridge::inspect).isPresent();
    }

    private static List<Entry> punchDefResults(ServerLevel level) {
        return PunchDefs.all().entrySet().stream()
                .map(e -> new Entry("upgrade definition " + e.getKey(), Optional.empty(), e.getValue().result()))
                .toList();
    }

    private static List<Entry> startingLoadout(ServerLevel level) {
        GameSettings.Players players = ServerConfig.GAME.get().players();
        List<Entry> entries = new ArrayList<>();
        players.startingGuns().forEach(gun -> entries.add(new Entry("players.starting_guns", Optional.empty(),
                new ItemSpec.Gun(gun.gunId(), players.startingGunsUnlimitedAmmo(), true, gun.nbt()))));
        players.startingItems().forEach(item -> entries.add(new Entry("players.starting_items", Optional.empty(), item)));
        return entries;
    }

    private static void log(ServerLevel level) {
        for (Finding finding : run(level)) {
            Hellstorm.LOG.error("Upgrader: {}{}", finding.message(), finding.entry().pos().map(p -> " at " + p.toShortString()).orElse(""));
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

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.hasPermissions(2)) {
            opNotice(run(player.serverLevel())).forEach(player::sendSystemMessage);
        }
    }
}
