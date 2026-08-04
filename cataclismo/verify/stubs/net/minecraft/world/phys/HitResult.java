package net.minecraft.world.phys;

public abstract class HitResult {
    public enum Type { MISS, BLOCK, ENTITY }
    public abstract Type getType();
    public Vec3 getLocation() { throw new UnsupportedOperationException(); }
}
