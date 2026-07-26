package net.minecraft.client.particle;

public interface ParticleRenderType {
    ParticleRenderType TERRAIN_SHEET = new ParticleRenderType() {};
    ParticleRenderType PARTICLE_SHEET_OPAQUE = new ParticleRenderType() {};
    ParticleRenderType PARTICLE_SHEET_TRANSLUCENT = new ParticleRenderType() {};
    ParticleRenderType PARTICLE_SHEET_LIT = new ParticleRenderType() {};
    ParticleRenderType CUSTOM = new ParticleRenderType() {};
    ParticleRenderType NO_RENDER = new ParticleRenderType() {};
}
