package net.minecraft.util;

import net.minecraft.world.phys.Vec3;

public class CubicSampler {
    private static final int GAUSSIAN_SAMPLE_RADIUS = 2;
    private static final int GAUSSIAN_SAMPLE_BREADTH = 6;
    private static final double[] GAUSSIAN_SAMPLE_KERNEL = new double[]{0.0, 1.0, 4.0, 6.0, 4.0, 1.0, 0.0};

    private CubicSampler() {
    }

    public static Vec3 gaussianSampleVec3(Vec3 vec, CubicSampler.Vec3Fetcher fetcher) {
        int floor = Mth.floor(vec.x());
        int floor1 = Mth.floor(vec.y());
        int floor2 = Mth.floor(vec.z());
        double d = vec.x() - floor;
        double d1 = vec.y() - floor1;
        double d2 = vec.z() - floor2;
        double d3 = 0.0;
        Vec3 vec3 = Vec3.ZERO;

        for (int i = 0; i < 6; i++) {
            double d4 = Mth.lerp(d, GAUSSIAN_SAMPLE_KERNEL[i + 1], GAUSSIAN_SAMPLE_KERNEL[i]);
            int i1 = floor - 2 + i;

            for (int i2 = 0; i2 < 6; i2++) {
                double d5 = Mth.lerp(d1, GAUSSIAN_SAMPLE_KERNEL[i2 + 1], GAUSSIAN_SAMPLE_KERNEL[i2]);
                int i3 = floor1 - 2 + i2;

                for (int i4 = 0; i4 < 6; i4++) {
                    double d6 = Mth.lerp(d2, GAUSSIAN_SAMPLE_KERNEL[i4 + 1], GAUSSIAN_SAMPLE_KERNEL[i4]);
                    int i5 = floor2 - 2 + i4;
                    double d7 = d4 * d5 * d6;
                    d3 += d7;
                    vec3 = vec3.add(fetcher.fetch(i1, i3, i5).scale(d7));
                }
            }
        }

        return vec3.scale(1.0 / d3);
    }

    @FunctionalInterface
    public interface Vec3Fetcher {
        Vec3 fetch(int x, int y, int z);
    }
}
