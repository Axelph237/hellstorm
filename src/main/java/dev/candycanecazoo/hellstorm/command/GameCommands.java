package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.candycanecazoo.hellstorm.game.GameManager;
import dev.candycanecazoo.hellstorm.game.GamePhase;
import dev.candycanecazoo.hellstorm.game.PlayerState;
import dev.candycanecazoo.hellstorm.game.Sessions;
import dev.candycanecazoo.hellstorm.map.validate.Problem;
import dev.candycanecazoo.hellstorm.perk.Perks;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

/** `/hs game join|leave|start|stop|status`: one game per level, played on that level's map. */
final class GameCommands {
    private GameCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> game() {
        return Commands.literal("game")
                .then(Commands.literal("join").executes(CommandErrors.titled("Unable to join game", GameCommands::join)))
                .then(Commands.literal("leave").executes(CommandErrors.titled("Unable to leave game", GameCommands::leave)))
                .then(Commands.literal("start").requires(GameCommands::opOrPlayerInGame).executes(CommandErrors.titled("Unable to start game", GameCommands::start)))
                .then(Commands.literal("stop").requires(HellstormCommand::op).executes(CommandErrors.titled("Unable to stop game", GameCommands::stop)))
                .then(Commands.literal("status").executes(CommandErrors.titled("Unable to show game status", GameCommands::status)));
    }

    private static boolean opOrPlayerInGame(CommandSourceStack source) {
        return HellstormCommand.op(source)
                || (source.getEntity() instanceof ServerPlayer player && GameManager.forPlayer(player.getUUID()).isPresent());
    }

    private static int join(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (GameManager.forPlayer(player.getUUID()).isPresent()) {
            throw CommandErrors.fail("You're already in a game. Run {} first", "/hs game leave");
        }
        ServerLevel level = player.serverLevel();
        Optional<GameManager.Active> existing = GameManager.get(level);
        GameManager.Active active;
        if (existing.isPresent()) {
            active = existing.get();
        } else {
            GameManager.CreateResult result = GameManager.create(level);
            if (result instanceof GameManager.CreateResult.Invalid invalid) {
                throw CommandErrors.failAll(MapCommands.messages(invalid.report(), Problem.Severity.ERROR), List.of());
            }
            if (result instanceof GameManager.CreateResult.EditMode) {
                throw CommandErrors.fail("This map is in edit mode. An op can open it for games with {}", "/hs map mode play");
            }
            if (result instanceof GameManager.CreateResult.NoStartCells) {
                throw CommandErrors.fail("No start room has a spot to stand on (solid floor and two blocks of headroom)");
            }
            active = ((GameManager.CreateResult.Created) result).active();
        }
        if (active.game().full()) {
            throw CommandErrors.fail("This game is full ({} players)", active.game().settings().players().maxPlayers());
        }
        if (!active.game().join(player.getUUID())) {
            throw CommandErrors.fail("This game is ending. Try again in a moment");
        }
        // Before the start nothing happens to them: the end of the countdown takes their state and moves everyone in.
        boolean waiting = active.game().player(player.getUUID()).map(PlayerState::waiting).orElse(false);
        if (!waiting) {
            active.world().enterGame(player.getUUID());
            active.world().spectate(player.getUUID());
        }
        String next = active.game().phase() instanceof GamePhase.Lobby ? " /hs game start when everyone is in"
                : waiting ? " It's about to start" : " You'll spawn next round";
        ctx.getSource().sendSuccess(() -> Component.literal("Joined the game." + next).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<GameManager.Active> active = GameManager.forPlayer(player.getUUID());
        if (active.isEmpty()) {
            throw CommandErrors.fail("You're not in a game");
        }
        boolean waiting = active.get().game().player(player.getUUID()).map(PlayerState::waiting).orElse(false);
        if (!waiting) {
            Perks.clearBuffs(player);
        }
        active.get().game().leave(player.getUUID());
        Sessions.get(player.server).restore(player);
        if (active.get().game().players().isEmpty()) {
            GameManager.end(active.get());
        }
        ctx.getSource().sendSuccess(() -> Component.literal(waiting ? "Left the game" : "Left the game. Your inventory is back"), false);
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Optional<GameManager.Active> active = GameManager.get(ctx.getSource().getLevel());
        if (active.isEmpty()) {
            throw CommandErrors.fail("No game here. Run {} first", "/hs game join");
        }
        if (!active.get().game().start()) {
            throw CommandErrors.fail("The game has already started");
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Starting"), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Optional<GameManager.Active> active = GameManager.get(ctx.getSource().getLevel());
        if (active.isEmpty()) {
            throw CommandErrors.fail("No game is running here");
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
                    + player.kills() + " kills (" + player.headshots() + " headshots)"
                    + (player.alive() ? "" : player.waiting() ? ", waiting for the start" : ", spectating")), false);
        }
        return 1;
    }
}
