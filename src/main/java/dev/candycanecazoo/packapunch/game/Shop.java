package dev.candycanecazoo.packapunch.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Optional;

/** The one purchase path every machine and door uses: feature rules, then power and points, then delivery. */
public final class Shop {
    /** What a machine sells. */
    public interface Sale {
        int cost();

        boolean needsPower();

        /** The feature's own rules, checked before charging: already owned, a full perk list, a busy machine. */
        default Optional<Refusal> check(GameManager.Active game, ServerPlayer player) {
            return Optional.empty();
        }

        /** Hands over what was bought. A refusal here refunds the cost. */
        Optional<Refusal> deliver(GameManager.Active game, ServerPlayer player);
    }

    private Shop() {}

    /** Returns true if the purchase went through. Either way the player hears and sees the result. */
    public static boolean buy(ServerPlayer player, GameManager.Active game, Sale sale) {
        Optional<Refusal> refusal = sale.check(game, player)
                .or(() -> game.game().charge(player.getUUID(), sale.cost(), sale.needsPower()));
        if (refusal.isEmpty()) {
            refusal = sale.deliver(game, player);
            if (refusal.isPresent()) {
                game.game().refund(player.getUUID(), sale.cost());
            }
        }
        if (refusal.isPresent()) {
            refuse(player, refusal.get());
            return false;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 0.7f);
        return true;
    }

    public static void refuse(ServerPlayer player, Refusal refusal) {
        Notices.show(player, Component.literal(refusal.message()).withStyle(ChatFormatting.RED));
        player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1.0f);
    }
}
