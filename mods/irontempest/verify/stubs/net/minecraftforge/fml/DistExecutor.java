package net.minecraftforge.fml;

import java.util.function.Supplier;
import net.minecraftforge.api.distmarker.Dist;

public final class DistExecutor {
    public static void unsafeRunWhenOn(Dist dist, Supplier<Runnable> toRun) {}
    public static <T> T unsafeRunForDist(Supplier<Supplier<T>> clientTarget, Supplier<Supplier<T>> serverTarget) { throw new UnsupportedOperationException(); }

    private DistExecutor() {}
}
