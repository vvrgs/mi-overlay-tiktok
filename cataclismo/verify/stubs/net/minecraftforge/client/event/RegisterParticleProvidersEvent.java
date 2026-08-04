package net.minecraftforge.client.event;

public class RegisterParticleProvidersEvent extends net.minecraftforge.eventbus.api.Event {
    public <T extends net.minecraft.core.particles.ParticleOptions> void registerSpriteSet(net.minecraft.core.particles.ParticleType<T> type, net.minecraft.client.particle.ParticleEngine.SpriteParticleRegistration<T> registration) {}
    public <T extends net.minecraft.core.particles.ParticleOptions> void registerSpecial(net.minecraft.core.particles.ParticleType<T> type, net.minecraft.client.particle.ParticleProvider<T> provider) {}
}
