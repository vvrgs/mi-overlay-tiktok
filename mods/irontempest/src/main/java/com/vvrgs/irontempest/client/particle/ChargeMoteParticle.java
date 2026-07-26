package com.vvrgs.irontempest.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Mota de carga de energía: el spawner fija la velocidad (convergen al cañón);
 * aquí solo se mantiene sin fricción ni gravedad, con pulso de 4 frames.
 */
public class ChargeMoteParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private final double targetXd;
    private final double targetYd;
    private final double targetZd;

    protected ChargeMoteParticle(ClientLevel level, double x, double y, double z,
                                 double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.targetXd = xSpeed;
        this.targetYd = ySpeed;
        this.targetZd = zSpeed;
        this.gravity = 0.0F;
        this.friction = 1.0F; // sin fricción: la convergencia la dicta el spawner
        this.hasPhysics = false;
        this.lifetime = 12 + this.random.nextInt(9); // 12-20
        this.quadSize = 0.2F + this.random.nextFloat() * 0.15F;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isAlive()) {
            // Leve corrección hacia la velocidad original: mantiene la convergencia.
            this.xd += (this.targetXd - this.xd) * 0.15D;
            this.yd += (this.targetYd - this.yd) * 0.15D;
            this.zd += (this.targetZd - this.zd) * 0.15D;
            this.setSpriteFromAge(this.sprites);
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
            return new ChargeMoteParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
