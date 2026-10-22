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
import dev.candycanecazoo.hellstorm.tools.Selection;
import dev.candycanecazoo.hellstorm.tools.MapEdits;
import dev.candycanecazoo.hellstorm.tools.ToolState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * {@code /hs door create|add|cut|set|remove|list}. A door's blocks come from the Zone Wand selection: select a box around
 * the door and create it, then add or cut more boxes until it's right.
 */
final class DoorCommands {
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(id -> Component.literal("No door named '" + id + "'"));
    private static final DynamicCommandExceptionType EXISTS = new DynamicCommandExceptionType(id -> Component.literal("Door '" + id + "' already exists"));
    private static final DynamicCommandExceptionType BAD_ID =
            new DynamicCommandExceptionType(id -> Component.literal("Invalid door id '" + id + "': use 1-32 of a-z, 0-9, _"));
    private static final SimpleCommandExceptionType NO_SELECTION =
            new SimpleCommandExceptionType(Component.literal("Select a box around the door's blocks with the Zone Wand first"));
    private static final SimpleCommandExceptionType NOTHING_ADDED = new SimpleCommandExceptionType(Component.literal(
            "The selection has no blocks to add: it's empty, they're already in this door, or they belong to other doors"));
    private static final SuggestionProvider<CommandSourceStack> DOORS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(MapSavedData.get(ctx.getSource().getLevel()).map().doors().keySet(), builder);

    private DoorCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> door() {
        return Commands.literal("door")
                .then(Commands.literal("create").then(id().executes(ctx -> create(ctx, -1))
                        .then(Commands.argument("cost", IntegerArgumentType.integer(0)).executes(ctx -> create(ctx, IntegerArgumentType.getInteger(ctx, "cost"))))))
                .then(Commands.literal("add").then(existing().executes(DoorCommands::add)))
                .then(Commands.literal("cut").then(existing().executes(DoorCommands::cut)))
                .then(Commands.literal("set").then(existing()
                        .then(Commands.literal("cost").then(Commands.argument("points", IntegerArgumentType.integer(0))
                                .executes(ctx -> set(ctx, d -> d.withCost(IntegerArgumentType.getInteger(ctx, "points"))))))
                        .then(Commands.literal("power").then(Commands.argument("required", BoolArgumentType.bool())
                                .executes(ctx -> set(ctx, d -> d.withPower(BoolArgumentType.getBool(ctx, "required"))))))
                        .then(Commands.literal("style").then(Commands.argument("style", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(DoorDef.Style.values()).map(DoorDef.Style::getSerializedName), b))
                                .executes(DoorCommands::style)))))
                .then(Commands.literal("remove").then(existing().executes(DoorCommands::remove)))
                .then(Commands.literal("list").executes(DoorCommands::list));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> id() {
        return Commands.argument("door", StringArgumentType.word());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> existing() {
        return id().suggests(DOORS);
    }

    private static DoorDef existing(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String id = StringArgumentType.getString(ctx, "door");
        DoorDef door = MapSavedData.get(ctx.getSource().getLevel()).map().doors().get(id);
        if (door == null) {
            throw UNKNOWN.create(id);
        }
        return door;
    }

    private static int create(CommandContext<CommandSourceStack> ctx, int cost) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = StringArgumentType.getString(ctx, "door");
        if (!ZoneId.isValid(id)) {
            throw BAD_ID.create(id);
        }
        if (MapSavedData.get(player.serverLevel()).map().doors().containsKey(id)) {
            throw EXISTS.create(id);
        }
        int price = cost >= 0 ? cost : GameManager.settings().doors().defaultCost();
        return addSelection(ctx, player, DoorDef.empty(id, price));
    }

    private static int add(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return addSelection(ctx, ctx.getSource().getPlayerOrException(), existing(ctx));
    }

    private static int addSelection(CommandContext<CommandSourceStack> ctx, ServerPlayer player, DoorDef door) throws CommandSyntaxException {
        ServerLevel level = player.serverLevel();
        BlockBox box = ToolState.selection(player).box().orElseThrow(NO_SELECTION::create);
        ZombiesMap map = MapSavedData.get(level).map();
        DoorBlocks.Result result = DoorBlocks.add(door, box, pos -> !level.getBlockState(pos).isAir(), pos -> map.doorAt(pos).map(DoorDef::id));
        if (result.changed() == 0) {
            throw NOTHING_ADDED.create();
        }
        MapEdits.apply(level, m -> m.withDoor(result.door()));
        ToolState.setSelection(player, Selection.NONE);
        if (result.skipped() > 0) {
            ctx.getSource().sendSuccess(() -> Component.literal("Skipped " + result.skipped() + " blocks in other doors or over the "
                    + DoorBlocks.MAX + "-block limit"), false);
        }
        return result.changed();
    }

    private static int cut(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockBox box = ToolState.selection(player).box().orElseThrow(NO_SELECTION::create);
        DoorBlocks.Result result = DoorBlocks.cut(existing(ctx), box);
        MapEdits.apply(level, m -> m.withDoor(result.door()));
        ToolState.setSelection(player, Selection.NONE);
        ctx.getSource().sendSuccess(() -> Component.literal("Took " + result.changed() + " blocks out of '" + result.door().id() + "'"
                + (result.door().members().isEmpty() ? ". It has no blocks left: add some, or /hs door remove " + result.door().id() : ". " + summary(level, result.door()))), false);
        return result.changed();
    }

    private static int set(CommandContext<CommandSourceStack> ctx, UnaryOperator<DoorDef> change) throws CommandSyntaxException {
        DoorDef door = change.apply(existing(ctx));
        MapEdits.apply(ctx.getSource().getLevel(), m -> m.withDoor(door));
        ctx.getSource().sendSuccess(() -> Component.literal(summary(ctx.getSource().getLevel(), door)), false);
        return 1;
    }

    private static int style(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String raw = StringArgumentType.getString(ctx, "style");
        DoorDef.Style style = Arrays.stream(DoorDef.Style.values()).filter(s -> s.getSerializedName().equals(raw)).findFirst()
                .orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("Style is door or debris")).create());
        return set(ctx, d -> d.withStyle(style));
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DoorDef door = existing(ctx);
        MapEdits.apply(ctx.getSource().getLevel(), m -> m.withoutDoor(door.id()));
        ctx.getSource().sendSuccess(() -> Component.literal("Removed door '" + door.id() + "'. Its blocks stay in the world"), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var doors = MapSavedData.get(ctx.getSource().getLevel()).map().doors().values();
        if (doors.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No doors yet. Select a box around a door with the Zone Wand, then /hs door create <id> [cost]"), false);
        }
        doors.forEach(d -> ctx.getSource().sendSuccess(() -> Component.literal(summary(ctx.getSource().getLevel(), d)), false));
        return doors.size();
    }

    private static String summary(ServerLevel level, DoorDef door) {
        Set<ZoneId> zones = door.zones(MapSavedData.get(level).index());
        return "Door '" + door.id() + "': " + door.members().size() + " blocks, " + door.cost() + " points"
                + (door.requiresPower() ? ", needs power" : "") + ", " + door.style().getSerializedName()
                + ", between " + (zones.isEmpty() ? "no zones" : zones);
    }
}
