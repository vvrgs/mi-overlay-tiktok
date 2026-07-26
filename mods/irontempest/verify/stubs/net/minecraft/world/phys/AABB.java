package net.minecraft.world.phys;

public class AABB {
    public AABB(double x1, double y1, double z1, double x2, double y2, double z2) {}
    public AABB(Vec3 min, Vec3 max) {}
    public AABB inflate(double amount) { throw new UnsupportedOperationException(); }
    public AABB expandTowards(Vec3 direction) { throw new UnsupportedOperationException(); }
}
