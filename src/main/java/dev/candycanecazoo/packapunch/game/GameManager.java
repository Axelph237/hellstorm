package dev.candycanecazoo.packapunch.game;

import dev.candycanecazoo.packapunch.config.ServerConfig;
import dev.candycanecazoo.packapunch.config.SettingsResolver;
import dev.candycanecazoo.packapunch.map.MapSavedData;
import dev.candycanecazoo.packapunch.map.Passages;
import dev.candycanecazoo.packapunch.map.ZoneIndex;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.map.validate.MapValidator;
import dev.candycanecazoo.packapunch.map.validate.ValidationReport;
import dev.candycanecazoo.packapunch.script.GameScripts;
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
    public record Active(ServerLevel level, Game game, ForgeGameWorld world, GameScripts scripts) {}

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
    public static CreateResult create(ServerLevel level) {
        ZombiesMap map = MapSavedData.get(level).map();
        int maxGap = ServerConfig.MAX_PASSAGE_GAP.get();
        ValidationReport report = MapValidator.validate(map, LevelCellProbe.of(level), maxGap);
        if (!report.playable()) {
            return new CreateResult.Invalid(report);
        }
        GameMap gameMap = GameMap.of(map, Passages.find(map, ZoneIndex.of(map), LevelCellProbe.of(level), maxGap).open());
        List<net.minecraft.core.BlockPos> startCells = StartCells.find(level, map.startZones());
        if (startCells.isEmpty()) {
            return new CreateResult.NoStartCells();
        }
        return new CreateResult.Created(register(level, gameMap, startCells, level.getServer().getPlayerList()::getPlayer));
    }

    /** Registers a game directly. GameTests use this with their own map and player lookup. */
    public static Active register(ServerLevel level, GameMap map, List<net.minecraft.core.BlockPos> startCells, Function<UUID, ServerPlayer> lookup) {
        var settings = SettingsResolver.resolve(ServerConfig.GAME.get(), Optional.empty()).getOrThrow(false, IllegalStateException::new);
        Game game = new Game(settings, map, RandomSource.create(), new GameEvents());
        ForgeGameWorld world = new ForgeGameWorld(level, startCells, () -> game.players().stream().map(PlayerState::id).toList(), lookup);
        Active active = new Active(level, game, world, GameScripts.attach(level, game, origin(map, startCells)));
        GAMES.put(level.dimension(), active);
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
