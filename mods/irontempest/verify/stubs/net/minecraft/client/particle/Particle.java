package net.minecraft.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;

public abstract class Particle {
    protected final ClientLevel level;
    protected final RandomSource random = null;
    protected double xo;
    protected double yo;
    protected double zo;
    protected double x;
    protected double y;
    protected double z;
    protected double xd;
    protected double yd;
    protected double zd;
    protected boolean onGround;
    protected boolean hasPhysics = true;
    protected boolean stoppedByCollision;
    protected int age;
    protected int lifetime;
    protected float gravity;
    protected float rCol = 1.0F;
    protected float gCol = 1.0F;
    protected float bCol = 1.0F;
    protected float alpha = 1.0F;
    protected float roll;
    protected float oRoll;
    protected float friction = 0.98F;

    protected Particle(ClientLevel level, double x, double y, double z) {
        this.level = level;
    }

    protected Particle(ClientLevel level, double x, double y, double z,
                       double xSpeed, double ySpeed, double zSpeed) {
        this.level = level;
    }

    public void tick() {}
    public abstract void render(VertexConsumer buffer, Camera camera, float partialTick);
    public abstract ParticleRenderType getRenderType();

    public boolean isAlive() { return false; }
    public void remove() {}
    public void setParticleSpeed(double xd, double yd, double zd) {}
    public void setLifetime(int lifetime) {}
    public int getLifetime() { return 0; }
    public Particle scale(float scale) { return this; }
    public void setColor(float r, float g, float b) {}
    protected void setAlpha(float alpha) {}
    protected int getLightColor(float partialTick) { return 0; }
}
