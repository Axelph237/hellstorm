package dev.candycanecazoo.hellstorm.client.hud;

import dev.candycanecazoo.hellstorm.net.HudSyncPacket;

import java.util.Optional;

/** The round number large in red, with the zombies left under it, or the phase while it isn't a plain round. */
public final class RoundCounter extends HudElement {
    private static final float SCALE = 3.0f;
    private static final int LINE_GAP = 2;

    RoundCounter() {
        super("round", "The round number and the zombies left.", 0, 0);
    }

    @Override
    protected Size size(Frame frame) {
        int width = 0;
        int height = 0;
        if (frame.hud().round() > 0) {
            width = Math.round(frame.font().width(String.valueOf(frame.hud().round())) * SCALE);
            height = Math.round(frame.font().lineHeight * SCALE);
        }
        Optional<String> subtitle = subtitle(frame.hud());
        if (subtitle.isPresent()) {
            width = Math.max(width, frame.font().width(subtitle.get()));
            height += (height > 0 ? LINE_GAP : 0) + frame.font().lineHeight;
        }
        return new Size(width, height);
    }

    @Override
    protected void draw(Frame frame, int left, int top, Size size) {
        HudSyncPacket hud = frame.hud();
        if (hud.round() > 0) {
            frame.graphics().pose().pushPose();
            frame.graphics().pose().translate(left, top, 0);
            frame.graphics().pose().scale(SCALE, SCALE, 1);
            frame.graphics().drawString(frame.font(), String.valueOf(hud.round()), 0, 0, Colors.RED);
            frame.graphics().pose().popPose();
        }
        boolean plainRound = hud.phase().equals("Round " + hud.round());
        subtitle(hud).ifPresent(text -> frame.graphics().drawString(frame.font(), text, left, top + size.height() - frame.font().lineHeight,
                plainRound ? Colors.GREY : Colors.WHITE));
    }

    /** The line under the round number, if any: the phase while it isn't a plain round, else the zombies left. */
    private static Optional<String> subtitle(HudSyncPacket hud) {
        if (!hud.phase().equals("Round " + hud.round())) {
            return Optional.of(hud.phase());
        }
        return hud.zombiesLeft() > 0 ? Optional.of(hud.zombiesLeft() + " left") : Optional.empty();
    }
}
