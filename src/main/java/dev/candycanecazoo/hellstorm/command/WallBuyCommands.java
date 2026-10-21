package dev.candycanecazoo.hellstorm.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.candycanecazoo.hellstorm.data.ItemSpec;
import dev.candycanecazoo.hellstorm.feature.WallBuy;
import dev.candycanecazoo.hellstorm.feature.WallBuyBlock;
import dev.candycanecazoo.hellstorm.tacz.TaczBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Optional;

/**
 * {@code /hs wallbuy set <cost> held|gun <id>}, {@code ammo <cost> [papCost]}, {@code repurchase <mode>},
 * {@code power <bool>}, {@code describe}: settings for the wall-buy or perk machine you're looking at.
 */
final class WallBuyCommands {
    private static final SimpleCommandExceptionType EMPTY_HAND = new SimpleCommandExceptionType(Component.literal("Hold the item to sell in your main hand"));
    private static final DynamicCommandExceptionType UNKNOWN_MODE = new DynamicCommandExceptionType(
            mode -> Component.literal("Unknown repurchase mode " + mode + "; use refill_ammo, top_up, duplicate, or deny"));

    private WallBuyCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> wallbuy() {
        return Commands.literal("wallbuy")
                .then(Commands.literal("set").then(Commands.argument("cost", IntegerArgumentType.integer(0))
                        .then(Commands.literal("held").executes(WallBuyCommands::setHeld))
                        .then(Commands.literal("gun").then(Commands.argument("gun", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(TaczBridge.allGuns(), builder))
                                .executes(WallBuyCommands::setGun)))))
                .then(Commands.literal("ammo").then(Commands.argument("cost", IntegerArgumentType.integer(0))
                        .executes(ctx -> ammo(ctx, Optional.empty()))
                        .then(Commands.argument("upgradedCost", IntegerArgumentType.integer(0))
                                .executes(ctx -> ammo(ctx, Optional.of(IntegerArgumentType.getInteger(ctx, "upgradedCost")))))))
                .then(Commands.literal("repurchase").then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(WallBuy.Repurchase.values()).map(WallBuy.Repurchase::getSerializedName), builder))
                        .executes(WallBuyCommands::repurchase)))
                .then(Commands.literal("power").then(Commands.argument("needed", BoolArgumentType.bool())
                        .executes(WallBuyCommands::power)))
                .then(Commands.literal("describe").executes(WallBuyCommands::describe));
    }

    private static WallBuyBlock.Entity target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return Targets.lookedAt(ctx.getSource().getPlayerOrException(), WallBuyBlock.Entity.class, "wall-buy or perk machine");
    }

    private static int setHeld(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WallBuyBlock.Entity entity = target(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            throw EMPTY_HAND.create();
        }
        return sell(ctx, entity, ItemSpec.of(held));
    }

    private static int setGun(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WallBuyBlock.Entity entity = target(ctx);
        return sell(ctx, entity, ItemSpec.Gun.of(ResourceLocationArgument.getId(ctx, "gun")));
    }

    private static int sell(CommandContext<CommandSourceStack> ctx, WallBuyBlock.Entity entity, ItemSpec item) throws CommandSyntaxException {
        int cost = IntegerArgumentType.getInteger(ctx, "cost");
        WallBuy.Config config = WallBuy.Config.forItem(item, cost, WallBuy.Repurchase.defaultFor(item));
        return apply(ctx, entity, config.withRequiresPower(entity.config().requiresPower()));
    }

    private static int apply(CommandContext<CommandSourceStack> ctx, WallBuyBlock.Entity entity, WallBuy.Config config) throws CommandSyntaxException {
        Targets.require(entity.trySet(config));
        ctx.getSource().sendSuccess(() -> WallBuyBlock.describe(entity), false);
        return 1;
    }

    private static int ammo(CommandContext<CommandSourceStack> ctx, Optional<Integer> papCost) throws CommandSyntaxException {
        WallBuyBlock.Entity entity = target(ctx);
        WallBuy.Config c = entity.config();
        int cost = IntegerArgumentType.getInteger(ctx, "cost");
        return apply(ctx, entity, new WallBuy.Config(c.item(), c.cost(), c.repurchase(), cost, papCost.orElse(c.papAmmoCost()), c.requiresPower()));
    }

    private static int power(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WallBuyBlock.Entity entity = target(ctx);
        return apply(ctx, entity, entity.config().withRequiresPower(BoolArgumentType.getBool(ctx, "needed")));
    }

    private static int repurchase(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WallBuyBlock.Entity entity = target(ctx);
        String name = StringArgumentType.getString(ctx, "mode");
        WallBuy.Repurchase mode = Arrays.stream(WallBuy.Repurchase.values()).filter(m -> m.getSerializedName().equals(name)).findFirst()
                .orElseThrow(() -> UNKNOWN_MODE.create(name));
        WallBuy.Config c = entity.config();
        return apply(ctx, entity, new WallBuy.Config(c.item(), c.cost(), mode, c.ammoCost(), c.papAmmoCost(), c.requiresPower()));
    }

    private static int describe(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WallBuyBlock.Entity entity = target(ctx);
        ctx.getSource().sendSuccess(() -> WallBuyBlock.describe(entity), false);
        return 1;
    }
}
