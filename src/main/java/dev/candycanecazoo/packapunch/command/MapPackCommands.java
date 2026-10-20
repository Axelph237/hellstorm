package dev.candycanecazoo.packapunch.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.candycanecazoo.packapunch.config.ServerConfig;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.map.BlockBox;
import dev.candycanecazoo.packapunch.map.MapGeometry;
import dev.candycanecazoo.packapunch.map.MapSavedData;
import dev.candycanecazoo.packapunch.map.ZombiesMap;
import dev.candycanecazoo.packapunch.map.validate.MapValidator;
import dev.candycanecazoo.packapunch.map.validate.Problem;
import dev.candycanecazoo.packapunch.map.validate.ValidationReport;
import dev.candycanecazoo.packapunch.tools.MapPack;
import dev.candycanecazoo.packapunch.tools.ToolState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * {@code /pap map export <name> [force]}, {@code /pap map import <name> [<pos>] [replace]}, {@code /pap map packs}: share a
 * map as one file in {@code packapunch/maps/} of the game folder (the server folder on a server).
 */
final class MapPackCommands {
    private static final SuggestionProvider<CommandSourceStack> PACKS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(available(), builder);

    private MapPackCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> export() {
        return Commands.literal("export").then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> export(ctx, false))
                .then(Commands.literal("force").executes(ctx -> export(ctx, true))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> importPack() {
        return Commands.literal("import").then(Commands.argument("name", StringArgumentType.word()).suggests(PACKS)
                .executes(ctx -> importPack(ctx, Optional.empty(), false))
                .then(Commands.literal("replace").executes(ctx -> importPack(ctx, Optional.empty(), true)))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> importPack(ctx, Optional.of(BlockPosArgument.getBlockPos(ctx, "pos")), false))
                        .then(Commands.literal("replace").executes(ctx -> importPack(ctx, Optional.of(BlockPosArgument.getBlockPos(ctx, "pos")), true)))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> packs() {
        return Commands.literal("packs").executes(ctx -> {
            List<String> names = available();
            ctx.getSource().sendSuccess(() -> Component.literal(names.isEmpty() ? "No map packs in " + folder()
                    : "Map packs in " + folder() + ": " + String.join(", ", names)), false);
            return names.size();
        });
    }

    static Path folder() {
        return FMLPaths.GAMEDIR.get().resolve("packapunch").resolve("maps");
    }

    private static List<String> available() {
        try {
            return MapPack.available(folder());
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String name(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        if (!MapPack.NAME.matcher(name).matches()) {
            throw Targets.fail("A map pack name is 1 to 32 lowercase letters, digits, _ or -");
        }
        return name;
    }

    private static void notWhilePlaying(ServerLevel level) throws CommandSyntaxException {
        if (GameManager.get(level).isPresent()) {
            throw Targets.fail("A game is running here. /pap stop first");
        }
    }

    private static int export(CommandContext<CommandSourceStack> ctx, boolean force) throws CommandSyntaxException {
        String name = name(ctx);
        ServerLevel level = ctx.getSource().getLevel();
        notWhilePlaying(level);
        ZombiesMap map = MapSavedData.get(level).map();
        ValidationReport report = MapValidator.validate(map, GameManager.validationContext(level));
        if (!report.playable() && !force) {
            report.problems().stream().filter(p -> p.severity() == Problem.Severity.ERROR)
                    .forEach(p -> ctx.getSource().sendFailure(Component.literal(" - " + p.message())));
            throw Targets.fail("The map has errors (above). Fix them, or export anyway with /pap map export " + name + " force");
        }
        Optional<BlockBox> selected = ctx.getSource().getEntity() instanceof ServerPlayer player ? ToolState.selection(player).box() : Optional.empty();
        Optional<BlockBox> area = selected.or(() -> MapGeometry.bounds(map).map(b -> b.grown(ServerConfig.EXPORT_MARGIN.get())));
        if (area.isEmpty()) {
            throw Targets.fail("There's nothing to export: the map is empty and nothing is selected with the Zone Wand");
        }
        Path datapack = level.getServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve(name);
        Path file = folder().resolve(name + ".zip");
        try {
            MapPack.export(level, name, area.get(), map, Optional.of(datapack), file);
        } catch (IOException e) {
            throw Targets.fail("Couldn't write " + file + ": " + e.getMessage());
        }
        BlockBox box = area.get();
        ctx.getSource().sendSuccess(() -> Component.literal("Exported " + box.sizeX() + "x" + box.sizeY() + "x" + box.sizeZ() + " blocks from "
                + box.min().toShortString() + (selected.isPresent() ? " (the Zone Wand selection)" : "") + " to " + file
                + (java.nio.file.Files.isDirectory(datapack) ? ", with the datapack datapacks/" + name : "")).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int importPack(CommandContext<CommandSourceStack> ctx, Optional<BlockPos> at, boolean replace) throws CommandSyntaxException {
        String name = name(ctx);
        ServerLevel level = ctx.getSource().getLevel();
        notWhilePlaying(level);
        if (!MapSavedData.get(level).map().equals(ZombiesMap.EMPTY) && !replace) {
            throw Targets.fail("This world already has a map. Add replace to put the pack's map in its place: /pap map import " + name + " ... replace");
        }
        Path file = folder().resolve(name + ".zip");
        MapPack.Contents contents;
        try {
            contents = MapPack.read(file);
        } catch (IOException e) {
            throw Targets.fail("Couldn't read " + file + ": " + e.getMessage());
        } catch (MapPack.BadPack e) {
            throw Targets.fail(name + ".zip isn't a usable map pack: " + e.getMessage());
        }
        BlockPos corner = at.orElse(contents.manifest().origin());
        if (!contents.datapack().isEmpty()) {
            try {
                MapPack.installDatapack(level.getServer().getWorldPath(LevelResource.DATAPACK_DIR), name, contents.datapack());
            } catch (IOException e) {
                throw Targets.fail("Couldn't install the pack's datapack: " + e.getMessage());
            }
        }
        MapPack.place(level, contents, corner);
        var size = contents.manifest().size();
        ctx.getSource().sendSuccess(() -> Component.literal("Imported " + name + " (" + size.getX() + "x" + size.getY() + "x" + size.getZ()
                + ") with its corner at " + corner.toShortString()).withStyle(ChatFormatting.GREEN), true);
        if (!contents.datapack().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Installed its datapack as datapacks/" + name + "; reloading"), true);
            level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), "reload");
        }
        ValidationReport report = MapValidator.validate(MapSavedData.get(level).map(), GameManager.validationContext(level));
        ctx.getSource().sendSuccess(() -> Component.literal(report.playable() ? "The map is playable. /pap map validate for details"
                : "The map isn't playable here yet. /pap map validate for what's wrong").withStyle(report.playable() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return 1;
    }
}
