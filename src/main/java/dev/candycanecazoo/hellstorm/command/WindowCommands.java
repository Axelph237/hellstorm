package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.candycanecazoo.hellstorm.config.GameSettings;
import dev.candycanecazoo.hellstorm.config.ServerConfig;
import dev.candycanecazoo.hellstorm.game.WindowOps;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.WindowDef;
import dev.candycanecazoo.hellstorm.map.ZoneIndex;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.tools.MapEdits;
import dev.candycanecazoo.hellstorm.tools.MapHistory;
import dev.candycanecazoo.hellstorm.tools.Selection;
import dev.candycanecazoo.hellstorm.tools.ToolState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/** The window branches of {@code /hs zones}. A window is made from the Zone Wand selection: select the hole in the wall, then add it. */
final class WindowCommands {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final SimpleCommandExceptionType NO_SELECTION = new SimpleCommandExceptionType(
            Component.literal("Select the window's opening with the Zone Wand: fill the hole with any blocks, then click its corners"));
    private static final SimpleCommandExceptionType NOT_ONE_BOX = new SimpleCommandExceptionType(Component.literal(
            "A window's opening is one box, and the selection is several. Sneak-click (or /hs zones cancel) and select just the opening"));
    private static final SuggestionProvider<CommandSourceStack> WINDOWS = (ctx, builder) -> SharedSuggestionProvider.suggest(
            MapSavedData.get(ctx.getSource().getLevel()).map().windows().keySet(), builder);

    private WindowCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> add() {
        return Commands.literal("window").then(name()
                .executes(ctx -> add(ctx, OptionalInt.empty()))
                .then(Commands.argument("boards", IntegerArgumentType.integer(1, WindowDef.MAX_BOARDS))
                        .executes(ctx -> add(ctx, OptionalInt.of(IntegerArgumentType.getInteger(ctx, "boards"))))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> remove() {
        return Commands.literal("window").then(name().executes(WindowCommands::remove));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> name() {
        return Commands.argument("window", StringArgumentType.word()).suggests(WINDOWS);
    }

    /**
     * Which way is out. The opening must be one block thick along that axis, with a room right behind it on one side and
     * no room on the other.
     */
    static Optional<Direction> outward(BlockBox opening, ZoneIndex index) {
        BlockPos base = new BlockPos(opening.center().getX(), opening.minY(), opening.center().getZ());
        List<Direction> outs = new ArrayList<>();
        for (Direction out : Direction.Plane.HORIZONTAL) {
            int thickness = out.getAxis() == Direction.Axis.X ? opening.sizeX() : opening.sizeZ();
            if (thickness == 1 && index.zoneAt(base.relative(out)).isEmpty() && index.zoneAt(base.relative(out.getOpposite())).isPresent()) {
                outs.add(out);
            }
        }
        return outs.size() == 1 ? Optional.of(outs.get(0)) : Optional.empty();
    }

    /**
     * Makes the selection a window. A new window holds {@code boards}, or the most there can be; adding an existing one
     * moves its opening and keeps its boards.
     */
    private static int add(CommandContext<CommandSourceStack> ctx, OptionalInt boards) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        String id = StringArgumentType.getString(ctx, "window");
        if (!ID.matcher(id).matches()) {
            ctx.getSource().sendFailure(Component.literal("Window names use 1-32 of a-z, 0-9, _"));
            return 0;
        }
        Selection selection = ToolState.selection(player);
        if (selection.isEmpty()) {
            throw NO_SELECTION.create();
        }
        BlockBox opening = selection.single().orElseThrow(NOT_ONE_BOX::create);
        ZombiesMap map = MapSavedData.get(level).map();
        Optional<Direction> outward = outward(opening, ZoneIndex.of(map));
        if (outward.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Couldn't tell inside from outside. The selection must be one block thick, "
                    + "with a room on exactly one side and no room on the other"));
            return 0;
        }
        Optional<WindowDef> old = Optional.ofNullable(map.windows().get(id));
        WindowDef window = new WindowDef(id, opening, outward.get(), old.map(WindowDef::maxBoards).orElse(boards.orElse(WindowDef.MAX_BOARDS)),
                ZoneIndex.of(map).zoneAt(opening.min().relative(outward.get().getOpposite())).orElseThrow());
        GameSettings.Windows rules = ServerConfig.GAME.get().windows();
        if (window.width() > rules.maxWidth() || window.height() > rules.maxHeight()) {
            ctx.getSource().sendFailure(Component.literal("That opening is " + window.width() + "x" + window.height()
                    + ". The limit is " + rules.maxWidth() + "x" + rules.maxHeight() + " (windows.max_width / max_height)"));
            return 0;
        }
        Optional<WindowDef> overlap = map.windows().values().stream()
                .filter(w -> !w.id().equals(id) && w.opening().intersects(opening)).findFirst();
        if (overlap.isPresent()) {
            ctx.getSource().sendFailure(Component.literal("That overlaps window '" + overlap.get().id() + "'"));
            return 0;
        }
        List<BlockPos> blocks = new ArrayList<>();
        old.ifPresent(w -> blocks.addAll(cells(w.opening())));
        blocks.addAll(cells(opening));
        MapHistory.record(level, "add window '" + id + "'", blocks, () -> {
            old.ifPresent(w -> clear(level, w));
            MapEdits.apply(level, m -> m.withWindow(window));
            WindowOps.setBoards(level, window, window.maxBoards());
        });
        return 1;
    }

    private static List<BlockPos> cells(BlockBox box) {
        List<BlockPos> cells = new ArrayList<>();
        BlockPos.betweenClosed(box.min(), box.max()).forEach(p -> cells.add(p.immutable()));
        return cells;
    }

    private static void clear(ServerLevel level, WindowDef window) {
        for (BlockPos pos : BlockPos.betweenClosed(window.opening().min(), window.opening().max())) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        String id = StringArgumentType.getString(ctx, "window");
        Optional<WindowDef> window = Optional.ofNullable(MapSavedData.get(level).map().windows().get(id));
        if (window.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No window named '" + id + "'"));
            return 0;
        }
        MapHistory.record(level, "remove window '" + id + "'", cells(window.get().opening()), () -> {
            clear(level, window.get());
            MapEdits.apply(level, m -> m.withoutWindow(id));
        });
        ctx.getSource().sendSuccess(() -> Component.literal("Removed window '" + id + "'"), false);
        return 1;
    }

    static void list(CommandContext<CommandSourceStack> ctx, ZombiesMap map) {
        ServerLevel level = ctx.getSource().getLevel();
        for (WindowDef w : map.windows().values()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Window " + w.id() + ": " + w.width() + "x" + w.height() + " into '" + w.zone() + "', "
                    + WindowOps.boards(level, w) + "/" + w.maxBoards() + " boards"), false);
        }
    }
}
