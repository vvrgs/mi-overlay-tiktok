package net.minecraft.world.level;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class ClipContext {
    public ClipContext(Vec3 from, Vec3 to, Block block, Fluid fluid, Entity entity) {}

    public enum Block {
        COLLIDER,
        OUTLINE,
        VISUAL,
        FALLDAMAGE_RESETTING
    }

    public enum Fluid {
        NONE,
        SOURCE_ONLY,
        ANY,
        WATER
    }
}
