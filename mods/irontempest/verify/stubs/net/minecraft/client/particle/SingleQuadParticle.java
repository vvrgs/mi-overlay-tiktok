package net.minecraft.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;

public abstract class SingleQuadParticle extends Particle {
    protected float quadSize = 0.1F;

    protected SingleQuadParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    protected SingleQuadParticle(ClientLevel level, double x, double y, double z,
                                 double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {}

    public float getQuadSize(float partialTick) { return this.quadSize; }

    @Override
    public Particle scale(float scale) { return this; }

    protected abstract float getU0();
    protected abstract float getU1();
    protected abstract float getV0();
    protected abstract float getV1();
}
