package com.vvrgs.cataclysm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Espuma/bruma de la cresta del tsunami: sube, se abre y cae en niebla. */
public class SprayParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected SprayParticle(ClientLevel level, double x, double y, double z,
                            double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0.22F;
        this.friction = 0.95F;
        this.lifetime = 22 + this.random.nextInt(14);
        this.quadSize = 0.12F + this.random.nextFloat() * 0.10F;
        setColor(0.85F, 0.92F, 1.0F);
        this.alpha = 0.8F;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        float life = (float) this.age / this.lifetime;
        this.quadSize *= 1.02F; // se abre en bruma
        this.alpha = 0.8F * (1.0F - life);
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
            return new SprayParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
