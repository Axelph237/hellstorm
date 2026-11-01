package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.gen.pack.RoomPack;
import dev.candycanecazoo.hellstorm.gen.place.MapGenerator;
import dev.candycanecazoo.hellstorm.gen.place.RoomSaver;
import dev.candycanecazoo.hellstorm.map.MapSavedData;
import dev.candycanecazoo.hellstorm.map.ZombiesMap;
import dev.candycanecazoo.hellstorm.tools.MapEdits;
import dev.candycanecazoo.hellstorm.tools.MapPack;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code /hs map generate <packs> [<seed>] [replace]}: generates a map from room packs with its corner where the command
 * runs. {@code packs} is pack ids joined with {@code +}, or {@code all}. Without a seed, a random one, which it reports,
 * so the map can be made again.
 */
final class GenerateCommands {
    private static final SuggestionProvider<CommandSourceStack> PACKS = (ctx, builder) -> SharedSuggestionProvider.suggest(
            Stream.concat(Stream.of("all"), RoomPack.available(RoomSaver.packs()).stream()), builder);

    private GenerateCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> generate() {
        return Commands.literal("generate").then(Commands.argument("packs", StringArgumentType.string()).suggests(PACKS)
                .executes(CommandErrors.titled("Unable to generate a map", ctx -> generate(ctx, ctx.getSource().getLevel().random.nextLong(), false)))
                .then(Commands.argument("seed", LongArgumentType.longArg())
                        .executes(CommandErrors.titled("Unable to generate a map", ctx -> generate(ctx, LongArgumentType.getLong(ctx, "seed"), false)))
                        .then(Commands.literal("replace")
                                .executes(CommandErrors.titled("Unable to generate a map", ctx -> generate(ctx, LongArgumentType.getLong(ctx, "seed"), true))))));
    }

    /** {@code /hs map saverooms [<radius>]}: saves every Room Block's room within {@code radius} blocks, as if Done were pressed on each. */
    static LiteralArgumentBuilder<CommandSourceStack> saveRooms() {
        return Commands.literal("saverooms")
                .executes(CommandErrors.titled("Unable to save rooms", ctx -> saveRooms(ctx, 64)))
                .then(Commands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 512))
                        .executes(CommandErrors.titled("Unable to save rooms", ctx -> saveRooms(ctx,
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius")))));
    }

    private static int saveRooms(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos center = BlockPos.containing(ctx.getSource().getPosition());
        java.util.List<dev.candycanecazoo.hellstorm.gen.block.RoomBlock.Entity> blocks = new java.util.ArrayList<>();
        int chunks = (radius >> 4) + 1;
        for (int cx = (center.getX() >> 4) - chunks; cx <= (center.getX() >> 4) + chunks; cx++) {
            for (int cz = (center.getZ() >> 4) - chunks; cz <= (center.getZ() >> 4) + chunks; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                for (var entity : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (entity instanceof dev.candycanecazoo.hellstorm.gen.block.RoomBlock.Entity room
                            && room.getBlockPos().distSqr(center) <= (double) radius * radius) {
                        blocks.add(room);
                    }
                }
            }
        }
        if (blocks.isEmpty()) {
            throw CommandErrors.fail("No Room Block within {} blocks", radius);
        }
        blocks.sort(java.util.Comparator.comparing(b -> b.getBlockPos()));
        int saved = 0;
        for (var block : blocks) {
            RoomSaver.Result result = RoomSaver.save(level, block);
            saved += result.saved() ? 1 : 0;
            ctx.getSource().sendSuccess(() -> Component.literal(result.message()).withStyle(result.saved() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
            result.warnings().forEach(w -> ctx.getSource().sendSuccess(() -> Component.literal(w).withStyle(ChatFormatting.YELLOW), false));
        }
        int done = saved;
        ctx.getSource().sendSuccess(() -> Component.literal("Saved " + done + " of " + blocks.size() + " rooms"), true);
        return saved;
    }

    private static int generate(CommandContext<CommandSourceStack> ctx, long seed, boolean replace) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        if (GameManager.get(level).isPresent()) {
            throw CommandErrors.fail("A game is running here. {} first", "/hs game stop");
        }
        ZombiesMap old = MapSavedData.get(level).map();
        if (!old.equals(ZombiesMap.EMPTY) && !replace) {
            throw CommandErrors.fail("This world already has a map. Add {} to generate one in its place: {}", "replace",
                    "/hs map generate " + StringArgumentType.getString(ctx, "packs") + " " + seed + " replace");
        }
        String packs = StringArgumentType.getString(ctx, "packs");
        List<String> ids = packs.equals("all") ? List.of() : Arrays.stream(packs.split("\\+")).filter(s -> !s.isBlank()).toList();
        List<RoomPack> loaded;
        try {
            loaded = MapGenerator.load(ids);
        } catch (IOException e) {
            throw CommandErrors.fail("Couldn't read the packs: {}", e.getMessage());
        } catch (RoomPack.BadPack e) {
            throw CommandErrors.fail("A pack isn't usable: {}", e.getMessage());
        }
        if (replace) {
            MapPack.clear(level, old);
            MapEdits.apply(level, m -> ZombiesMap.EMPTY);
        }
        BlockPos corner = BlockPos.containing(ctx.getSource().getPosition());
        MapGenerator.Result result = MapGenerator.generate(level, loaded, seed, corner);
        if (result instanceof MapGenerator.Result.Failed failed) {
            throw CommandErrors.fail("{}", failed.reason());
        }
        MapGenerator.Result.Generated made = (MapGenerator.Result.Generated) result;
        ctx.getSource().sendSuccess(() -> Component.literal("Generated " + made.layout().rooms().size() + " rooms from "
                + loaded.stream().map(RoomPack::id).toList() + " with seed " + seed + ", its corner at " + corner.toShortString())
                .withStyle(ChatFormatting.GREEN), true);
        made.notes().forEach(note -> ctx.getSource().sendSuccess(() -> Component.literal(note), false));
        ctx.getSource().sendSuccess(() -> Component.literal(made.report().playable() ? "The map is playable. /hs map validate for details"
                : "The map isn't playable yet. /hs map validate for what's wrong").withStyle(made.report().playable() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return made.layout().rooms().size();
    }
}
