package net.minecraft.core;

public class BlockPos extends Vec3i {
    public BlockPos(int x, int y, int z) { super(x, y, z); }
    public static BlockPos containing(double x, double y, double z) { throw new UnsupportedOperationException(); }
    public static BlockPos containing(Position position) { throw new UnsupportedOperationException(); }
    public BlockPos offset(int dx, int dy, int dz) { throw new UnsupportedOperationException(); }
    public BlockPos below() { throw new UnsupportedOperationException(); }
    public BlockPos above() { throw new UnsupportedOperationException(); }
    public BlockPos north() { throw new UnsupportedOperationException(); }
    public BlockPos south() { throw new UnsupportedOperationException(); }
    public BlockPos east() { throw new UnsupportedOperationException(); }
    public BlockPos west() { throw new UnsupportedOperationException(); }
}
