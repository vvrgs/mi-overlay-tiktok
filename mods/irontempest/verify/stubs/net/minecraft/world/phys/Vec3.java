package net.minecraft.world.phys;

import net.minecraft.core.Position;

public class Vec3 implements Position {
    public static final Vec3 ZERO = new Vec3(0.0D, 0.0D, 0.0D);

    public final double x;
    public final double y;
    public final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public double x() { return this.x; }
    @Override
    public double y() { return this.y; }
    @Override
    public double z() { return this.z; }

    public Vec3 add(Vec3 other) { throw new UnsupportedOperationException(); }
    public Vec3 add(double dx, double dy, double dz) { throw new UnsupportedOperationException(); }
    public Vec3 subtract(Vec3 other) { throw new UnsupportedOperationException(); }
    public Vec3 subtract(double dx, double dy, double dz) { throw new UnsupportedOperationException(); }
    public Vec3 multiply(double fx, double fy, double fz) { throw new UnsupportedOperationException(); }
    public Vec3 scale(double factor) { throw new UnsupportedOperationException(); }
    public Vec3 normalize() { throw new UnsupportedOperationException(); }
    public Vec3 cross(Vec3 other) { throw new UnsupportedOperationException(); }
    public Vec3 lerp(Vec3 to, double delta) { throw new UnsupportedOperationException(); }
    public double dot(Vec3 other) { return 0.0D; }
    public double length() { return 0.0D; }
    public double lengthSqr() { return 0.0D; }
    public double horizontalDistance() { return 0.0D; }
    public double distanceTo(Vec3 other) { return 0.0D; }
    public double distanceToSqr(Vec3 other) { return 0.0D; }
}
