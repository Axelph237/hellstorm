package dev.candycanecazoo.packapunch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import dev.candycanecazoo.packapunch.tacz.TaczBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class ZombiesCommand {
    private static final int RESERVE_MAGAZINES = 8;
    private static final DynamicCommandExceptionType UNKNOWN_GUN =
            new DynamicCommandExceptionType(id -> Component.literal("Unknown TacZ gun: " + id));

    private ZombiesCommand() {}

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("zombies")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("give")
                        .then(Commands.argument("gun", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(TaczBridge.allGuns(), builder))
                                .executes(ZombiesCommand::give))));
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
