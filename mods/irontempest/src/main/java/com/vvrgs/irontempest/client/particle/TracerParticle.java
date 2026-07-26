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
 * Trazadora: quad estirado a lo largo de la velocidad, orientado hacia la
 * cámara (billboard axial). Vida cortísima, full-bright.
 */
public class TracerParticle extends TextureSheetParticle {

    private static final double HALF_WIDTH = 0.06D;
    private static final double LENGTH = 0.9D;

    protected TracerParticle(ClientLevel level, double x, double y, double z,
                             double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.hasPhysics = false;
        this.lifetime = 3 + this.random.nextInt(3); // 3-5
        this.pickSprite(sprites);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        Vec3 cam = camera.getPosition();
        double px = Mth.lerp((double) partialTick, this.xo, this.x);
        double py = Mth.lerp((double) partialTick, this.yo, this.y);
        double pz = Mth.lerp((double) partialTick, this.zo, this.z);
        float fx = (float) (px - cam.x());
        float fy = (float) (py - cam.y());
        float fz = (float) (pz - cam.z());

        Vec3 dir = new Vec3(this.xd, this.yd, this.zd);
        dir = dir.lengthSqr() < 1.0E-8D ? new Vec3(0.0D, 1.0D, 0.0D) : dir.normalize();
        Vec3 view = new Vec3(fx, fy, fz).normalize();
        Vec3 side = dir.cross(view);
        if (side.lengthSqr() < 1.0E-8D) {
            side = dir.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (side.lengthSqr() < 1.0E-8D) {
                side = new Vec3(1.0D, 0.0D, 0.0D);
            }
        }
        side = side.normalize().scale(HALF_WIDTH);
        Vec3 tip = dir.scale(LENGTH);

        float sx = (float) side.x;
        float sy = (float) side.y;
        float sz = (float) side.z;
        float tx = (float) tip.x;
        float ty = (float) tip.y;
        float tz = (float) tip.z;

        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = this.getLightColor(partialTick);
        float a = this.alpha;

        // Doble cara: el cull no se toca en el pipeline de partículas.
        buffer.vertex(fx - sx, fy - sy, fz - sz).uv(u0, v1).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();
        buffer.vertex(fx + sx, fy + sy, fz + sz).uv(u1, v1).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();
        buffer.vertex(fx + tx + sx, fy + ty + sy, fz + tz + sz).uv(u1, v0).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();
        buffer.vertex(fx + tx - sx, fy + ty - sy, fz + tz - sz).uv(u0, v0).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();

        buffer.vertex(fx + tx - sx, fy + ty - sy, fz + tz - sz).uv(u0, v0).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();
        buffer.vertex(fx + tx + sx, fy + ty + sy, fz + tz + sz).uv(u1, v0).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();
        buffer.vertex(fx + sx, fy + sy, fz + sz).uv(u1, v1).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();
        buffer.vertex(fx - sx, fy - sy, fz - sz).uv(u0, v1).color(this.rCol, this.gCol, this.bCol, a).uv2(light).endVertex();
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
            return new TracerParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
