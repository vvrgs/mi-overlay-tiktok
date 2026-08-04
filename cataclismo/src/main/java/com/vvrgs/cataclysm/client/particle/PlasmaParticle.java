package com.vvrgs.cataclysm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Plasma de bolido: brillante, encoge rapido, blanco->naranja->rojo. */
public class PlasmaParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private final float baseSize;

    protected PlasmaParticle(ClientLevel level, double x, double y, double z,
                             double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0.0F;
        this.friction = 0.90F;
        this.lifetime = 10 + this.random.nextInt(8);
        this.baseSize = 0.14F + this.random.nextFloat() * 0.10F;
        this.quadSize = baseSize;
        setColor(1.0F, 0.95F, 0.85F);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        float life = (float) this.age / this.lifetime;
        this.quadSize = baseSize * (1.0F - life * 0.8F);
        // blanco -> naranja -> rojo al enfriarse
        this.gCol = Math.max(0.2F, 0.95F - life * 0.8F);
        this.bCol = Math.max(0.05F, 0.85F - life * 1.4F);
        this.alpha = 1.0F - life * life;
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
            return new PlasmaParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
