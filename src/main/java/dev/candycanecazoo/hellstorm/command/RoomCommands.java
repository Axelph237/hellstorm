package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
    private static CommandErrors.Failure noDetection() {
        return CommandErrors.fail("No pending detection. Run {} first", "/hs zones detect <room>");
    }

    private static CommandErrors.Failure unknown(ZoneId id) {
        return CommandErrors.fail("No room named {}", id);
    }

    private static CommandErrors.Failure overlap(ZoneId id) {
        return CommandErrors.fail("That box overlaps room {}", id);
    }

    static final SuggestionProvider<CommandSourceStack> ROOMS = (ctx, builder) -> SharedSuggestionProvider.suggest(
            MapSavedData.get(ctx.getSource().getLevel()).map().zones().keySet().stream().map(ZoneId::value), builder);

    private RoomCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> add() {
        return Commands.literal("room").then(name()
                .executes(CommandErrors.titled("Unable to add room", ctx -> add(ctx, Optional.empty())))
                .then(Commands.argument("start", BoolArgumentType.bool())
                        .executes(CommandErrors.titled("Unable to add room", ctx -> add(ctx, Optional.of(BoolArgumentType.getBool(ctx, "start")))))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> cut() {
        return Commands.literal("room").then(name().executes(CommandErrors.titled("Unable to cut room", RoomCommands::cut)));
    }

    static LiteralArgumentBuilder<CommandSourceStack> remove() {
        return Commands.literal("room").then(name().executes(CommandErrors.titled("Unable to remove room", RoomCommands::remove)));
    }

    static LiteralArgumentBuilder<CommandSourceStack> set() {
        return Commands.literal("room").then(name()
                .then(Commands.literal("start").then(Commands.argument("start", BoolArgumentType.bool()).executes(CommandErrors.titled("Unable to set room start", RoomCommands::setStart)))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> detect() {
        return Commands.literal("detect").then(name().executes(CommandErrors.titled("Unable to detect room", RoomCommands::detect)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> name() {
        return Commands.argument("room", StringArgumentType.word()).suggests(ROOMS);
    }

    private static ZoneId roomId(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return parse(StringArgumentType.getString(ctx, "room"));
    }

    static ZoneId parse(String raw) throws CommandSyntaxException {
        if (!ZoneId.isValid(raw)) {
            throw CommandErrors.fail("Invalid room name {}: use {}", raw, "1-32 of a-z, 0-9, _");
        }
        return new ZoneId(raw);
    }

    private static Zone existing(ServerLevel level, ZoneId id) throws CommandSyntaxException {
        return MapSavedData.get(level).map().zone(id).orElseThrow(() -> unknown(id));
    }

    /** Adds the selection to the room. A new room starts with {@code start}; an existing one keeps its own. */
    private static int add(CommandContext<CommandSourceStack> ctx, Optional<Boolean> start) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ZoneId id = roomId(ctx);
        List<BlockBox> boxes = ZonesCommands.selection(player);
        ServerLevel level = player.serverLevel();
        for (BlockBox box : boxes) {
            Optional<ZoneId> overlap = MapSavedData.get(level).map().zoneOverlapping(box, id);
            if (overlap.isPresent()) {
                throw overlap(overlap.get());
            }
        }
        MapHistory.edit(level, "add room '" + id + "'",
                map -> map.withZone(map.zone(id).orElse(Zone.empty(id).withStart(start.orElse(false))).withBoxes(boxes)));
        ZonesCommands.used(player);
        return MapSavedData.get(level).map().zone(id).orElseThrow().boxes().size();
    }

    /** Takes the selection out of one room. A room with nothing left is removed. */
    private static int cut(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ZoneId id = roomId(ctx);
        List<BlockBox> boxes = ZonesCommands.selection(player);
        ServerLevel level = player.serverLevel();
        if (existing(level, id).boxes().stream().noneMatch(b -> ZonesCommands.touches(boxes, b))) {
            throw CommandErrors.fail("The selection doesn't touch room {}", id);
        }
        MapHistory.edit(level, "cut room '" + id + "'", map -> cut(map, id, boxes));
        ZonesCommands.used(player);
        return 1;
    }

    static ZombiesMap cut(ZombiesMap map, ZoneId id, List<BlockBox> boxes) {
        Zone left = map.zone(id).orElseThrow();
        for (BlockBox box : boxes) {
            left = left.without(box);
        }
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
            throw CommandErrors.fail("You're standing in room {}, which is already mapped. Detect fills unmapped space: stand in the room you want to add to {}",
                    standingIn.get(), id);
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
            throw CommandErrors.fail("No closed room found within {} blocks. Stand inside a room with a floor, walls, and a ceiling (doors and gaps let the fill escape), or raise {}",
                    tooLarge.limit(), "tools.detect_max_volume");
        }
        throw CommandErrors.fail("Your feet are inside a solid block. Step into open space and try again");
    }

    static int confirm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ToolState.PendingDetection pending = ToolState.detection(player).orElseThrow(RoomCommands::noDetection);
        ServerLevel level = player.server.getLevel(pending.level());
        if (level == null || level != player.serverLevel()) {
            ToolState.clearDetection(player);
            throw noDetection();
        }
        for (BlockBox box : pending.boxes()) {
            Optional<ZoneId> overlap = MapSavedData.get(level).map().zoneOverlapping(box, pending.zone());
            if (overlap.isPresent()) {
                throw overlap(overlap.get());
            }
        }
        MapHistory.edit(level, "add detected room '" + pending.zone() + "'",
                map -> map.withZone(map.zone(pending.zone()).orElse(Zone.empty(pending.zone())).withBoxes(pending.boxes())));
        ToolState.clearDetection(player);
        return pending.boxes().size();
    }

}
