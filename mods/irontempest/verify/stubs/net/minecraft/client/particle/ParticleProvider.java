package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface ParticleProvider<T extends ParticleOptions> {
    @Nullable
    Particle createParticle(T type, ClientLevel level, double x, double y, double z,
                            double xSpeed, double ySpeed, double zSpeed);
}
