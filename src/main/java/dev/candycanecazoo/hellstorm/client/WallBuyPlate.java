package dev.candycanecazoo.hellstorm.client;

/**
 * The wall-buy's plate, in blocks across the wall from the block's center: as long as the item it sells with
 * {@link #BORDER} at each end, never shorter than {@link #MIN}, and the frame's {@link #HEIGHT} tall, centred on the item.
 */
record WallBuyPlate(double minX, double minY, double maxX, double maxY) {
    static final double MIN = 0.75;
    static final double BORDER = 1.0 / 16;
    /** The frame model's width, which stands up the wall. */
    static final double HEIGHT = 1;
    static final WallBuyPlate EMPTY = around(0, 0, 0, 0);

    /** A plate round an item drawn from {@code minX}, {@code minY} to {@code maxX}, {@code maxY}. */
    static WallBuyPlate around(double minX, double minY, double maxX, double maxY) {
        double centerX = (minX + maxX) / 2;
        double centerY = (minY + maxY) / 2;
        double half = Math.max(MIN, maxX - minX + 2 * BORDER) / 2;
        return new WallBuyPlate(centerX - half, centerY - HEIGHT / 2, centerX + half, centerY + HEIGHT / 2);
    }

    double width() {
        return maxX - minX;
    }

    double height() {
        return maxY - minY;
    }
}
