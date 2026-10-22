package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.map.BlockBox;
import dev.candycanecazoo.hellstorm.map.DoorDef;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.map.ZoneId;
import dev.candycanecazoo.hellstorm.tools.DoorBlocks;
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * The door branches of {@code /hs zones}. A door's blocks come from the Zone Wand selection: select a box around the door
 * and add it, then add or cut more boxes until it's right.
 */
final class DoorCommands {
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(id -> Component.literal("No door named '" + id + "'"));
    private static final DynamicCommandExceptionType BAD_ID =
            new DynamicCommandExceptionType(id -> Component.literal("Invalid door name '" + id + "': use 1-32 of a-z, 0-9, _"));
    private static final DynamicCommandExceptionType NOT_IN_DOOR =
            new DynamicCommandExceptionType(id -> Component.literal("The selection holds no blocks of door '" + id + "'"));
    private static final SimpleCommandExceptionType NOTHING_ADDED = new SimpleCommandExceptionType(Component.literal(
            "The selection has no blocks to add: it's empty, they're already in this door, or they belong to other doors"));
    static final SuggestionProvider<CommandSourceStack> DOORS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(MapSavedData.get(ctx.getSource().getLevel()).map().doors().keySet(), builder);

    private DoorCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> add() {
        return Commands.literal("door").then(name()
                .executes(ctx -> add(ctx, OptionalInt.empty()))
                .then(Commands.argument("cost", IntegerArgumentType.integer(0))
                        .executes(ctx -> add(ctx, OptionalInt.of(IntegerArgumentType.getInteger(ctx, "cost"))))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> cut() {
        return Commands.literal("door").then(name().executes(DoorCommands::cut));
    }

    static LiteralArgumentBuilder<CommandSourceStack> remove() {
        return Commands.literal("door").then(name().executes(DoorCommands::remove));
    }

    static LiteralArgumentBuilder<CommandSourceStack> set() {
        return Commands.literal("door").then(name()
                .then(Commands.literal("cost").then(Commands.argument("points", IntegerArgumentType.integer(0))
                        .executes(ctx -> set(ctx, "cost", d -> d.withCost(IntegerArgumentType.getInteger(ctx, "points"))))))
                .then(Commands.literal("power").then(Commands.argument("required", BoolArgumentType.bool())
                        .executes(ctx -> set(ctx, "power", d -> d.withPower(BoolArgumentType.getBool(ctx, "required"))))))
                .then(Commands.literal("style").then(Commands.argument("style", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(DoorDef.Style.values()).map(DoorDef.Style::getSerializedName), b))
                        .executes(DoorCommands::style))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> name() {
        return Commands.argument("door", StringArgumentType.word()).suggests(DOORS);
    }

    private static DoorDef existing(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "door");
        DoorDef door = MapSavedData.get(ctx.getSource().getLevel()).map().doors().get(id);
        if (door == null) {
            throw UNKNOWN.create(id);
        }
        return door;
    }

    /** Adds the selection's blocks to the door. A new door costs {@code cost}, or the default; an existing one keeps its price. */
    private static int add(CommandContext<CommandSourceStack> ctx, OptionalInt cost) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        String id = StringArgumentType.getString(ctx, "door");
        BlockBox box = ZonesCommands.selection(player);
        ZombiesMap map = MapSavedData.get(level).map();
        DoorDef door = map.doors().get(id);
        if (door == null) {
            if (!ZoneId.isValid(id)) {
                throw BAD_ID.create(id);
            }
            door = DoorDef.empty(id, cost.orElse(GameManager.settings().doors().defaultCost()));
        }
        DoorBlocks.Result result = DoorBlocks.add(door, box, pos -> !level.getBlockState(pos).isAir(), pos -> map.doorAt(pos).map(DoorDef::id));
        if (result.changed() == 0) {
            throw NOTHING_ADDED.create();
        }
        MapHistory.edit(level, "add door '" + id + "'", m -> m.withDoor(result.door()));
        ToolState.setSelection(player, Selection.NONE);
        if (result.skipped() > 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("Skipped " + result.skipped() + " blocks in other doors or over the "
                    + DoorBlocks.MAX + "-block limit"), false);
        }
        return result.changed();
    }

    /** Takes the selection out of one door. A door with no blocks left is removed. */
    private static int cut(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        DoorDef door = existing(ctx);
        BlockBox box = ZonesCommands.selection(player);
        if (door.members().stream().noneMatch(box::contains)) {
            throw NOT_IN_DOOR.create(door.id());
        }
        MapHistory.edit(player.serverLevel(), "cut door '" + door.id() + "'", map -> cut(map, door.id(), box));
        ToolState.setSelection(player, Selection.NONE);
        return 1;
    }

    static ZombiesMap cut(ZombiesMap map, String id, BlockBox box) {
        DoorDef left = DoorBlocks.cut(map.doors().get(id), box).door();
        return left.members().isEmpty() ? map.withoutDoor(id) : map.withDoor(left);
    }

    /** {@code from}'s blocks join {@code into}, which keeps its own cost, power, and style. */
    static ZombiesMap merge(ZombiesMap map, String from, String into) {
        DoorDef target = map.doors().get(into);
        Set<BlockPos> members = new HashSet<>(target.members());
        members.addAll(map.doors().get(from).members());
        return map.withoutDoor(from).withDoor(new DoorDef(into, members, target.cost(), target.requiresPower(), target.style()));
    }

    private static int set(CommandContext<CommandSourceStack> ctx, String setting, UnaryOperator<DoorDef> change) throws CommandSyntaxException {
        DoorDef door = change.apply(existing(ctx));
        MapHistory.edit(ctx.getSource().getLevel(), "set door '" + door.id() + "' " + setting, m -> m.withDoor(door));
        ctx.getSource().sendSuccess(() -> Component.literal(summary(ctx.getSource().getLevel(), door)), false);
        return 1;
    }

    private static int style(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(ctx, "style");
        DoorDef.Style style = Arrays.stream(DoorDef.Style.values()).filter(s -> s.getSerializedName().equals(raw)).findFirst()
                .orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("Style is door or debris")).create());
        return set(ctx, "style", d -> d.withStyle(style));
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DoorDef door = existing(ctx);
        MapHistory.edit(ctx.getSource().getLevel(), "remove door '" + door.id() + "'", m -> m.withoutDoor(door.id()));
        ctx.getSource().sendSuccess(() -> Component.literal("Removed door '" + door.id() + "'. Its blocks stay in the world"), false);
        return 1;
    }

    static void list(CommandContext<CommandSourceStack> ctx, ZombiesMap map) {
        map.doors().values().forEach(d -> ctx.getSource().sendSuccess(() -> Component.literal(summary(ctx.getSource().getLevel(), d)), false));
    }

    private static String summary(ServerLevel level, DoorDef door) {
        Set<ZoneId> rooms = door.zones(MapSavedData.get(level).index());
        return "Door '" + door.id() + "': " + door.members().size() + " blocks, " + door.cost() + " points"
                + (door.requiresPower() ? ", needs power" : "") + ", " + door.style().getSerializedName()
                + ", between " + (rooms.isEmpty() ? "no rooms" : rooms);
    }
}
