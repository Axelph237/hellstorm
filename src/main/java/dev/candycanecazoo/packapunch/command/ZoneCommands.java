package dev.candycanecazoo.packapunch.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.candycanecazoo.packapunch.config.ServerConfig;
import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.BoxMerger;
import dev.candycanecazoo.packapunch.map.MapSavedData;
import dev.candycanecazoo.packapunch.map.RoomDetector;
import dev.candycanecazoo.packapunch.map.Zone;
import dev.candycanecazoo.packapunch.map.ZoneId;
import dev.candycanecazoo.packapunch.map.ZoneIndex;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.map.validate.MapValidator;
import dev.candycanecazoo.packapunch.map.validate.Problem;
import dev.candycanecazoo.packapunch.map.validate.ValidationReport;
import dev.candycanecazoo.packapunch.net.PapNetwork;
import dev.candycanecazoo.packapunch.net.ProblemsPacket;
import dev.candycanecazoo.packapunch.tools.LevelCellProbe;
import dev.candycanecazoo.packapunch.tools.MapEdits;
import dev.candycanecazoo.packapunch.tools.RoomDetection;
import dev.candycanecazoo.packapunch.tools.Selection;
import dev.candycanecazoo.packapunch.tools.ToolState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/** `/pap zone ...` and `/pap map validate`. */
final class ZoneCommands {
    private static final SimpleCommandExceptionType NO_SELECTION =
            new SimpleCommandExceptionType(Component.literal("Select two corners with the Zone Wand first"));
    private static final SimpleCommandExceptionType NO_DETECTION =
            new SimpleCommandExceptionType(Component.literal("No pending detection. Run /pap zone detect <zone> first"));
    private static final DynamicCommandExceptionType BAD_ID =
            new DynamicCommandExceptionType(id -> Component.literal("Invalid zone id '" + id + "': use 1-32 of a-z, 0-9, _"));
    private static final DynamicCommandExceptionType UNKNOWN_ZONE =
            new DynamicCommandExceptionType(id -> Component.literal("No zone named '" + id + "'"));
    private static final DynamicCommandExceptionType OVERLAP =
            new DynamicCommandExceptionType(id -> Component.literal("That box overlaps zone '" + id + "'"));

    private static final SuggestionProvider<CommandSourceStack> ZONES = (ctx, builder) -> SharedSuggestionProvider.suggest(
            MapSavedData.get(ctx.getSource().getLevel()).map().zones().keySet().stream().map(ZoneId::value), builder);

    private ZoneCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> zone() {
        return Commands.literal("zone")
                .then(Commands.literal("add").then(zoneArg().executes(ZoneCommands::add)))
                .then(Commands.literal("remove").then(zoneArg().executes(ZoneCommands::remove)))
                .then(Commands.literal("merge").then(Commands.argument("from", StringArgumentType.word()).suggests(ZONES)
                        .then(Commands.argument("into", StringArgumentType.word()).suggests(ZONES).executes(ZoneCommands::merge))))
                .then(Commands.literal("list").executes(ZoneCommands::list))
                .then(Commands.literal("start").then(zoneArg().then(Commands.argument("start", BoolArgumentType.bool()).executes(ZoneCommands::start))))
                .then(Commands.literal("detect").then(zoneArg().executes(ZoneCommands::detect)))
                .then(Commands.literal("confirm").executes(ZoneCommands::confirm))
                .then(Commands.literal("cancel").executes(ZoneCommands::cancel));
    }

