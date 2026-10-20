package dev.candycanecazoo.packapunch.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.candycanecazoo.packapunch.game.GameManager;
import dev.candycanecazoo.packapunch.game.GamePhase;
import dev.candycanecazoo.packapunch.game.Loadout;
import dev.candycanecazoo.packapunch.game.PlayerState;
import dev.candycanecazoo.packapunch.game.Sessions;
import dev.candycanecazoo.packapunch.map.validate.Problem;
import dev.candycanecazoo.packapunch.perk.Perks;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/** `/pap join|leave|start|stop|status`: one game per level, played on that level's map. */
final class GameCommands {
    private GameCommands() {}

    static List<LiteralArgumentBuilder<CommandSourceStack>> all() {
        return List.of(
                Commands.literal("join").executes(GameCommands::join),
                Commands.literal("leave").executes(GameCommands::leave),
                Commands.literal("start").requires(GameCommands::opOrPlayerInGame).executes(GameCommands::start),
                Commands.literal("stop").requires(PapCommand::op).executes(GameCommands::stop),
                Commands.literal("status").executes(GameCommands::status));
    }

    private static boolean opOrPlayerInGame(CommandSourceStack source) {
        return PapCommand.op(source)
                || (source.getEntity() instanceof ServerPlayer player && GameManager.forPlayer(player.getUUID()).isPresent());
    }

    private static int join(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (GameManager.forPlayer(player.getUUID()).isPresent()) {
            ctx.getSource().sendFailure(Component.literal("You're already in a game. /pap leave first"));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Optional<GameManager.Active> existing = GameManager.get(level);
        GameManager.Active active;
        if (existing.isPresent()) {
            active = existing.get();
        } else {
            GameManager.CreateResult result = GameManager.create(level);
            if (result instanceof GameManager.CreateResult.Invalid invalid) {
                ctx.getSource().sendFailure(Component.literal("This map isn't playable yet:"));
                invalid.report().problems().stream().filter(p -> p.severity() == Problem.Severity.ERROR)
                        .forEach(p -> ctx.getSource().sendFailure(Component.literal(" - " + p.message())));
                return 0;
            }
            if (result instanceof GameManager.CreateResult.NoStartCells) {
                ctx.getSource().sendFailure(Component.literal("No start zone has a spot to stand on (solid floor and two blocks of headroom)"));
                return 0;
            }
            active = ((GameManager.CreateResult.Created) result).active();
        }
        if (!active.game().join(player.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("This game is ending. Try again in a moment"));
            return 0;
        }
        Sessions.get(player.server).save(player);
        Loadout.give(player, active.game().settings().players());
        // Players stay where they are until the game starts, which moves everyone to the start zone.
        if (!active.game().player(player.getUUID()).map(PlayerState::alive).orElse(false)) {
            active.world().spectate(player.getUUID());
        }
        String next = active.game().phase() instanceof GamePhase.Lobby ? " /pap start when everyone is in" : " You'll spawn next round";
        ctx.getSource().sendSuccess(() -> Component.literal("Joined the game." + next).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<GameManager.Active> active = GameManager.forPlayer(player.getUUID());
        if (active.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("You're not in a game"));
            return 0;
        }
        Perks.removeAll(active.get(), player);
        active.get().game().leave(player.getUUID());
        Sessions.get(player.server).restore(player);
        if (active.get().game().players().isEmpty()) {
            GameManager.end(active.get());
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Left the game. Your inventory is back"), false);
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> ctx) {
        Optional<GameManager.Active> active = GameManager.get(ctx.getSource().getLevel());
        if (active.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No game here. /pap join first"));
            return 0;
        }
        if (!active.get().game().start()) {
            ctx.getSource().sendFailure(Component.literal("The game has already started"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Starting"), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        Optional<GameManager.Active> active = GameManager.get(ctx.getSource().getLevel());
        if (active.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No game is running here"));
            return 0;
        }
        GameManager.end(active.get());
        ctx.getSource().sendSuccess(() -> Component.literal("Game stopped. Everyone's inventory is back"), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        Optional<GameManager.Active> active = GameManager.get(ctx.getSource().getLevel());
        if (active.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No game is running here"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Phase: " + active.get().game().phase()), false);
        ServerLevel level = ctx.getSource().getLevel();
        for (PlayerState player : active.get().game().players()) {
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(player.id());
            String name = online == null ? player.id().toString() : online.getGameProfile().getName();
            ctx.getSource().sendSuccess(() -> Component.literal(" " + name + ": " + player.points() + " pts, "
                    + player.kills() + " kills (" + player.headshots() + " headshots)" + (player.alive() ? "" : ", spectating")), false);
        }
        return 1;
    }
}
