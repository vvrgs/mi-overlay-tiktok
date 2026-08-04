package com.vvrgs.cataclysm.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Busqueda de suelo con guard de vacio: en el End (o tras un TP al cielo)
 * jamas se re-ancla un desastre sobre el void.
 */
public final class Anchors {

    /**
     * Busca suelo firme cerca de un punto probando varios rumbos con heightmap.
     * @return posicion sobre el suelo, o null si no hay suelo (void).
     */
    @Nullable
    public static Vec3 findGroundNear(ServerLevel level, Vec3 near, int maxRadius) {
        // primero el punto exacto, luego anillos de 8 rumbos a radios crecientes
        Vec3 direct = groundAt(level, near.x, near.z);
        if (direct != null) {
            return direct;
        }
        for (int radius = 8; radius <= maxRadius; radius += 8) {
            for (int i = 0; i < 8; i++) {
                double angle = (Math.PI * 2.0D * i) / 8.0D;
                double x = near.x + Math.cos(angle) * radius;
                double z = near.z + Math.sin(angle) * radius;
                Vec3 ground = groundAt(level, x, z);
                if (ground != null) {
                    return ground;
                }
            }
        }
        return null;
    }

    /** Suelo en (x,z) o null si la columna esta vacia hasta el fondo del mundo. */
    @Nullable
    public static Vec3 groundAt(ServerLevel level, double x, double z) {
        BlockPos query = BlockPos.containing(x, 0, z);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                query.getX(), query.getZ());
        if (y <= level.getMinBuildHeight()) {
            return null; // columna vacia: void
        }
        return new Vec3(x, y, z);
    }

    /** Altura de suelo clampeada a los limites del mundo. */
    public static int surfaceY(ServerLevel level, int x, int z) {
        return Mth.clamp(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z),
                level.getMinBuildHeight() + 1, level.getMaxBuildHeight() - 1);
    }

    private Anchors() {
    }
}