    static LiteralArgumentBuilder<CommandSourceStack> map() {
        return Commands.literal("map").then(Commands.literal("validate").executes(ZoneCommands::validate));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> zoneArg() {
        return Commands.argument("zone", StringArgumentType.word()).suggests(ZONES);
    }

    private static ZoneId zoneId(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return parse(StringArgumentType.getString(ctx, "zone"));
    }

    private static ZoneId parse(String raw) throws CommandSyntaxException {
        if (!ZoneId.isValid(raw)) {
            throw BAD_ID.create(raw);
        }
        return new ZoneId(raw);
    }

    private static Zone existing(ServerLevel level, ZoneId id) throws CommandSyntaxException {
        return MapSavedData.get(level).map().zone(id).orElseThrow(() -> UNKNOWN_ZONE.create(id));
    }

    private static int add(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ZoneId id = zoneId(ctx);
        BlockBox box = ToolState.selection(player).box().orElseThrow(NO_SELECTION::create);
        ServerLevel level = player.serverLevel();
        Optional<ZoneId> overlap = MapSavedData.get(level).map().zoneOverlapping(box, id);
        if (overlap.isPresent()) {
            throw OVERLAP.create(overlap.get());
        }
        boolean existed = MapSavedData.get(level).map().zone(id).isPresent();
        MapEdits.apply(level, map -> map.withZone(map.zone(id).orElse(Zone.empty(id)).withBox(box)));
        ToolState.setSelection(player, Selection.NONE);
        int boxes = MapSavedData.get(level).map().zone(id).orElseThrow().boxes().size();
        String size = box.sizeX() + "x" + box.sizeY() + "x" + box.sizeZ();
        ctx.getSource().sendSuccess(() -> Component.literal(existed
                ? "Added a " + size + " box to zone '" + id + "'. It now has " + boxes + " boxes, treated as one zone"
                : "Created zone '" + id + "' from a " + size + " box. Run /pap zone add " + id + " again to extend it with more boxes"), false);
        return boxes;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        ZoneId id = zoneId(ctx);
        existing(level, id);
        MapEdits.apply(level, map -> map.withoutZone(id));
        ctx.getSource().sendSuccess(() -> Component.literal("Removed zone '" + id + "'"), false);
        return 1;
    }

    private static int merge(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        ZoneId from = parse(StringArgumentType.getString(ctx, "from"));
        ZoneId into = parse(StringArgumentType.getString(ctx, "into"));
        if (from.equals(into)) {
            ctx.getSource().sendFailure(Component.literal("Pick two different zones"));
            return 0;
        }
        Zone source = existing(level, from);
        Zone target = existing(level, into);
        MapEdits.apply(level, map -> map.withoutZone(from)
                .withZone(target.withBoxes(source.boxes()).withStart(target.start() || source.start())));
        ctx.getSource().sendSuccess(() -> Component.literal("Merged zone '" + from + "' into '" + into + "' ("
                + (target.boxes().size() + source.boxes().size()) + " boxes)"), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        ZombiesMap map = MapSavedData.get(ctx.getSource().getLevel()).map();
        if (map.zones().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No zones yet. Select corners with the Zone Wand, then /pap zone add <zone>"), false);
            return 0;
        }
        for (Zone zone : map.zones().values()) {
            ctx.getSource().sendSuccess(() -> Component.literal(zone.id() + (zone.start() ? " [start]" : "")
                    + ": " + zone.boxes().size() + " boxes, " + zone.volume() + " blocks"), false);
        }
        return map.zones().size();
    }

    private static int start(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        ZoneId id = zoneId(ctx);
        boolean start = BoolArgumentType.getBool(ctx, "start");
        Zone zone = existing(level, id);
        MapEdits.apply(level, map -> map.withZone(zone.withStart(start)));
        ctx.getSource().sendSuccess(() -> Component.literal("Zone '" + id + "' is " + (start ? "now" : "no longer") + " a start zone"), false);
        return 1;
    }

    private static int detect(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ZoneId id = zoneId(ctx);
        int limit = ServerConfig.DETECT_MAX_VOLUME.get();
        ZoneIndex index = MapSavedData.get(level).index();
        Optional<ZoneId> standingIn = index.zoneAt(player.blockPosition());
        if (standingIn.isPresent()) {
            ctx.getSource().sendFailure(Component.literal("You're standing in zone '" + standingIn.get()
                    + "', which is already mapped. Detect fills unmapped space: stand in the room you want to add to '" + id + "'"));
            return 0;
        }
        RoomDetector.Result result = RoomDetector.detect(player.blockPosition(), RoomDetection.openCells(level, index), limit);
        if (result instanceof RoomDetector.Found found) {
            List<BlockBox> boxes = BoxMerger.merge(found.cells());
            ToolState.setDetection(player, new ToolState.PendingDetection(level.dimension(), id, boxes));
            ctx.getSource().sendSuccess(() -> Component.literal("Detected " + found.cells().size() + " blocks in " + boxes.size()
                    + " boxes for zone '" + id + "'. /pap zone confirm to add, /pap zone cancel to discard"), false);
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

    private static int confirm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
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
        MapEdits.apply(level, map -> map.withZone(map.zone(pending.zone()).orElse(Zone.empty(pending.zone())).withBoxes(pending.boxes())));
        ToolState.clearDetection(player);
        ctx.getSource().sendSuccess(() -> Component.literal("Added " + pending.boxes().size() + " boxes to zone '" + pending.zone() + "'"), false);
        return pending.boxes().size();
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ToolState.clearDetection(ctx.getSource().getPlayerOrException());
        ctx.getSource().sendSuccess(() -> Component.literal("Detection discarded"), false);
        return 1;
    }

    private static int validate(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        ValidationReport report = MapValidator.validate(MapSavedData.get(level).map(), dev.candycanecazoo.packapunch.game.GameManager.validationContext(level));
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            PapNetwork.send(player, new ProblemsPacket(report.problems()));
        }
        if (report.problems().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Map is valid").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        for (Problem problem : report.problems()) {
            MutableComponent line = Component.literal(problem.severity() == Problem.Severity.ERROR ? "ERROR " : "WARNING ")
                    .withStyle(problem.severity() == Problem.Severity.ERROR ? ChatFormatting.RED : ChatFormatting.GOLD)
                    .append(Component.literal(problem.message()).withStyle(ChatFormatting.WHITE));
            problem.pos().ifPresent(pos -> line.append(Component.literal(" at " + pos.toShortString()).withStyle(ChatFormatting.GRAY)));
            ctx.getSource().sendSuccess(() -> line, false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(report.playable() ? "Playable, with warnings" : "Not playable")
                .withStyle(report.playable() ? ChatFormatting.GOLD : ChatFormatting.RED), false);
        return report.playable() ? 1 : 0;
    }
}
