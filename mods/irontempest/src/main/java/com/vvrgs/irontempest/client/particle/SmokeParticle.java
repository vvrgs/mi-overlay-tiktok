package com.vvrgs.irontempest.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Humo: negro si viene de explosión (ySpeed &lt; 0.02) o gris claro si es
 * venteo/escape (ySpeed &gt;= 0.02). Luz normal — el humo se sombrea.
 */
public class SmokeParticle extends TextureSheetParticle {

    private static final int LIT_TICKS = 9;

    private final SpriteSet sprites;
    private final boolean darkSmoke;
    private final float baseShade;

    protected SmokeParticle(ClientLevel level, double x, double y, double z,
                            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.gravity = -0.02F; // asciende
        this.friction = 0.94F;
        this.lifetime = 30 + this.random.nextInt(41); // 30-70
        this.quadSize = 0.8F + this.random.nextFloat() * 1.0F; // 0.8-1.8
        this.darkSmoke = ySpeed < 0.02D;
        this.baseShade = this.darkSmoke
                ? 0.10F + this.random.nextFloat() * 0.10F  // humo negro de explosión
                : 0.55F + this.random.nextFloat() * 0.20F; // gris claro / polvo de venteo
        applyTint(0);
        this.setSpriteFromAge(sprites);
    }

    /**
     * El humo de explosión nace ILUMINADO por la bola de fuego (tinte naranja
     * que se apaga en los primeros ticks) — detalle que vende la cronología.
     */
    private void applyTint(int ageNow) {
        if (this.darkSmoke && ageNow < LIT_TICKS) {
            float lit = 1.0F - ageNow / (float) LIT_TICKS;
            this.rCol = this.baseShade + 0.85F * lit;
            this.gCol = this.baseShade + 0.42F * lit;
            this.bCol = this.baseShade + 0.10F * lit;
        } else {
            this.rCol = this.baseShade;
            this.gCol = this.baseShade;
            this.bCol = this.baseShade;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isAlive()) {
            this.setSpriteFromAge(this.sprites);
            applyTint(this.age);
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = Mth.clamp((this.age + partialTick) / (float) this.lifetime, 0.0F, 1.0F);
        return this.quadSize * Mth.lerp(t, 1.0F, 1.6F);
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
            return new SmokeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
