package com.vvrgs.cataclysm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Polvo balistico: sale despedido con gravedad real (fisuras, ondas, crateres). */
public class DustParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected DustParticle(ClientLevel level, double x, double y, double z,
                           double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0.55F;
        this.friction = 0.97F;
        this.lifetime = 24 + this.random.nextInt(20);
        this.quadSize = 0.10F + this.random.nextFloat() * 0.08F;
        float shade = 0.45F + this.random.nextFloat() * 0.25F;
        setColor(shade, shade * 0.92F, shade * 0.8F);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        if (this.age > this.lifetime - 8) {
            this.alpha = (this.lifetime - this.age) / 8.0F;
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
            return new DustParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
