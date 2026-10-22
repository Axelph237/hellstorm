package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.DoorDef;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.Zone;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.tools.MapHistory;
import dev.candycanecazoo.hellstorm.tools.Selection;
import dev.candycanecazoo.hellstorm.tools.ToolState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code /hs zones ...}: the rooms, doors, and windows of a level's map. Every edit here goes into {@link MapHistory}, so
 * {@code /hs undo} takes it back.
 */
final class ZonesCommands {
    private static final SimpleCommandExceptionType NO_SELECTION =
            new SimpleCommandExceptionType(Component.literal("Select two corners with the Zone Wand first"));
    private static final SimpleCommandExceptionType CUT_NOTHING =
            new SimpleCommandExceptionType(Component.literal("The selection doesn't touch any room or door"));
    private static final SimpleCommandExceptionType MERGE_NOTHING = new SimpleCommandExceptionType(Component.literal(
            "The selection touches fewer than two rooms and fewer than two doors, so there's nothing to merge"));
    private static final SimpleCommandExceptionType MERGE_WHAT = new SimpleCommandExceptionType(Component.literal(
            "Select the rooms or doors to merge with the Zone Wand, or name them: /hs zones merge <from> <into>"));
    private static final SimpleCommandExceptionType MERGE_SAME =
            new SimpleCommandExceptionType(Component.literal("Pick two different rooms or doors"));
    private static final SimpleCommandExceptionType MERGE_MISMATCH =
            new SimpleCommandExceptionType(Component.literal("Those aren't two rooms or two doors"));

    private static final SuggestionProvider<CommandSourceStack> ROOMS_AND_DOORS = (ctx, builder) -> {
        ZombiesMap map = MapSavedData.get(ctx.getSource().getLevel()).map();
        return SharedSuggestionProvider.suggest(Stream.concat(map.zones().keySet().stream().map(ZoneId::value), map.doors().keySet().stream()), builder);
    };

    private ZonesCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> zones() {
        return Commands.literal("zones")
                .then(Commands.literal("add").then(RoomCommands.add()).then(DoorCommands.add()).then(WindowCommands.add()))
                .then(Commands.literal("cut").executes(ZonesCommands::cut).then(RoomCommands.cut()).then(DoorCommands.cut()))
                .then(Commands.literal("merge").executes(ZonesCommands::merge)
                        .then(Commands.argument("from", StringArgumentType.word()).suggests(ROOMS_AND_DOORS)
                                .then(Commands.argument("into", StringArgumentType.word()).suggests(ROOMS_AND_DOORS).executes(ZonesCommands::mergeNamed))))
                .then(Commands.literal("remove").then(RoomCommands.remove()).then(DoorCommands.remove()).then(WindowCommands.remove()))
                .then(Commands.literal("set").then(RoomCommands.set()).then(DoorCommands.set()))
                .then(Commands.literal("list").executes(ZonesCommands::list))
                .then(RoomCommands.detect())
                .then(Commands.literal("confirm").executes(RoomCommands::confirm))
                .then(Commands.literal("cancel").executes(ZonesCommands::cancel));
    }

    /** The selection's boxes; there's at least one. */
    static List<BlockBox> selection(ServerPlayer player) throws CommandSyntaxException {
        Selection selection = ToolState.selection(player);
        if (selection.isEmpty()) {
            throw NO_SELECTION.create();
        }
        return selection.boxes();
    }

    static boolean touches(List<BlockBox> boxes, BlockBox box) {
        return boxes.stream().anyMatch(box::intersects);
    }

    static boolean holds(List<BlockBox> boxes, BlockPos pos) {
        return boxes.stream().anyMatch(b -> b.contains(pos));
    }

    private static List<ZoneId> roomsTouching(ZombiesMap map, List<BlockBox> boxes) {
        return map.zones().values().stream().filter(z -> z.boxes().stream().anyMatch(b -> touches(boxes, b))).map(Zone::id).toList();
    }

    private static List<String> doorsTouching(ZombiesMap map, List<BlockBox> boxes) {
        return map.doors().values().stream().filter(d -> d.members().stream().anyMatch(p -> holds(boxes, p))).map(DoorDef::id).toList();
    }

