package net.minecraft.util;

public interface RandomSource {
    static RandomSource create() { throw new UnsupportedOperationException(); }
    int nextInt();
    int nextInt(int bound);
    double nextDouble();
    float nextFloat();
    double nextGaussian();
    boolean nextBoolean();
}
