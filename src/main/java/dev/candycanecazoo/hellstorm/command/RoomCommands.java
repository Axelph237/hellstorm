package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.candycanecazoo.hellstorm.config.ServerConfig;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.BoxMerger;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.RoomDetector;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZoneIndex;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.tools.MapHistory;
import dev.candycanecazoo.hellstorm.tools.RoomDetection;
import dev.candycanecazoo.hellstorm.tools.Selection;
import dev.candycanecazoo.hellstorm.tools.ToolState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/** The room branches of {@code /hs zones}. A room is made of boxes from the Zone Wand selection or from room detection. */
final class RoomCommands {
    private static final SimpleCommandExceptionType NO_DETECTION =
            new SimpleCommandExceptionType(Component.literal("No pending detection. Run /hs zones detect <room> first"));
    private static final DynamicCommandExceptionType BAD_ID =
            new DynamicCommandExceptionType(id -> Component.literal("Invalid room name '" + id + "': use 1-32 of a-z, 0-9, _"));
    private static final DynamicCommandExceptionType UNKNOWN =
            new DynamicCommandExceptionType(id -> Component.literal("No room named '" + id + "'"));
    private static final DynamicCommandExceptionType NOT_IN_ROOM =
            new DynamicCommandExceptionType(id -> Component.literal("The selection doesn't touch room '" + id + "'"));
    private static final DynamicCommandExceptionType OVERLAP =
            new DynamicCommandExceptionType(id -> Component.literal("That box overlaps room '" + id + "'"));

    static final SuggestionProvider<CommandSourceStack> ROOMS = (ctx, builder) -> SharedSuggestionProvider.suggest(
            MapSavedData.get(ctx.getSource().getLevel()).map().zones().keySet().stream().map(ZoneId::value), builder);

