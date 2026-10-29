package dev.candycanecazoo.hellstorm.client;

/**
 * The wall-buy's plate, in blocks across the wall from the block's center: the item it sells with {@link #BORDER} round
 * it, and never smaller than {@link #MIN} square.
 */
record WallBuyPlate(double minX, double minY, double maxX, double maxY) {
    static final double MIN = 0.75;
    static final double BORDER = 1.0 / 16;
    static final WallBuyPlate EMPTY = around(0, 0, 0, 0);

    /** A plate round an item drawn from {@code minX}, {@code minY} to {@code maxX}, {@code maxY}. */
    static WallBuyPlate around(double minX, double minY, double maxX, double maxY) {
        double[] x = span(minX, maxX);
        double[] y = span(minY, maxY);
        return new WallBuyPlate(x[0], y[0], x[1], y[1]);
    }

    private static double[] span(double min, double max) {
        double center = (min + max) / 2;
        double half = Math.max(MIN, max - min + 2 * BORDER) / 2;
        return new double[] {center - half, center + half};
    }

    double width() {
        return maxX - minX;
    }

    double height() {
        return maxY - minY;
    }
}
