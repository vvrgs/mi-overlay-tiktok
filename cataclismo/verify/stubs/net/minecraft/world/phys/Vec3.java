package net.minecraft.world.phys;

public class Vec3 {
    public static final Vec3 ZERO = new Vec3(0.0D, 0.0D, 0.0D);
    public final double x;
    public final double y;
    public final double z;
    public Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    public Vec3 add(Vec3 other) { throw new UnsupportedOperationException(); }
    public Vec3 add(double dx, double dy, double dz) { throw new UnsupportedOperationException(); }
    public Vec3 subtract(Vec3 other) { throw new UnsupportedOperationException(); }
    public Vec3 scale(double factor) { throw new UnsupportedOperationException(); }
    public Vec3 normalize() { throw new UnsupportedOperationException(); }
    public double length() { throw new UnsupportedOperationException(); }
    public double horizontalDistance() { throw new UnsupportedOperationException(); }
    public double distanceTo(Vec3 other) { throw new UnsupportedOperationException(); }
    public double distanceToSqr(Vec3 other) { throw new UnsupportedOperationException(); }
}
