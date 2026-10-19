package dev.candycanecazoo.packapunch.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.candycanecazoo.packapunch.game.ForgeGameWorld;
import dev.candycanecazoo.packapunch.map.MapSavedData;
import dev.candycanecazoo.packapunch.map.WindowDef;
import dev.candycanecazoo.packapunch.mob.AiInjector;
import dev.candycanecazoo.packapunch.mob.MobFactory;
import dev.candycanecazoo.packapunch.mob.MobProfile;
import dev.candycanecazoo.packapunch.mob.MobProfiles;
import dev.candycanecazoo.packapunch.mob.SupportTier;
import dev.candycanecazoo.packapunch.mob.path.MovementClass;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Optional;

/**
 * `/pap mobtest <profile>`: spawns a profile where you're looking, with the game's AI and you as its target, and reports
 * how well the game can drive it. For pack authors trying other mods' mobs.
 */
final class MobTestCommand {
    private MobTestCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> mobtest() {
        return Commands.literal("mobtest").then(Commands.argument("profile", ResourceLocationArgument.id())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(MobProfiles.all().keySet(), builder))
                .executes(MobTestCommand::run));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "profile");
        MobProfile profile = MobProfiles.all().get(id);
        if (profile == null) {
            ctx.getSource().sendFailure(Component.literal("No mob profile " + id + ". Profiles live in data/<ns>/packapunch/mob_profiles/"));
            return 0;
        }
        Optional<Mob> created = MobFactory.create(level, profile, id);
        if (created.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Couldn't create " + profile.entity() + " (see the log)"));
            return 0;
        }
        Mob mob = created.get();
        HitResult hit = player.pick(8, 1.0f, false);
        BlockPos at = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? block.getBlockPos().relative(block.getDirection()) : player.blockPosition().relative(player.getDirection(), 3);
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot() + 180, 0);
        mob.addTag(ForgeGameWorld.MOB_TAG);
        SupportTier tier = SupportTier.of(mob);
        MobProfile.Ai used = AiInjector.inject(mob, profile, new AiInjector.Stats(20 * profile.healthMultiplier(),
                0.23 * profile.speedMultiplier(), (float) profile.damage()), () -> List.of(player));
        level.addFreshEntity(mob);
        MovementClass mc = MovementClass.ofHitbox(mob.getBbWidth(), mob.getBbHeight());
        List<String> tooSmall = MapSavedData.get(level).map().windows().values().stream()
                .filter(w -> mc.width() > w.width() || mc.height() > w.height()).map(WindowDef::id).toList();
        ChatFormatting color = tier == SupportTier.FULL ? ChatFormatting.GREEN : ChatFormatting.GOLD;
        ctx.getSource().sendSuccess(() -> Component.literal(id + " (" + profile.entity() + "): " + tier).withStyle(color), false);
        ctx.getSource().sendSuccess(() -> Component.literal(" AI " + used.getSerializedName()
                + (used != profile.ai() ? " (profile asked for " + profile.ai().getSerializedName() + ")" : "")
                + ", hitbox " + mob.getBbWidth() + "x" + mob.getBbHeight() + " -> moves as " + mc.width() + "x" + mc.height()), false);
        ctx.getSource().sendSuccess(() -> Component.literal(tooSmall.isEmpty()
                ? " Fits through every window on this map"
                : " Too big for windows: " + String.join(", ", tooSmall)), false);
        return 1;
    }
}
