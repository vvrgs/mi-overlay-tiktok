package net.minecraft.core;

public class BlockPos extends Vec3i {
    public BlockPos(int x, int y, int z) { super(x, y, z); }
    public static BlockPos containing(double x, double y, double z) { throw new UnsupportedOperationException(); }
    public static BlockPos containing(net.minecraft.world.phys.Vec3 pos) { throw new UnsupportedOperationException(); }
    public static Iterable<BlockPos> betweenClosed(BlockPos from, BlockPos to) { throw new UnsupportedOperationException(); }
    public static BlockPos of(long packed) { throw new UnsupportedOperationException(); }
    public long asLong() { throw new UnsupportedOperationException(); }
    public BlockPos offset(int dx, int dy, int dz) { throw new UnsupportedOperationException(); }
    public BlockPos below() { throw new UnsupportedOperationException(); }
    public BlockPos immutable() { throw new UnsupportedOperationException(); }
}