    private RoomCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> add() {
        return Commands.literal("room").then(name()
                .executes(ctx -> add(ctx, Optional.empty()))
                .then(Commands.argument("start", BoolArgumentType.bool())
                        .executes(ctx -> add(ctx, Optional.of(BoolArgumentType.getBool(ctx, "start"))))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> cut() {
        return Commands.literal("room").then(name().executes(RoomCommands::cut));
    }

    static LiteralArgumentBuilder<CommandSourceStack> remove() {
        return Commands.literal("room").then(name().executes(RoomCommands::remove));
    }

    static LiteralArgumentBuilder<CommandSourceStack> set() {
        return Commands.literal("room").then(name()
                .then(Commands.literal("start").then(Commands.argument("start", BoolArgumentType.bool()).executes(RoomCommands::setStart))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> detect() {
        return Commands.literal("detect").then(name().executes(RoomCommands::detect));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> name() {
        return Commands.argument("room", StringArgumentType.word()).suggests(ROOMS);
    }

    private static ZoneId roomId(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return parse(StringArgumentType.getString(ctx, "room"));
    }

    static ZoneId parse(String raw) throws CommandSyntaxException {
        if (!ZoneId.isValid(raw)) {
            throw BAD_ID.create(raw);
        }
        return new ZoneId(raw);
    }

    private static Zone existing(ServerLevel level, ZoneId id) throws CommandSyntaxException {
        return MapSavedData.get(level).map().zone(id).orElseThrow(() -> UNKNOWN.create(id));
    }

    /** Adds the selection to the room. A new room starts with {@code start}; an existing one keeps its own. */
    private static int add(CommandContext<CommandSourceStack> ctx, Optional<Boolean> start) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ZoneId id = roomId(ctx);
        BlockBox box = ZonesCommands.selection(player);
        ServerLevel level = player.serverLevel();
        Optional<ZoneId> overlap = MapSavedData.get(level).map().zoneOverlapping(box, id);
        if (overlap.isPresent()) {
            throw OVERLAP.create(overlap.get());
        }
        MapHistory.edit(level, "add room '" + id + "'",
                map -> map.withZone(map.zone(id).orElse(Zone.empty(id).withStart(start.orElse(false))).withBox(box)));
        ToolState.setSelection(player, Selection.NONE);
        return MapSavedData.get(level).map().zone(id).orElseThrow().boxes().size();
    }

    /** Takes the selection out of one room. A room with nothing left is removed. */
    private static int cut(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ZoneId id = roomId(ctx);
        BlockBox box = ZonesCommands.selection(player);
        ServerLevel level = player.serverLevel();
        if (existing(level, id).boxes().stream().noneMatch(box::intersects)) {
            throw NOT_IN_ROOM.create(id);
        }
        MapHistory.edit(level, "cut room '" + id + "'", map -> cut(map, id, box));
        ToolState.setSelection(player, Selection.NONE);
        return 1;
    }

    static ZombiesMap cut(ZombiesMap map, ZoneId id, BlockBox box) {
        Zone left = map.zone(id).orElseThrow().without(box);
        return left.boxes().isEmpty() ? map.withoutZone(id) : map.withZone(left);
    }

    /** {@code from}'s boxes join {@code into}, which is a start room if either was. */
    static ZombiesMap merge(ZombiesMap map, ZoneId from, ZoneId into) {
        Zone source = map.zone(from).orElseThrow();
        Zone target = map.zone(into).orElseThrow();
        return map.withoutZone(from).withZone(target.withBoxes(source.boxes()).withStart(target.start() || source.start()));
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        ZoneId id = roomId(ctx);
        existing(level, id);
        MapHistory.edit(level, "remove room '" + id + "'", map -> map.withoutZone(id));
        ctx.getSource().sendSuccess(() -> Component.literal("Removed room '" + id + "'"), false);
        return 1;
    }

    static void list(CommandContext<CommandSourceStack> ctx, ZombiesMap map) {
        for (Zone room : map.zones().values()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Room " + room.id() + (room.start() ? " [start]" : "")
                    + ": " + room.boxes().size() + " boxes, " + room.volume() + " blocks"), false);
        }
    }

    private static int setStart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        ZoneId id = roomId(ctx);
        boolean start = BoolArgumentType.getBool(ctx, "start");
        Zone room = existing(level, id);
        MapHistory.edit(level, "set room '" + id + "' start " + start, map -> map.withZone(room.withStart(start)));
        ctx.getSource().sendSuccess(() -> Component.literal("Room '" + id + "' is " + (start ? "now" : "no longer") + " a start room"), false);
        return 1;
    }

    private static int detect(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ZoneId id = roomId(ctx);
        int limit = ServerConfig.DETECT_MAX_VOLUME.get();
        ZoneIndex index = MapSavedData.get(level).index();
        Optional<ZoneId> standingIn = index.zoneAt(player.blockPosition());
        if (standingIn.isPresent()) {
            ctx.getSource().sendFailure(Component.literal("You're standing in room '" + standingIn.get()
                    + "', which is already mapped. Detect fills unmapped space: stand in the room you want to add to '" + id + "'"));
            return 0;
        }
        RoomDetector.Result result = RoomDetector.detect(player.blockPosition(), RoomDetection.openCells(level, index), limit);
        if (result instanceof RoomDetector.Found found) {
            List<BlockBox> boxes = BoxMerger.merge(found.cells());
            ToolState.setDetection(player, new ToolState.PendingDetection(level.dimension(), id, boxes));
            ctx.getSource().sendSuccess(() -> Component.literal("Detected " + found.cells().size() + " blocks in " + boxes.size()
                    + " boxes for room '" + id + "'. /hs zones confirm to add, /hs zones cancel to discard"), false);
            return boxes.size();
        }
        if (result instanceof RoomDetector.TooLarge tooLarge) {
            ctx.getSource().sendFailure(Component.literal("No closed room found within " + tooLarge.limit()
                    + " blocks. Stand inside a room with a floor, walls, and a ceiling (doors and gaps let the fill escape), or raise tools.detect_max_volume"));
        } else {
            ctx.getSource().sendFailure(Component.literal("Your feet are inside a solid block. Step into open space and try again"));
        }
        return 0;
    }

    static int confirm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ToolState.PendingDetection pending = ToolState.detection(player).orElseThrow(NO_DETECTION::create);
        ServerLevel level = player.server.getLevel(pending.level());
        if (level == null || level != player.serverLevel()) {
            ToolState.clearDetection(player);
            throw NO_DETECTION.create();
        }
        for (BlockBox box : pending.boxes()) {
            Optional<ZoneId> overlap = MapSavedData.get(level).map().zoneOverlapping(box, pending.zone());
            if (overlap.isPresent()) {
                throw OVERLAP.create(overlap.get());
            }
        }
        MapHistory.edit(level, "add detected room '" + pending.zone() + "'",
                map -> map.withZone(map.zone(pending.zone()).orElse(Zone.empty(pending.zone())).withBoxes(pending.boxes())));
        ToolState.clearDetection(player);
        return pending.boxes().size();
    }

    static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ToolState.clearDetection(ctx.getSource().getPlayerOrException());
        ctx.getSource().sendSuccess(() -> Component.literal("Detection discarded"), false);
        return 1;
    }
}
