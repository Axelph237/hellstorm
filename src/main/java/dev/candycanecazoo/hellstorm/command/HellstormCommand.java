package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import dev.candycanecazoo.hellstorm.tools.MapHistory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Optional;

public final class HellstormCommand {
    private static final int RESERVE_MAGAZINES = 8;

    private HellstormCommand() {}

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /** Each subcommand carries its own permission check: the root is open so players can join games. */
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("hs")
                .then(ZonesCommands.zones().requires(HellstormCommand::op))
                .then(Commands.literal("undo").requires(HellstormCommand::op).executes(CommandErrors.titled("Unable to undo", ctx -> step(ctx, true))))
                .then(Commands.literal("redo").requires(HellstormCommand::op).executes(CommandErrors.titled("Unable to redo", ctx -> step(ctx, false))))
                .then(MapCommands.map().requires(HellstormCommand::op))
                .then(ScriptCommands.script().requires(HellstormCommand::op))
                .then(PowerCommands.power().requires(HellstormCommand::op))
                .then(WallBuyCommands.wallbuy().requires(HellstormCommand::op))
                .then(BoxCommands.box().requires(HellstormCommand::op))
                .then(PapMachineCommands.upgrader().requires(HellstormCommand::op))
                .then(TrapCommands.trap().requires(HellstormCommand::op))
                .then(MobTestCommand.mobtest().requires(HellstormCommand::op))
                .then(DebugCommands.debug().requires(HellstormCommand::op))
                .then(Commands.literal("give").requires(HellstormCommand::op)
                        .then(Commands.argument("gun", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(TaczBridge.allGuns(), builder))
                                .executes(CommandErrors.titled("Unable to give gun", HellstormCommand::give))))
                .then(GameCommands.game());
        dispatcher.register(root);
    }

    static boolean op(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    /** Undoes or redoes the level's last map edit. */
    private static int step(CommandContext<CommandSourceStack> ctx, boolean undo) throws CommandSyntaxException {
        ServerLevel level = ctx.getSource().getLevel();
        Optional<String> what = undo ? MapHistory.undo(level) : MapHistory.redo(level);
        if (what.isEmpty()) {
            throw CommandErrors.fail(undo ? "Nothing to undo" : "Nothing to redo");
        }
        ctx.getSource().sendSuccess(() -> Component.literal((undo ? "Undid " : "Redid ") + what.get()), false);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceLocation gunId = ResourceLocationArgument.getId(ctx, "gun");
        ItemStack gun = TaczBridge.createGun(gunId, RESERVE_MAGAZINES).orElseThrow(() -> CommandErrors.fail("Unknown TacZ gun: {}", gunId));
        if (!player.getInventory().add(gun)) {
            player.drop(gun, false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + gunId), false);
        return 1;
    }
}
