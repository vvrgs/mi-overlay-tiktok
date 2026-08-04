package net.minecraft.client.particle;

public abstract class SingleQuadParticle extends Particle {
    protected float quadSize = 0.1F;
    protected SingleQuadParticle(net.minecraft.client.multiplayer.ClientLevel level, double x, double y, double z) { super(level, x, y, z); }
    protected SingleQuadParticle(net.minecraft.client.multiplayer.ClientLevel level, double x, double y, double z, double xd, double yd, double zd) { super(level, x, y, z, xd, yd, zd); }
}
