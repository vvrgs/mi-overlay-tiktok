package com.vvrgs.cataclysm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Mota electrica: VIBRA cada tick (jitter nervioso), azul-blanca, breve. */
public class SparkParticle extends TextureSheetParticle {

    protected SparkParticle(ClientLevel level, double x, double y, double z,
                            double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = -0.004F;
        this.friction = 0.92F;
        this.lifetime = 10 + this.random.nextInt(10);
        this.quadSize = 0.05F + this.random.nextFloat() * 0.04F;
        setColor(0.75F, 0.85F, 1.0F);
        setSprite(sprites.get(this.random));
    }

    @Override
    public void tick() {
        super.tick();
        // vibracion electrica
        this.xd += (this.random.nextDouble() - 0.5D) * 0.045D;
        this.zd += (this.random.nextDouble() - 0.5D) * 0.045D;
        this.alpha = this.random.nextFloat() < 0.2F ? 0.3F : 1.0F; // estrobo
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
            return new SparkParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
