package net.minecraftforge.network;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public class PacketDistributor<T> {
    public static final PacketDistributor<ServerPlayer> PLAYER = new PacketDistributor<>();
    public static final PacketDistributor<ResourceKey<Level>> DIMENSION = new PacketDistributor<>();
    public static final PacketDistributor<TargetPoint> NEAR = new PacketDistributor<>();
    public static final PacketDistributor<Void> ALL = new PacketDistributor<>();

    public PacketTarget with(Supplier<T> arg) { throw new UnsupportedOperationException(); }
    public PacketTarget noArg() { throw new UnsupportedOperationException(); }

    public static class TargetPoint {
        public TargetPoint(double x, double y, double z, double r2, ResourceKey<Level> dim) {}
    }

    public static class PacketTarget {
    }
}
