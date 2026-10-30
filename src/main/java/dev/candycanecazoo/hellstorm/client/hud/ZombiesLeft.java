package dev.candycanecazoo.hellstorm.client.hud;

import dev.candycanecazoo.hellstorm.net.HudSyncPacket;

import java.util.Optional;

/**
 * How many zombies are left in the round, or the phase while it isn't a plain round ("Starting in 3", "Game over").
 * Attached under the round counter by default.
 */
public final class ZombiesLeft extends HudElement {
    ZombiesLeft(HudElement round) {
        super("zombies", "The zombies left, or the phase between rounds.", 0, 0, round);
    }

    @Override
    protected Size size(Frame frame) {
        return text(frame.hud()).map(text -> new Size(frame.font().width(text), frame.font().lineHeight - 1)).orElse(Size.NONE);
    }

    @Override
    protected void draw(Frame frame, int left, int top, Size size) {
        HudSyncPacket hud = frame.hud();
        boolean plainRound = hud.phase().equals("Round " + hud.round());
        text(hud).ifPresent(text -> frame.graphics().drawString(frame.font(), text, left, top, plainRound ? Colors.GREY : Colors.WHITE));
    }

    private static Optional<String> text(HudSyncPacket hud) {
        if (!hud.phase().equals("Round " + hud.round())) {
            return Optional.of(hud.phase());
        }
        return hud.zombiesLeft() > 0 ? Optional.of(hud.zombiesLeft() + " left") : Optional.empty();
    }
}
