package net.minecraft.world.level;

public class FoliageColor {
    public static final int FOLIAGE_EVERGREEN = -10380959;
    public static final int FOLIAGE_BIRCH = -8345771;
    public static final int FOLIAGE_DEFAULT = -12012264;
    public static final int FOLIAGE_MANGROVE = -7158200;
    private static int[] pixels = new int[65536];

    public static void init(int[] foliageBuffer) {
        pixels = foliageBuffer;
    }

    public static int get(double temperature, double humidity) {
        humidity *= temperature;
        int i = (int)((1.0 - temperature) * 255.0);
        int i1 = (int)((1.0 - humidity) * 255.0);
        int i2 = i1 << 8 | i;
        return i2 >= pixels.length ? -12012264 : pixels[i2];
    }
}
