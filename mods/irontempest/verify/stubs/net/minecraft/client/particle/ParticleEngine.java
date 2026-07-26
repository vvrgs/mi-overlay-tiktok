package net.minecraft.client.particle;

import net.minecraft.core.particles.ParticleOptions;

public class ParticleEngine {
    @FunctionalInterface
    public interface SpriteParticleRegistration<T extends ParticleOptions> {
        ParticleProvider<T> create(SpriteSet sprites);
    }
}
