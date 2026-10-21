package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class HellstormCommand {
    private static final int RESERVE_MAGAZINES = 8;
    private static final DynamicCommandExceptionType UNKNOWN_GUN =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown TacZ gun: " + id));

    private HellstormCommand() {}

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /** Each subcommand carries its own permission check: the root is open so players can join games. */
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("hs")
                .then(ZoneCommands.zone().requires(HellstormCommand::op))
                .then(ZoneCommands.map().requires(HellstormCommand::op))
                .then(ScriptCommands.script().requires(HellstormCommand::op))
                .then(WindowCommands.window().requires(HellstormCommand::op))
                .then(PowerCommands.power().requires(HellstormCommand::op))
                .then(DoorCommands.door().requires(HellstormCommand::op))
                .then(WallBuyCommands.wallbuy().requires(HellstormCommand::op))
                .then(BoxCommands.box().requires(HellstormCommand::op))
                .then(PapMachineCommands.upgrader().requires(HellstormCommand::op))
                .then(TrapCommands.trap().requires(HellstormCommand::op))
                .then(MobTestCommand.mobtest().requires(HellstormCommand::op))
                .then(DebugCommands.debug().requires(HellstormCommand::op))
                .then(Commands.literal("give").requires(HellstormCommand::op)
                        .then(Commands.argument("gun", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(TaczBridge.allGuns(), builder))
                                .executes(HellstormCommand::give)));
        GameCommands.all().forEach(root::then);
        dispatcher.register(root);
    }

    static boolean op(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    private static int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceLocation gunId = ResourceLocationArgument.getId(ctx, "gun");
        ItemStack gun = TaczBridge.createGun(gunId, RESERVE_MAGAZINES).orElseThrow(() -> UNKNOWN_GUN.create(gunId));
        if (!player.getInventory().add(gun)) {
            player.drop(gun, false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + gunId), false);
        return 1;
    }
}
