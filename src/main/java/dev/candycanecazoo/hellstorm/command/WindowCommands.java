package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
import java.util.regex.Pattern;

/** `/hs window add|remove|list`. A window is made from the Zone Wand selection: select the hole in the wall, then add it. */
final class WindowCommands {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    private static final SimpleCommandExceptionType NO_SELECTION = new SimpleCommandExceptionType(
            Component.literal("Select the window's opening with the Zone Wand: fill the hole with any blocks, then click its corners"));
    private static final SuggestionProvider<CommandSourceStack> WINDOWS = (ctx, builder) -> SharedSuggestionProvider.suggest(
            MapSavedData.get(ctx.getSource().getLevel()).map().windows().keySet(), builder);

    private WindowCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> window() {
        return Commands.literal("window")
                .then(Commands.literal("add").then(Commands.argument("window", StringArgumentType.word())
                        .executes(ctx -> add(ctx, WindowDef.MAX_BOARDS))
                        .then(Commands.argument("boards", IntegerArgumentType.integer(1, WindowDef.MAX_BOARDS))
                                .executes(ctx -> add(ctx, IntegerArgumentType.getInteger(ctx, "boards"))))))
                .then(Commands.literal("remove").then(Commands.argument("window", StringArgumentType.word()).suggests(WINDOWS)
                        .executes(WindowCommands::remove)))
                .then(Commands.literal("list").executes(WindowCommands::list));
    }

    /**
     * Which way is out. The opening must be one block thick along that axis, with a zone right behind it on one side and
     * no zone on the other.
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

    private static int add(CommandContext<CommandSourceStack> ctx, int boards) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        String id = StringArgumentType.getString(ctx, "window");
        if (!ID.matcher(id).matches()) {
            ctx.getSource().sendFailure(Component.literal("Window ids use 1-32 of a-z, 0-9, _"));
            return 0;
        }
        BlockBox opening = ToolState.selection(player).box().orElseThrow(NO_SELECTION::create);
        ZombiesMap map = MapSavedData.get(level).map();
        Optional<Direction> outward = outward(opening, ZoneIndex.of(map));
        if (outward.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Couldn't tell inside from outside. The selection must be one block thick, "
                    + "with a zone on exactly one side and no zone on the other"));
            return 0;
        }
        WindowDef window = new WindowDef(id, opening, outward.get(), boards,
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
        map.windows().values().stream().filter(w -> w.id().equals(id)).findFirst().ifPresent(old -> clear(level, old));
        MapEdits.apply(level, m -> m.withWindow(window));
        WindowOps.setBoards(level, window, boards);
        ToolState.setSelection(player, Selection.NONE);
        return 1;
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
        clear(level, window.get());
        MapEdits.apply(level, m -> m.withoutWindow(id));
        ctx.getSource().sendSuccess(() -> Component.literal("Removed window '" + id + "'"), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        var windows = MapSavedData.get(level).map().windows().values();
        if (windows.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No windows yet. Select a hole in a zone's wall with the Zone Wand, then /hs window add <id>"), false);
            return 0;
        }
        for (WindowDef w : windows) {
            ctx.getSource().sendSuccess(() -> Component.literal(w.id() + ": " + w.width() + "x" + w.height() + " into '" + w.zone() + "', "
                    + WindowOps.boards(level, w) + "/" + w.maxBoards() + " boards"), false);
        }
        return windows.size();
    }
}
