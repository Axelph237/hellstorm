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
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Finds mistakes in upgrades before anyone plays: punchable items the machine would refuse (a {@code punch_id} with no
 * {@link PunchDef}, or a non-gun with no {@code punch_id}), and upgrade files with bad entries, results that aren't
 * installed, or gun classes no installed gun has. Runs on server start and after /reload (ERROR log lines), in map
 * validation, and when an op joins (a chat summary).
 * Items from elsewhere (chests, other mods) can't be scanned; the machine still hands them back unchanged.
 */
@Mod.EventBusSubscriber(modid = Hellstorm.MOD_ID)
public final class PunchAudit {
    private static final int NOTICE_LINES = 5;

    /** One item the mod hands out. {@code pos} is the block it comes from, for items sold by a machine. */
    public record Entry(String source, Optional<BlockPos> pos, ItemSpec spec) {}

    /** {@code code} is the problem code in map validation. */
    public enum Kind {
        UNKNOWN_PUNCH_ID("pap_unknown_punch_id"),
        NO_UPGRADE_PATH("pap_no_upgrade_path"),
        BAD_ENTRY("pap_bad_entry"),
        MISSING_RESULT("pap_missing_result"),
        UNKNOWN_GUN_CLASS("pap_unknown_gun_class");

        public final String code;

        Kind(String code) {
            this.code = code;
        }
    }

    public record Finding(Kind kind, String message, Optional<BlockPos> pos) {
        static Finding of(Entry entry, PunchOutcome.RejectReason reason) {
            boolean unknown = reason == PunchOutcome.RejectReason.UNKNOWN_PUNCH_ID;
            return new Finding(unknown ? Kind.UNKNOWN_PUNCH_ID : Kind.NO_UPGRADE_PATH, entry.source() + ": " + entry.spec().describe()
                    + " is punchable, but " + (unknown ? "its punch_id names no upgrade definition" : "it is not a TacZ gun and has no punch_id"),
                    entry.pos());
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
                findings.add(Finding.of(entry, rejected.reason()));
            }
        }
        return findings;
    }

    /**
     * Entries the upgrade files skipped, results that are neither an installed item nor a TacZ gun, and defaults for a gun
     * class that no installed gun has.
     */
    public static List<Finding> checkFiles(PunchDefs.Loaded loaded, Predicate<ResourceLocation> installed, Set<String> gunClasses) {
        List<Finding> findings = new ArrayList<>();
        loaded.errors().forEach(error -> findings.add(new Finding(Kind.BAD_ENTRY, "upgrade file " + error, Optional.empty())));
        loaded.defs().forEach((id, def) -> {
            if (!installed.test(def.itemId())) {
                findings.add(new Finding(Kind.MISSING_RESULT, "upgrade definition " + id + ": its item-id " + def.itemId()
                        + " is not an installed item or TacZ gun", Optional.empty()));
            }
        });
        loaded.defaults().keySet().stream().filter(gunClass -> !gunClasses.contains(gunClass)).forEach(gunClass -> findings.add(new Finding(
                Kind.UNKNOWN_GUN_CLASS, "upgrade defaults: \"" + gunClass + "\" is not the class of any installed TacZ gun (they are "
                + String.join(", ", new TreeSet<>(gunClasses)) + ")", Optional.empty())));
        return findings;
    }

    /** The NBT the spec's item would carry, as far as the upgrader cares. */
    static @Nullable CompoundTag tag(ItemSpec spec) {
        if (spec instanceof ItemSpec.Gun gun) {
            CompoundTag tag = gun.nbt().map(CompoundTag::copy).orElseGet(CompoundTag::new);
            if (gun.punchable()) {
                CompoundTag pap = tag.getCompound(ItemSpec.PAP);
                pap.putBoolean(ItemSpec.PUNCHABLE, true);
                tag.put(ItemSpec.PAP, pap);
            }
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
        List<Finding> findings = new ArrayList<>(checkFiles(PunchDefs.loaded(),
                id -> ForgeRegistries.ITEMS.containsKey(id) || TaczBridge.gunExists(id), TaczBridge.gunClasses()));
        findings.addAll(check(entries));
        return findings;
    }

    /** Warnings, not errors: the machine refuses these items safely, so they shouldn't stop a game. */
    public static List<Problem> problems(ServerLevel level) {
        return run(level).stream().map(f -> Problem.warning(f.kind().code, f.message(), f.pos().orElse(null))).toList();
    }

    /** What an op sees in chat on joining: a count, the first few findings, and where to see the rest. Empty when all is well. */
    public static List<Component> opNotice(List<Finding> findings) {
        if (findings.isEmpty()) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("The upgrade check found " + findings.size() + (findings.size() == 1 ? " problem" : " problems")
                + " with upgrades:").withStyle(ChatFormatting.RED));
        findings.stream().limit(NOTICE_LINES).forEach(f -> lines.add(Component.literal(" - " + f.message()
                + f.pos().map(p -> " at " + p.toShortString()).orElse("")).withStyle(ChatFormatting.GRAY)));
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
            Hellstorm.LOG.error("Upgrader: {}{}", finding.message(), finding.pos().map(p -> " at " + p.toShortString()).orElse(""));
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
