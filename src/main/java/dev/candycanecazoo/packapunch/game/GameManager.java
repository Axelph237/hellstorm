package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.config.ServerConfig;
import dev.candycanecazoo.packapunch.config.SettingsResolver;
import dev.candycanecazoo.packapunch.map.MapSavedData;
import dev.candycanecazoo.packapunch.map.Passages;
import dev.candycanecazoo.packapunch.map.ZoneIndex;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.map.validate.MapValidator;
import dev.candycanecazoo.packapunch.map.validate.ValidationContext;
import dev.candycanecazoo.packapunch.map.validate.ValidationReport;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import dev.candycanecazoo.packapunch.script.GameScripts;
import dev.candycanecazoo.packapunch.mob.MobProfile;
import dev.candycanecazoo.packapunch.mob.MobProfiles;
import dev.candycanecazoo.packapunch.tools.LevelCellProbe;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** At most one game per level. Owns the link between a {@link Game} and its {@link ForgeGameWorld}. */
public final class GameManager {
    public record Active(ServerLevel level, Game game, ForgeGameWorld world, GameScripts scripts, GamePathing pathing) {}

    public sealed interface CreateResult {
        record Created(Active active) implements CreateResult {}

        record Invalid(ValidationReport report) implements CreateResult {}

        record NoStartCells() implements CreateResult {}
    }

    private static final Map<ResourceKey<Level>, Active> GAMES = new LinkedHashMap<>();

    private GameManager() {}

    public static Optional<Active> get(ServerLevel level) {
        return Optional.ofNullable(GAMES.get(level.dimension()));
    }

    public static Collection<Active> all() {
        return List.copyOf(GAMES.values());
    }

    public static Optional<Active> forPlayer(UUID player) {
        return GAMES.values().stream().filter(a -> a.game().player(player).isPresent()).findFirst();
    }

    public static Optional<Active> forMob(UUID mob) {
        return GAMES.values().stream().filter(a -> a.game().hasMob(mob)).findFirst();
    }

    /** Validates the level's map and opens a lobby for it. */
    /** How this level's map is validated: its blocks, the window rules, and every mob profile's hitbox. */
    public static ValidationContext validationContext(ServerLevel level) {
        List<ValidationContext.MobSize> mobs = new ArrayList<>();
        MobProfiles.all().forEach((id, profile) -> {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(profile.entity());
            if (type != null) {
                mobs.add(new ValidationContext.MobSize(id.toString(), type.getWidth(), type.getHeight()));
            }
        });
        return new ValidationContext(LevelCellProbe.of(level), pos -> StartCells.standable(level, pos), ServerConfig.MAX_PASSAGE_GAP.get(),
                settings().windows(), mobs);
    }

    private static dev.candycanecazoo.packapunch.config.GameSettings settings() {
        return SettingsResolver.resolve(ServerConfig.GAME.get(), Optional.empty()).getOrThrow(false, IllegalStateException::new);
    }

    public static CreateResult create(ServerLevel level) {
        ZombiesMap map = MapSavedData.get(level).map();
        ValidationContext context = validationContext(level);
        ValidationReport report = MapValidator.validate(map, context);
        if (!report.playable()) {
            return new CreateResult.Invalid(report);
        }
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), context.probe(), context.maxPassageGap());
        GameMap gameMap = GameMap.of(map, passages.open(), context.windows().linkRange());
        List<net.minecraft.core.BlockPos> startCells = StartCells.find(level, map.startZones());
        if (startCells.isEmpty()) {
            return new CreateResult.NoStartCells();
        }
        MapReset.reset(level, map);
        return new CreateResult.Created(register(level, gameMap, passages, startCells, level.getServer().getPlayerList()::getPlayer));
    }

    /** Registers a game directly, finding passages from the live world. GameTests use this with their own map and player lookup. */
    public static Active register(ServerLevel level, GameMap map, List<net.minecraft.core.BlockPos> startCells, Function<UUID, ServerPlayer> lookup) {
        return register(level, map, Passages.find(map.map(), map.index(), LevelCellProbe.of(level), ServerConfig.MAX_PASSAGE_GAP.get()), startCells, lookup);
    }

    public static Active register(ServerLevel level, GameMap map, Passages.Result passages, List<net.minecraft.core.BlockPos> startCells,
                                  Function<UUID, ServerPlayer> lookup) {
        var settings = settings();
        Map<net.minecraft.resources.ResourceLocation, MobProfile> profiles = MobProfiles.all();
        MobRoster roster = new MobRoster(profiles.entrySet().stream()
                .map(e -> new MobRoster.Entry(e.getKey(), e.getValue().weight(), e.getValue().minRound(), e.getValue().maxRound()))
                .toList());
        Game game = new Game(settings, map, roster, RandomSource.create(), new GameEvents());
        GamePathing pathing = new GamePathing(level, map, passages);
        ForgeGameWorld world = new ForgeGameWorld(level, map, startCells, () -> game.players().stream().map(PlayerState::id).toList(), lookup,
                profiles, game::targetable, settings.windows().tearTicks(), pathing);
        Active active = new Active(level, game, world, GameScripts.attach(level, game, origin(map, startCells)), pathing);
        GAMES.put(level.dimension(), active);
        dev.candycanecazoo.packapunch.feature.MysteryBoxes.start(active);
        return active;
    }

    /** Where script commands run: the center of the first start zone, or the first start cell. */
    static net.minecraft.core.BlockPos origin(GameMap map, List<net.minecraft.core.BlockPos> startCells) {
        return map.map().startZones().stream().flatMap(z -> z.boxes().stream()).findFirst()
                .map(dev.candycanecazoo.packapunch.map.BlockBox::center)
                .orElse(startCells.isEmpty() ? net.minecraft.core.BlockPos.ZERO : startCells.get(0));
    }

    public static void unregister(ServerLevel level) {
        GAMES.remove(level.dimension());
    }

    public static void tick(MinecraftServer server) {
        for (Active active : all()) {
            active.game().tick(active.world());
            if (active.game().finished()) {
                end(active);
            }
        }
    }

    /** Stops the game if needed, gives every player back their normal state, and removes the game. */
    public static void end(Active active) {
        active.game().stop(active.world());
        MapReset.reset(active.level(), active.game().map().map());
        active.pathing().close();
        Sessions sessions = Sessions.get(active.level().getServer());
        for (PlayerState player : new ArrayList<>(active.game().players())) {
            ServerPlayer online = active.level().getServer().getPlayerList().getPlayer(player.id());
            if (online != null) {
                sessions.restore(online);
            }
        }
        GAMES.remove(active.level().dimension());
    }

    public static void endAll() {
        all().forEach(GameManager::end);
    }
}
