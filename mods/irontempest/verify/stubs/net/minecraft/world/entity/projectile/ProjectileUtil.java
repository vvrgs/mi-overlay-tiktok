package net.minecraft.world.entity.projectile;

import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class ProjectileUtil {
    @Nullable
    public static EntityHitResult getEntityHitResult(Level level, Entity projectile, Vec3 startVec, Vec3 endVec,
                                                     AABB boundingBox, Predicate<Entity> filter) {
        throw new UnsupportedOperationException();
    }

    private ProjectileUtil() {}
}
