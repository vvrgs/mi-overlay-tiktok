package com.vvrgs.irontempest.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Brasa flotante: parpadea cambiando de sprite cada 3 ticks, deriva con el
 * viento y se apaga (fade de alpha) en el último 20% de su vida. Full-bright.
 */
public class EmberParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected EmberParticle(ClientLevel level, double x, double y, double z,
                            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.gravity = 0.02F;
        this.friction = 0.96F;
        this.lifetime = 30 + this.random.nextInt(61); // 30-90
        this.quadSize = 0.08F + this.random.nextFloat() * 0.10F;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isAlive()) {
            return;
        }
        // Deriva de viento suave.
        this.xd += Math.sin(this.age * 0.1D) * 0.001D;
        // Parpadeo: nuevo frame aleatorio cada 3 ticks.
        if (this.age % 3 == 0) {
            this.pickSprite(this.sprites);
        }
        // Fade en el último 20% de vida.
        int fadeStart = (int) (this.lifetime * 0.8F);
        if (this.age > fadeStart) {
            this.alpha = 1.0F - (float) (this.age - fadeStart) / (float) (this.lifetime - fadeStart);
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
            return new EmberParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
