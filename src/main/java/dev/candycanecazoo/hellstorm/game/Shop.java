package dev.candycanecazoo.hellstorm.game;

import dev.candycanecazoo.hellstorm.registry.PapRegistries;
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

    /**
     * Returns true if the purchase went through. Either way the player sees the result; only a purchase makes a sound: the
     * cha-ching when points were spent, a chime for something free (taking a finished upgrade, a free power switch), since
     * no points moved.
     */
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
        if (sale.cost() > 0) {
            chaChing(player);
        } else {
            player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 0.7f);
        }
        return true;
    }

    /**
     * The register's cha-ching, for points changing hands: spent here, or earned rebuilding a board. Only this player hears
     * it, as in CoD: it is their points, and in a busy room everyone else's purchases would be noise. It plays where they
     * stand.
     */
    public static void chaChing(ServerPlayer player) {
        player.playNotifySound(PapRegistries.CHA_CHING.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    public static void refuse(ServerPlayer player, Refusal refusal) {
        Component text = refusal instanceof Refusal.Translated t
                ? Component.translatable(t.key(), t.nameKeys().stream().map(Component::translatable).toArray())
                : Component.literal(refusal.message());
        Notices.show(player, text.copy().withStyle(ChatFormatting.RED));
    }
}
