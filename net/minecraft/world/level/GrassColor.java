package net.minecraft.world.level;

public class GrassColor {
    private static int[] pixels = new int[65536];

    public static void init(int[] grassBuffer) {
        pixels = grassBuffer;
    }

    public static int get(double temperature, double humidity) {
        humidity *= temperature;
        int i = (int)((1.0 - temperature) * 255.0);
        int i1 = (int)((1.0 - humidity) * 255.0);
        int i2 = i1 << 8 | i;
        return i2 >= pixels.length ? -65281 : pixels[i2];
    }

    public static int getDefaultColor() {
        return get(0.5, 1.0);
    }
}
