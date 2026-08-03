package com.vvrgs.irontempest.server.util;

import com.vvrgs.irontempest.config.WarConfig;
import com.vvrgs.irontempest.registry.ModDamage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class DamageUtil {

    /**
     * Daño letal estándar del skill: mata salvo tótem. NUNCA usa tipos que
     * bypaseen la invulnerabilidad, así el tótem de la inmortalidad SIEMPRE
     * puede salvar al jugador.
     */
    public static void killIfNoTotem(ServerLevel level, LivingEntity target,
                                     ResourceKey<DamageType> type, @Nullable Entity direct) {
        double mult = WarConfig.DAMAGE_MULTIPLIER.get();
        if (mult <= 0.0D) {
            return;
        }
        if (WarConfig.LETHAL_STRIKES.get()) {
            target.hurt(ModDamage.source(level, type, direct), 10000.0F);
        } else {
            target.hurt(ModDamage.source(level, type, direct), (float) (14.0F * mult));
        }
    }

    /** Daño radial con falloff (1 - d/r)^1.5. Los tipos is_explosion respetan Blast Protection. */
    public static void radialDamage(ServerLevel level, Vec3 center, double radius, float maxDamage,
                                    ResourceKey<DamageType> type, @Nullable Entity direct) {
        double mult = WarConfig.DAMAGE_MULTIPLIER.get();
        if (mult <= 0.0D) {
            return;
        }
        AABB box = new AABB(center, center).inflate(radius);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box)) {
            double dist = living.position().add(0.0D, living.getBbHeight() * 0.5D, 0.0D).distanceTo(center);
            if (dist > radius) {
                continue;
            }
            float falloff = (float) Math.pow(1.0D - dist / radius, 1.5D);
            float dmg = (float) (maxDamage * falloff * mult);
            if (dmg > 0.5F) {
                living.hurt(ModDamage.source(level, type, direct), dmg);
            }
        }
    }

    /** Letal en el epicentro (r_kill) + radial en el resto. */
    public static void strikeDamage(ServerLevel level, Vec3 center, double killRadius, double radius,
                                    float maxDamage, ResourceKey<DamageType> type, @Nullable Entity direct) {
        AABB box = new AABB(center, center).inflate(radius);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box)) {
            double dist = living.position().add(0.0D, living.getBbHeight() * 0.5D, 0.0D).distanceTo(center);
            if (dist <= killRadius) {
                killIfNoTotem(level, living, type, direct);
            }
        }
        radialDamage(level, center, radius, maxDamage, type, direct);
    }

    /**
     * FÍSICA de onda expansiva: empuja a todo ser vivo lejos del centro con
     * falloff, sesgo hacia arriba y sincronización de movimiento al cliente
     * (hurtMarked). Se siente el golpe, no solo se ve.
     */
    public static void blastImpulse(ServerLevel level, Vec3 center, double radius, double strength) {
        double k = WarConfig.KNOCKBACK_STRENGTH.get();
        if (k <= 0.0D) {
            return;
        }
        AABB box = new AABB(center, center).inflate(radius);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box)) {
            Vec3 to = living.position().add(0.0D, living.getBbHeight() * 0.5D, 0.0D).subtract(center);
            double dist = to.length();
            if (dist > radius) {
                continue;
            }
            double falloff = Math.pow(1.0D - dist / radius, 1.5D);
            Vec3 dir = dist > 0.05D ? to.normalize() : new Vec3(0.0D, 1.0D, 0.0D);
            Vec3 impulse = dir.scale(strength * k * falloff)
                    .add(0.0D, 0.35D * strength * k * falloff, 0.0D);
            // Techo vertical: espectacular, no un viaje a la luna.
            impulse = new Vec3(impulse.x, Math.min(impulse.y, 1.1D), impulse.z);
            living.setDeltaMovement(living.getDeltaMovement().add(impulse));
            living.hurtMarked = true; // forzar sync del motion al cliente (jugadores)
        }
    }

    private DamageUtil() {}
}
