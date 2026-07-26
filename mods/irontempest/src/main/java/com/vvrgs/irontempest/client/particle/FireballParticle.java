package com.vvrgs.irontempest.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Bola de fuego de explosión: animada por edad, asciende levemente, full-bright. */
public class FireballParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected FireballParticle(ClientLevel level, double x, double y, double z,
                               double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.gravity = 0.0F;
        this.friction = 0.86F;
        this.hasPhysics = false;
        this.lifetime = 8 + this.random.nextInt(7); // 8-14
        float speed = (float) Math.sqrt(xSpeed * xSpeed + ySpeed * ySpeed + zSpeed * zSpeed);
        this.quadSize = 0.9F + this.random.nextFloat() * 0.7F + speed * 0.4F;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isAlive()) {
            this.yd += 0.01D; // ascenso térmico
            this.setSpriteFromAge(this.sprites);
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = Mth.clamp((this.age + partialTick) / (float) this.lifetime, 0.0F, 1.0F);
        return this.quadSize * Mth.lerp(t, 1.0F, 1.35F);
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
            return new FireballParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
