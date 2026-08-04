package com.vvrgs.cataclysm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Brasa incandescente: sube con drag alto, PARPADEA, muere apagandose. */
public class EmberParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected EmberParticle(ClientLevel level, double x, double y, double z,
                            double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = -0.02F; // asciende
        this.friction = 0.91F;
        this.lifetime = 35 + this.random.nextInt(30);
        this.quadSize = 0.05F + this.random.nextFloat() * 0.05F;
        setColor(1.0F, 0.55F + this.random.nextFloat() * 0.25F, 0.15F);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        // parpadeo + enfriamiento hacia rojo oscuro
        float life = (float) this.age / this.lifetime;
        this.alpha = (0.75F + 0.25F * Mth.sin(this.age * 1.3F)) * (1.0F - life * 0.7F);
        this.gCol = Math.max(0.1F, this.gCol - 0.012F);
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
            return new EmberParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
