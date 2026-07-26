package com.vvrgs.irontempest.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Chispa balística incandescente: cae, rebota una fracción, full-bright. */
public class SparkParticle extends TextureSheetParticle {

    protected SparkParticle(ClientLevel level, double x, double y, double z,
                            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.gravity = 0.06F;
        this.friction = 0.91F;
        this.hasPhysics = true;
        this.lifetime = 8 + this.random.nextInt(13); // 8-20
        this.quadSize = 0.12F + this.random.nextFloat() * 0.13F;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        // Se captura yd ANTES de super.tick(): la colisión del Particle base la anula.
        double lastYd = this.yd;
        super.tick();
        if (this.isAlive() && this.onGround && lastYd < -0.01D) {
            this.yd = -lastYd * 0.4D;
        }
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
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
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new SparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
