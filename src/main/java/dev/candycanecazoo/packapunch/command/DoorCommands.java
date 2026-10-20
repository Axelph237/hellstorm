package dev.candycanecazoo.packapunch.command;

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
import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.DoorDef;
import dev.candycanecazoo.packapunch.map.MapSavedData;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.tools.DoorWandItem;
import dev.candycanecazoo.packapunch.tools.MapEdits;
import dev.candycanecazoo.packapunch.tools.ToolState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * {@code /pap door create|select|fill|set|remove|list}. Doors are picked block by block with the Door Wand; {@code fill}
 * adds every block in the Zone Wand selection at once.
 */
final class DoorCommands {
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(id -> Component.literal("No door named '" + id + "'"));
    private static final DynamicCommandExceptionType EXISTS = new DynamicCommandExceptionType(id -> Component.literal("Door '" + id + "' already exists"));
    private static final DynamicCommandExceptionType BAD_ID =
            new DynamicCommandExceptionType(id -> Component.literal("Invalid door id '" + id + "': use 1-32 of a-z, 0-9, _"));
    private static final SimpleCommandExceptionType NOT_EDITING =
            new SimpleCommandExceptionType(Component.literal("Not editing a door. /pap door create <id> or /pap door select <id>"));
    private static final SimpleCommandExceptionType NO_SELECTION = new SimpleCommandExceptionType(Component.literal("Select two corners with the Zone Wand first"));
    private static final SuggestionProvider<CommandSourceStack> DOORS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(MapSavedData.get(ctx.getSource().getLevel()).map().doors().keySet(), builder);

    private DoorCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> door() {
        return Commands.literal("door")
                .then(Commands.literal("create").then(id().executes(ctx -> create(ctx, -1))
                        .then(Commands.argument("cost", IntegerArgumentType.integer(0)).executes(ctx -> create(ctx, IntegerArgumentType.getInteger(ctx, "cost"))))))
                .then(Commands.literal("select").then(existing().executes(DoorCommands::select)))
                .then(Commands.literal("fill").executes(DoorCommands::fill))
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
        int price = cost >= 0 ? cost : dev.candycanecazoo.packapunch.game.GameManager.settings().doors().defaultCost();
        MapEdits.apply(player.serverLevel(), m -> m.withDoor(DoorDef.empty(id, price)));
        ToolState.setDoor(player, Optional.of(id));
        ctx.getSource().sendSuccess(() -> Component.literal("Created door '" + id + "' costing " + price
                + ". Right-click its blocks with the Door Wand, or /pap door fill to add the Zone Wand selection"), false);
        return 1;
    }

    private static int select(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        DoorDef door = existing(ctx);
        ToolState.setDoor(ctx.getSource().getPlayerOrException(), Optional.of(door.id()));
        ctx.getSource().sendSuccess(() -> Component.literal("Editing door '" + door.id() + "'"), false);
        return 1;
    }

    private static int fill(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        String id = ToolState.door(player).orElseThrow(NOT_EDITING::create);
        BlockBox box = ToolState.selection(player).box().orElseThrow(NO_SELECTION::create);
        DoorDef door = Optional.ofNullable(MapSavedData.get(level).map().doors().get(id)).orElseThrow(NOT_EDITING::create);
        int added = 0;
        int skipped = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.min(), box.max())) {
            if (level.getBlockState(pos).isAir() || door.members().contains(pos)) {
                continue;
            }
            if (door.members().size() >= DoorWandItem.MAX_MEMBERS || MapSavedData.get(level).map().doorAt(pos).isPresent()) {
                skipped++;
                continue;
            }
            door = door.withMember(pos);
            added++;
        }
        DoorDef result = door;
        MapEdits.apply(level, m -> m.withDoor(result));
        int finalAdded = added;
        int finalSkipped = skipped;
        ctx.getSource().sendSuccess(() -> Component.literal("Added " + finalAdded + " blocks to '" + id + "' (" + result.members().size() + " total)"
                + (finalSkipped > 0 ? ". Skipped " + finalSkipped + " in other doors or over the " + DoorWandItem.MAX_MEMBERS + "-block limit" : "")), false);
        return added;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, java.util.function.UnaryOperator<DoorDef> change) throws CommandSyntaxException {
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
            ctx.getSource().sendSuccess(() -> Component.literal("No doors yet. /pap door create <id>"), false);
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
