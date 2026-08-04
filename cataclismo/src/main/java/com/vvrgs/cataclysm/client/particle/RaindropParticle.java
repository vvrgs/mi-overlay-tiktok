package com.vvrgs.cataclysm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Gota de lluvia de la celula de tormenta: cae rapido y muere al tocar algo. */
public class RaindropParticle extends TextureSheetParticle {

    protected RaindropParticle(ClientLevel level, double x, double y, double z,
                               double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 1.0F;
        this.friction = 1.0F;
        this.hasPhysics = true; // muere al tocar suelo
        this.lifetime = 18 + this.random.nextInt(8);
        this.quadSize = 0.04F + this.random.nextFloat() * 0.02F;
        setColor(0.55F, 0.65F, 0.85F);
        this.alpha = 0.75F;
        setSprite(sprites.get(this.random));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.onGround) {
            remove();
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
            return new RaindropParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
