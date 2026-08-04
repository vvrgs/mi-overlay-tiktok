package net.minecraft.client.particle;

public class ParticleEngine {
    @FunctionalInterface
    public interface SpriteParticleRegistration<T extends net.minecraft.core.particles.ParticleOptions> {
        ParticleProvider<T> create(SpriteSet sprites);
    }
}
