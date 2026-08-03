package com.vvrgs.irontempest.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Escombro sólido: 8 formas aleatorias, gira mientras vuela, colisiona y
 * rebota amortiguado contra el suelo. Luz normal.
 */
public class DebrisParticle extends TextureSheetParticle {

    protected DebrisParticle(ClientLevel level, double x, double y, double z,
                             double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.gravity = 0.12F;
        this.hasPhysics = true;
        this.lifetime = 40 + this.random.nextInt(41); // 40-80
        this.quadSize = 0.15F + this.random.nextFloat() * 0.25F;
        this.roll = this.random.nextFloat() * ((float) Math.PI * 2.0F);
        this.oRoll = this.roll;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        // yd se captura ANTES de super.tick(): el Particle base la anula al chocar.
        double lastYd = this.yd;
        super.tick();
        if (!this.isAlive()) {
            return;
        }
        if (this.onGround && Math.abs(lastYd) > 0.1D) {
            // Rebote amortiguado.
            this.setParticleSpeed(this.xd * 0.6D, -lastYd * 0.45D, this.zd * 0.6D);
            // FÍSICA: chispa de impacto al rebotar contra el suelo.
            this.level.addParticle(com.vvrgs.irontempest.registry.ModParticles.SPARK.get(),
                    this.x, this.y + 0.05D, this.z,
                    this.xd * 0.5D, 0.12D, this.zd * 0.5D);
        } else if (!this.onGround) {
            this.roll += 0.3F;
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
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new DebrisParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
