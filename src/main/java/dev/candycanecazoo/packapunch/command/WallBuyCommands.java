package dev.candycanecazoo.packapunch.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.candycanecazoo.packapunch.data.ItemSpec;
import dev.candycanecazoo.packapunch.feature.WallBuy;
import dev.candycanecazoo.packapunch.feature.WallBuyBlock;
import dev.candycanecazoo.packapunch.tacz.TaczBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.Optional;

/**
 * {@code /pap wallbuy set <cost> held|gun <id>}, {@code ammo <cost> [papCost]}, {@code repurchase <mode>}, {@code describe}:
 * settings for the wall-buy you're looking at.
 */
final class WallBuyCommands {
    private static final SimpleCommandExceptionType EMPTY_HAND = new SimpleCommandExceptionType(Component.literal("Hold the item to sell in your main hand"));
    private static final SimpleCommandExceptionType NOT_A_GUN = new SimpleCommandExceptionType(
            Component.literal("Only TacZ guns refill ammo; use top_up, duplicate, or deny"));
    private static final DynamicCommandExceptionType UNKNOWN_GUN = new DynamicCommandExceptionType(id -> Component.literal("Unknown TacZ gun: " + id));
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
                        .then(Commands.argument("papCost", IntegerArgumentType.integer(0))
                                .executes(ctx -> ammo(ctx, Optional.of(IntegerArgumentType.getInteger(ctx, "papCost")))))))
                .then(Commands.literal("repurchase").then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(WallBuy.Repurchase.values()).map(WallBuy.Repurchase::getSerializedName), builder))
                        .executes(WallBuyCommands::repurchase)))
                .then(Commands.literal("describe").executes(WallBuyCommands::describe));
    }

    private static WallBuyBlock.Entity target(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return Targets.lookedAt(ctx.getSource().getPlayerOrException(), WallBuyBlock.Entity.class, "wall-buy");
    }

    private static int setHeld(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WallBuyBlock.Entity entity = target(ctx);
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            throw EMPTY_HAND.create();
        }
        Optional<TaczBridge.GunInfo> gun = TaczBridge.inspect(held);
        ItemSpec item = gun.isPresent()
                ? new ItemSpec.Gun(gun.get().gunId(), TaczBridge.hasUnlimitedAmmo(held), true)
                : new ItemSpec.Plain(ForgeRegistries.ITEMS.getKey(held.getItem()), held.getCount(), Optional.ofNullable(held.getTag()).map(CompoundTag::copy));
        return sell(ctx, entity, item);
    }

    private static int setGun(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WallBuyBlock.Entity entity = target(ctx);
        ResourceLocation gunId = ResourceLocationArgument.getId(ctx, "gun");
        if (!TaczBridge.gunExists(gunId)) {
            throw UNKNOWN_GUN.create(gunId);
        }
        return sell(ctx, entity, ItemSpec.Gun.of(gunId));
    }

    private static int sell(CommandContext<CommandSourceStack> ctx, WallBuyBlock.Entity entity, ItemSpec item) {
        int cost = IntegerArgumentType.getInteger(ctx, "cost");
        entity.setConfig(WallBuy.Config.forItem(item, cost, WallBuy.Repurchase.defaultFor(item)));
        ctx.getSource().sendSuccess(() -> WallBuyBlock.describe(entity), false);
        return 1;
    }

    private static int ammo(CommandContext<CommandSourceStack> ctx, Optional<Integer> papCost) throws CommandSyntaxException {
        WallBuyBlock.Entity entity = target(ctx);
        WallBuy.Config c = entity.config();
        int cost = IntegerArgumentType.getInteger(ctx, "cost");
        entity.setConfig(new WallBuy.Config(c.item(), c.cost(), c.repurchase(), cost, papCost.orElse(c.papAmmoCost())));
        ctx.getSource().sendSuccess(() -> WallBuyBlock.describe(entity), false);
        return 1;
    }

    private static int repurchase(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WallBuyBlock.Entity entity = target(ctx);
        String name = StringArgumentType.getString(ctx, "mode");
        WallBuy.Repurchase mode = Arrays.stream(WallBuy.Repurchase.values()).filter(m -> m.getSerializedName().equals(name)).findFirst()
                .orElseThrow(() -> UNKNOWN_MODE.create(name));
        WallBuy.Config c = entity.config();
        if (mode == WallBuy.Repurchase.REFILL_AMMO && !(c.item() instanceof ItemSpec.Gun)) {
            throw NOT_A_GUN.create();
        }
        entity.setConfig(new WallBuy.Config(c.item(), c.cost(), mode, c.ammoCost(), c.papAmmoCost()));
        ctx.getSource().sendSuccess(() -> WallBuyBlock.describe(entity), false);
        return 1;
    }

    private static int describe(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        WallBuyBlock.Entity entity = target(ctx);
        ctx.getSource().sendSuccess(() -> WallBuyBlock.describe(entity), false);
        return 1;
    }
}
