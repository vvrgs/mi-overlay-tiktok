package com.vvrgs.cataclysm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Ceniza volcanica: FLOTA con deriva lateral senoidal, vida larga, gris calido. */
public class AshParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private final float driftPhase;

    protected AshParticle(ClientLevel level, double x, double y, double z,
                          double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0.02F;
        this.friction = 0.98F;
        this.lifetime = 90 + this.random.nextInt(50);
        this.quadSize = 0.06F + this.random.nextFloat() * 0.05F;
        float shade = 0.55F + this.random.nextFloat() * 0.2F;
        setColor(shade, shade * 0.95F, shade * 0.9F);
        this.driftPhase = this.random.nextFloat() * 6.28F;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        // deriva lateral: la ceniza no cae recta
        this.xd += Math.sin(this.age * 0.06D + driftPhase) * 0.0012D;
        this.zd += Math.cos(this.age * 0.05D + driftPhase) * 0.0012D;
        setSpriteFromAge(sprites);
        if (this.age > this.lifetime - 20) {
            this.alpha = (this.lifetime - this.age) / 20.0F;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double dx, double dy, double dz) {
            return new AshParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
