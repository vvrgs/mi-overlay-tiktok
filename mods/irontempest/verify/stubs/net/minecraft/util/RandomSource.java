package net.minecraft.util;

public interface RandomSource {
    int nextInt();
    int nextInt(int bound);
    float nextFloat();
    double nextDouble();
    double nextGaussian();
    boolean nextBoolean();
    long nextLong();
}
