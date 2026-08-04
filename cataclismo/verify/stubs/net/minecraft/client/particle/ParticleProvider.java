package net.minecraft.client.particle;

@FunctionalInterface
public interface ParticleProvider<T extends net.minecraft.core.particles.ParticleOptions> {
    Particle createParticle(T options, net.minecraft.client.multiplayer.ClientLevel level, double x, double y, double z, double xd, double yd, double zd);
}
