package com.vvrgs.cataclysm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Humo denso: CRECE al subir, se frena, oscurece el fondo. Vida larga. */
public class SmokeParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private final float baseSize;

    protected SmokeParticle(ClientLevel level, double x, double y, double z,
                            double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = -0.008F; // flota hacia arriba lentamente
        this.friction = 0.96F;
        this.lifetime = 80 + this.random.nextInt(60);
        this.baseSize = 0.30F + this.random.nextFloat() * 0.2F;
        this.quadSize = baseSize * 0.5F;
        float shade = 0.18F + this.random.nextFloat() * 0.16F;
        setColor(shade, shade, shade * 1.06F);
        this.alpha = 0.85F;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        float life = (float) this.age / this.lifetime;
        this.quadSize = baseSize * (0.5F + life * 1.6F); // crece al subir
        if (life > 0.7F) {
            this.alpha = 0.85F * (1.0F - (life - 0.7F) / 0.3F);
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
            return new SmokeParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
