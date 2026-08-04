package com.vvrgs.cataclysm.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Globo de lava de las fuentes: balistico, incandescente, se enfria al caer. */
public class LavaGlobParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected LavaGlobParticle(ClientLevel level, double x, double y, double z,
                               double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.gravity = 0.45F;
        this.friction = 0.98F;
        this.lifetime = 26 + this.random.nextInt(12);
        this.quadSize = 0.14F + this.random.nextFloat() * 0.10F;
        setColor(1.0F, 0.75F, 0.2F);
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
        float life = (float) this.age / this.lifetime;
        // se enfria: naranja -> rojo -> oscuro
        this.gCol = Math.max(0.15F, 0.75F - life * 0.7F);
        this.rCol = Math.max(0.4F, 1.0F - life * 0.5F);
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
            return new LavaGlobParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
