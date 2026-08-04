package net.minecraft.client.particle;

public abstract class Particle {
    protected final net.minecraft.client.multiplayer.ClientLevel level;
    protected final net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create();
    protected int age;
    protected int lifetime;
    protected float gravity;
    protected float friction = 0.98F;
    protected boolean hasPhysics = true;
    protected boolean onGround;
    protected double xd;
    protected double yd;
    protected double zd;
    protected float alpha = 1.0F;
    protected float rCol = 1.0F;
    protected float gCol = 1.0F;
    protected float bCol = 1.0F;
    protected float roll;
    protected float oRoll;
    protected Particle(net.minecraft.client.multiplayer.ClientLevel level, double x, double y, double z) { this.level = level; }
    protected Particle(net.minecraft.client.multiplayer.ClientLevel level, double x, double y, double z, double xd, double yd, double zd) { this.level = level; }
    public void tick() {}
    public void remove() {}
    protected void setColor(float r, float g, float b) {}
    protected void setAlpha(float alpha) {}
    public abstract ParticleRenderType getRenderType();
}
