package net.minecraftforge.client.event;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.eventbus.api.Event;

public class RegisterParticleProvidersEvent extends Event {
    public <T extends ParticleOptions> void registerSpecial(ParticleType<T> type, ParticleProvider<T> provider) {}
    public <T extends ParticleOptions> void registerSpriteSet(ParticleType<T> type,
                                                              ParticleEngine.SpriteParticleRegistration<T> registration) {}
}
