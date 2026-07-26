package com.vvrgs.irontempest.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Anillo de choque: quad horizontal apoyado en el suelo que se expande con
 * easing out-cubic y gira lentamente. El spawner (FxDirector.spawn) pasa el
 * multiplicador de radio como zSpeed (si es <= 0 se usa 1.0); xSpeed/ySpeed
 * se ignoran.
 */
public class ShockwaveParticle extends TextureSheetParticle {

    private final float scaleParam;

    protected ShockwaveParticle(ClientLevel level, double x, double y, double z,
                                double scale, SpriteSet sprites) {
        super(level, x, y, z);
        this.scaleParam = scale > 0.0D ? (float) scale : 1.0F;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.lifetime = 14;
        this.pickSprite(sprites);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        Vec3 cam = camera.getPosition();
        float fx = (float) (Mth.lerp((double) partialTick, this.xo, this.x) - cam.x());
        float fy = (float) (Mth.lerp((double) partialTick, this.yo, this.y) - cam.y());
        float fz = (float) (Mth.lerp((double) partialTick, this.zo, this.z) - cam.z());

        float t = Mth.clamp((this.age + partialTick) / (float) this.lifetime, 0.0F, 1.0F);
        float inv = 1.0F - t;
        float eased = 1.0F - inv * inv * inv; // out-cubic
        float radius = 0.5F + (7.0F * this.scaleParam - 0.5F) * eased;
        float a = 0.85F * inv;

        float angle = (this.age + partialTick) * (5.0F * Mth.DEG_TO_RAD);
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);

        // Esquinas locales (±1, ±1) rotadas en el plano XZ y escaladas al radio.
        float x0 = (-cos + sin) * radius;
        float z0 = (-sin - cos) * radius;
        float x1 = (-cos - sin) * radius;
        float z1 = (-sin + cos) * radius;
        float x2 = (cos - sin) * radius;
        float z2 = (sin + cos) * radius;
        float x3 = (cos + sin) * radius;
        float z3 = (sin - cos) * radius;

        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = this.getLightColor(partialTick);

        // Doble cara para que sea visible desde arriba y desde abajo.
        buffer.vertex(fx + x0, fy, fz + z0).uv(u0, v1).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();
        buffer.vertex(fx + x1, fy, fz + z1).uv(u0, v0).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();
        buffer.vertex(fx + x2, fy, fz + z2).uv(u1, v0).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();
        buffer.vertex(fx + x3, fy, fz + z3).uv(u1, v1).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();

        buffer.vertex(fx + x3, fy, fz + z3).uv(u1, v1).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();
        buffer.vertex(fx + x2, fy, fz + z2).uv(u1, v0).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();
        buffer.vertex(fx + x1, fy, fz + z1).uv(u0, v0).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();
        buffer.vertex(fx + x0, fy, fz + z0).uv(u0, v1).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();
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
            return new ShockwaveParticle(level, x, y, z, zSpeed, this.sprites);
        }
    }
}
