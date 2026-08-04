package net.minecraftforge.network;

public class PacketDistributor<T> {
    public static final class TargetPoint {
        public TargetPoint(double x, double y, double z, double radius, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}
    }
    public static final class PacketTarget {}
    public static final PacketDistributor<TargetPoint> NEAR = new PacketDistributor<>();
    public static final PacketDistributor<Void> ALL = new PacketDistributor<>();
    public PacketTarget with(java.util.function.Supplier<T> arg) { throw new UnsupportedOperationException(); }
    public PacketTarget noArg() { throw new UnsupportedOperationException(); }
}