    /** Takes the selection out of every room and door it touches. */
    private static int cut(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<BlockBox> boxes = selection(player);
        ServerLevel level = player.serverLevel();
        ZombiesMap map = MapSavedData.get(level).map();
        List<ZoneId> rooms = roomsTouching(map, boxes);
        List<String> doors = doorsTouching(map, boxes);
        if (rooms.isEmpty() && doors.isEmpty()) {
            throw CUT_NOTHING.create();
        }
        MapHistory.edit(level, "cut", m -> {
            for (ZoneId room : rooms) {
                m = RoomCommands.cut(m, room, boxes);
            }
            for (String door : doors) {
                m = DoorCommands.cut(m, door, boxes);
            }
            return m;
        });
        return rooms.size() + doors.size();
    }

    /**
     * With a selection, merges the rooms it touches into the biggest of them, and the doors it touches into the one with the
     * most blocks. Without one, says how to name them instead.
     */
    private static int merge(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Selection selection = ToolState.selection(player);
        if (selection.isEmpty()) {
            throw MERGE_WHAT.create();
        }
        ServerLevel level = player.serverLevel();
        ZombiesMap map = MapSavedData.get(level).map();
        List<ZoneId> rooms = roomsTouching(map, selection.boxes());
        List<String> doors = doorsTouching(map, selection.boxes());
        if (rooms.size() < 2 && doors.size() < 2) {
            throw MERGE_NOTHING.create();
        }
        ZoneId roomInto = rooms.stream().max(Comparator.comparingLong(id -> map.zones().get(id).volume())).orElse(null);
        String doorInto = doors.stream().max(Comparator.comparingInt(id -> map.doors().get(id).members().size())).orElse(null);
        MapHistory.edit(level, "merge", m -> {
            for (ZoneId room : rooms) {
                if (!room.equals(roomInto)) {
                    m = RoomCommands.merge(m, room, roomInto);
                }
            }
            for (String door : doors) {
                if (!door.equals(doorInto)) {
                    m = DoorCommands.merge(m, door, doorInto);
                }
            }
            return m;
        });
        return 1;
    }

    /** Merges {@code from} into {@code into}: two rooms, or failing that two doors. */
    private static int mergeNamed(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        String from = StringArgumentType.getString(ctx, "from");
        String into = StringArgumentType.getString(ctx, "into");
        if (from.equals(into)) {
            throw MERGE_SAME.create();
        }
        ZombiesMap map = MapSavedData.get(level).map();
        if (ZoneId.isValid(from) && ZoneId.isValid(into) && map.zones().containsKey(new ZoneId(from)) && map.zones().containsKey(new ZoneId(into))) {
            MapHistory.edit(level, "merge room '" + from + "' into '" + into + "'", m -> RoomCommands.merge(m, new ZoneId(from), new ZoneId(into)));
        } else if (map.doors().containsKey(from) && map.doors().containsKey(into)) {
            MapHistory.edit(level, "merge door '" + from + "' into '" + into + "'", m -> DoorCommands.merge(m, from, into));
        } else {
            throw MERGE_MISMATCH.create();
        }
        return 1;
    }

    /** Clears the Zone Wand selection and any pending room detection. */
    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean detecting = ToolState.detection(player).isPresent();
        ToolState.setSelection(player, Selection.NONE);
        ToolState.clearDetection(player);
        ctx.getSource().sendSuccess(() -> Component.literal(detecting ? "Selection cleared and detection discarded" : "Selection cleared"), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ZombiesMap map = MapSavedData.get(ctx.getSource().getLevel()).map();
        if (map.zones().isEmpty() && map.doors().isEmpty() && map.windows().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No rooms, doors, or windows yet. Select corners with the Zone Wand, "
                    + "then /hs zones add <room|window|door> <name>"), false);
            return 0;
        }
        RoomCommands.list(ctx, map);
        DoorCommands.list(ctx, map);
        WindowCommands.list(ctx, map);
        return map.zones().size() + map.doors().size() + map.windows().size();
    }
}
