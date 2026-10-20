package dev.candycanecazoo.packapunch.script;

import dev.candycanecazoo.packapunch.PackAPunch;
import dev.candycanecazoo.packapunch.game.Game;
import dev.candycanecazoo.packapunch.game.GamePhase;
import dev.candycanecazoo.packapunch.game.PlayerState;
import dev.candycanecazoo.packapunch.tools.DebugRules;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Runs script commands in the game's level at the map origin, and reports script errors to the log and to ops in the game. */
public final class ForgeScriptHost implements ScriptHost {
    private final ServerLevel level;
    private final Game game;
    private final BlockPos origin;
    private final int permissionLevel;
    private final boolean commandsAllowed;

    public ForgeScriptHost(ServerLevel level, Game game, BlockPos origin, int permissionLevel, boolean commandsAllowed) {
        this.level = level;
        this.game = game;
        this.origin = origin;
        this.permissionLevel = permissionLevel;
        this.commandsAllowed = commandsAllowed;
    }

    @Override
    public CommandResult runCommand(String command) {
        if (!commandsAllowed) {
            return new CommandResult(false, 0);
        }
        CommandSourceStack source = level.getServer().createCommandSourceStack()
                .withLevel(level)
                .withPosition(Vec3.atBottomCenterOf(origin))
                .withRotation(Vec2.ZERO)
                .withPermission(permissionLevel)
                .withSuppressedOutput();
        int result = level.getServer().getCommands().performPrefixedCommand(source, command);
        return new CommandResult(result > 0, result);
    }

    @Override
    public void log(LogLevel logLevel, String source, String message) {
        String line = "[" + source + "] " + message;
        switch (logLevel) {
            case INFO -> PackAPunch.LOG.info("Script {}", line);
            case WARN -> PackAPunch.LOG.warn("Script {}", line);
            case ERROR -> {
                PackAPunch.LOG.error("Script {}", line);
                if (!DebugRules.get(level.getServer()).get(DebugRules.Rule.SHOW_ERRORS)) {
                    return;
                }
                Component text = Component.literal("Script error " + line).withStyle(ChatFormatting.RED);
                for (PlayerState player : game.players()) {
                    ServerPlayer online = level.getServer().getPlayerList().getPlayer(player.id());
                    if (online != null && online.hasPermissions(2)) {
                        online.sendSystemMessage(text);
                    }
                }
            }
        }
    }

    @Override
    public int round() {
        GamePhase phase = game.phase();
        if (phase instanceof GamePhase.RoundActive active) {
            return active.round();
        }
        if (phase instanceof GamePhase.Intermission intermission) {
            return intermission.nextRound() - 1;
        }
        if (phase instanceof GamePhase.GameOver over) {
            return over.roundReached();
        }
        return 0;
    }

    @Override
    public List<PlayerInfo> players() {
        List<PlayerInfo> players = new ArrayList<>();
        for (PlayerState player : game.players()) {
            ServerPlayer online = level.getServer().getPlayerList().getPlayer(player.id());
            String name = online == null ? player.id().toString() : online.getGameProfile().getName();
            players.add(new PlayerInfo(player.id(), name, player.points(), player.alive()));
        }
        return players;
    }
}
