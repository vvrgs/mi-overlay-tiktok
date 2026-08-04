package com.vvrgs.cataclysm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Escombro solido: trozo de bloque GIRANDO (roll), balistico, variantes de sprite. */
public class DebrisParticle extends TextureSheetParticle {

    private final float rollSpeed;

    protected DebrisParticle(ClientLevel level, double x, double y, double z,
                             double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0.50F;
        this.friction = 0.985F;
        this.lifetime = 34 + this.random.nextInt(22);
        this.quadSize = 0.12F + this.random.nextFloat() * 0.10F;
        float shade = 0.5F + this.random.nextFloat() * 0.3F;
        setColor(shade, shade * 0.9F, shade * 0.75F);
        this.rollSpeed = (this.random.nextFloat() - 0.5F) * 0.8F;
        setSprite(sprites.get(this.random)); // variante estable por particula
    }

    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        this.roll += rollSpeed; // giro visible del escombro
        if (this.age > this.lifetime - 10) {
            this.alpha = (this.lifetime - this.age) / 10.0F;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        // translucido: el fade de alpha del final necesita blending real
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
            return new DebrisParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
