package com.vvrgs.cataclysm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Mota dorada de telegraph: pulsa y converge — receta visual DISTINTA de una explosion. */
public class TelegraphParticle extends TextureSheetParticle {

    protected TelegraphParticle(ClientLevel level, double x, double y, double z,
                                double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0.0F;
        this.friction = 0.99F;
        this.lifetime = 20 + this.random.nextInt(8);
        this.quadSize = 0.07F + this.random.nextFloat() * 0.03F;
        setColor(1.0F, 0.85F, 0.3F);
        setSprite(sprites.get(this.random));
    }

    @Override
    public void tick() {
        super.tick();
        this.alpha = 0.6F + 0.4F * Mth.sin(this.age * 0.9F); // pulso
        if (this.age > this.lifetime - 6) {
            this.quadSize *= 0.85F;
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
            return new TelegraphParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
