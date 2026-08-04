package com.vvrgs.cataclysm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Destello de pop de totem: burst dorado-verde propio, expande y muere rapido. */
public class PopFlashParticle extends TextureSheetParticle {

    private final float baseSize;

    protected PopFlashParticle(ClientLevel level, double x, double y, double z,
                               double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0.02F;
        this.friction = 0.90F;
        this.lifetime = 9 + this.random.nextInt(5);
        this.baseSize = 0.12F + this.random.nextFloat() * 0.08F;
        this.quadSize = baseSize * 0.4F;
        // dorado-verde: el color del totem, pero PROPIO
        if (this.random.nextBoolean()) {
            setColor(1.0F, 0.85F, 0.25F);
        } else {
            setColor(0.45F, 0.95F, 0.35F);
        }
        setSprite(sprites.get(this.random));
    }

    @Override
    public void tick() {
        super.tick();
        float life = (float) this.age / this.lifetime;
        this.quadSize = baseSize * (0.4F + life * 1.4F);
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
            return new PopFlashParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
