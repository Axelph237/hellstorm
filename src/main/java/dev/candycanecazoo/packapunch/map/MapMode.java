package dev.candycanecazoo.packapunch.map;

import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** A map being built (markers show, no games) or ready to play (markers hidden, games allowed). */
public enum MapMode implements StringRepresentable {
    EDIT, PLAY;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
