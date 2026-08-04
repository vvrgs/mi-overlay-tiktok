package net.minecraft.world.level;

public class ClipContext {
    public enum Block { COLLIDER, OUTLINE, VISUAL }
    public enum Fluid { NONE, SOURCE_ONLY, ANY }
    public ClipContext(net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to, Block block, Fluid fluid, net.minecraft.world.entity.Entity entity) {}
}
