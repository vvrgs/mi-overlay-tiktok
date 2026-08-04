package com.vvrgs.cataclysm.core;

import com.vvrgs.cataclysm.CataclysmConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Impulso fisico real: setDeltaMovement + hurtMarked=true para sincronizar
 * al cliente. El viento es fuerza SOSTENIDA por tick, no un empujon.
 */
public final class Physics {

    private static final double MAX_WIND_SPEED = 1.8D;

    /** Onda expansiva con falloff lineal y sesgo vertical. */
    public static void blast(Entity entity, Vec3 center, double radius, double strength, double verticalBias) {
        Vec3 delta = entity.position().subtract(center);
        double dist = delta.length();
        if (dist > radius) {
            return;
        }
        double falloff = 1.0D - dist / radius;
        double power = strength * falloff * CataclysmConfig.COMMON.knockbackStrength.get();
        Vec3 dir = dist < 0.01D ? new Vec3(0, 1, 0) : delta.scale(1.0D / dist);
        Vec3 push = new Vec3(dir.x * power, Math.abs(dir.y) * power * 0.3D + verticalBias * falloff, dir.z * power);
        entity.setDeltaMovement(entity.getDeltaMovement().add(push));
        entity.hurtMarked = true;
    }

    /** Viento sostenido por tick, con cap de velocidad horizontal. */
    public static void wind(Entity entity, Vec3 force) {
        Vec3 scaled = force.scale(CataclysmConfig.COMMON.windStrength.get());
        Vec3 motion = entity.getDeltaMovement().add(scaled);
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontal > MAX_WIND_SPEED) {
            double f = MAX_WIND_SPEED / horizontal;
            motion = new Vec3(motion.x * f, motion.y, motion.z * f);
        }
        entity.setDeltaMovement(motion);
        entity.hurtMarked = true;
    }

    /** Succion hacia un punto con componente tangencial (tornado, grieta). */
    public static void suction(Entity entity, Vec3 center, double radius,
                               double pullStrength, double tangentialStrength, double lift) {
        Vec3 delta = center.subtract(entity.position());
        double dist = delta.length();
        if (dist > radius || dist < 0.01D) {
            return;
        }
        double falloff = 1.0D - dist / radius;
        Vec3 toCenter = delta.scale(1.0D / dist);
        // tangencial: perpendicular en el plano XZ (giro antihorario)
        Vec3 tangent = new Vec3(-toCenter.z, 0.0D, toCenter.x);
        Vec3 force = toCenter.scale(pullStrength * falloff)
                .add(tangent.scale(tangentialStrength * falloff))
                .add(0.0D, lift * falloff, 0.0D);
        wind(entity, force);
    }

    private Physics() {
    }
}
