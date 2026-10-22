package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.candycanecazoo.hellstorm.config.ServerConfig;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.Passages;
import dev.candycanecazoo.hellstorm.map.ZoneIndex;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.map.validate.MapValidator;
import dev.candycanecazoo.hellstorm.map.validate.Problem;
import dev.candycanecazoo.hellstorm.map.validate.ValidationReport;
import dev.candycanecazoo.hellstorm.mob.path.Portal;
import dev.candycanecazoo.hellstorm.net.PapNetwork;
import dev.candycanecazoo.hellstorm.net.ProblemsPacket;
import dev.candycanecazoo.hellstorm.tools.LevelCellProbe;
import dev.candycanecazoo.hellstorm.tools.PathDebug;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/** `/hs map validate|paths|export|import|packs|mode`. */
final class MapCommands {
    private MapCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> map() {
        return Commands.literal("map")
                .then(Commands.literal("validate").executes(MapCommands::validate))
                .then(Commands.literal("paths").executes(MapCommands::paths))
                .then(MapPackCommands.export())
                .then(MapPackCommands.importPack())
                .then(MapPackCommands.packs())
                .then(MapPackCommands.mode());
    }

    /** Which room connections the pathfinder can use for a normal-sized zombie, and through which cell. */
    private static int paths(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        ZombiesMap map = MapSavedData.get(level).map();
        Passages.Result passages = Passages.find(map, ZoneIndex.of(map), LevelCellProbe.of(level), ServerConfig.MAX_PASSAGE_GAP.get());
        List<Portal> portals = PathDebug.portals(level, map);
        if (passages.open().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No room connects to another"), false);
        }
        for (Passages.Link link : passages.open()) {
            Optional<Portal> portal = portals.stream().filter(p -> Passages.Link.of(p.zoneA(), p.zoneB()).equals(link)).findFirst();
            ctx.getSource().sendSuccess(() -> portal.map(p -> Component.literal(link.a() + " <-> " + link.b() + ": through "
                            + (p.cellA().equals(p.cellB()) ? "doorway " + p.cellA().toShortString() : p.cellA().toShortString() + " / " + p.cellB().toShortString()))
                    .withStyle(ChatFormatting.GREEN))
                    .orElse(Component.literal(link.a() + " <-> " + link.b() + ": connected, but no walkable 1x2 crossing (too low, too narrow, or no floor)")
                            .withStyle(ChatFormatting.RED)), false);
        }
        passages.closedDoors().forEach((link, door) -> ctx.getSource().sendSuccess(() -> Component.literal(link.a() + " <-> " + link.b()
                + ": only through a closed door at " + door.toShortString()).withStyle(ChatFormatting.GOLD), false));
        return portals.size();
    }

    private static int validate(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        ValidationReport report = MapValidator.validate(MapSavedData.get(level).map(), GameManager.validationContext(level));
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
