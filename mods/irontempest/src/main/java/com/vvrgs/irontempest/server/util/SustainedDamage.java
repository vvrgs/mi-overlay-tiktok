package com.vvrgs.irontempest.server.util;

import com.vvrgs.irontempest.IronTempest;
import com.vvrgs.irontempest.config.WarConfig;
import com.vvrgs.irontempest.net.FxType;
import com.vvrgs.irontempest.net.ModNetwork;
import com.vvrgs.irontempest.registry.ModDamage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Daño SOSTENIDO: cada impacto grande deja el terreno ardiendo (Zone) y a los
 * alcanzados con una aflicción que pulsa daño cada 10 ticks (la ventana de
 * invulnerabilidad vanilla) durante 3-8 s. Se tickea desde SessionManager y se
 * limpia con los mismos 3 hooks de cleanup.
 */
public final class SustainedDamage {

    private static final int PULSE_INTERVAL = 10;
    private static final int FX_INTERVAL = 15;

    private static final class Zone {
        final ServerLevel level;
        final Vec3 pos;
        final double radius;
        final float pulseDamage;
        final ResourceKey<DamageType> type;
        final boolean fire;
        int ticksLeft;

        Zone(ServerLevel level, Vec3 pos, double radius, int ticks, float pulseDamage,
             ResourceKey<DamageType> type, boolean fire) {
            this.level = level;
            this.pos = pos;
            this.radius = radius;
            this.ticksLeft = ticks;
            this.pulseDamage = pulseDamage;
            this.type = type;
            this.fire = fire;
        }
    }

    private static final class Affliction {
        final ServerLevel level;
        final UUID target;
        final float pulseDamage;
        final ResourceKey<DamageType> type;
        final boolean fire;
        int ticksLeft;
        boolean expired;

        Affliction(ServerLevel level, UUID target, int ticks, float pulseDamage,
                   ResourceKey<DamageType> type, boolean fire) {
            this.level = level;
            this.target = target;
            this.ticksLeft = ticks;
            this.pulseDamage = pulseDamage;
            this.type = type;
            this.fire = fire;
        }
    }

    private static final List<Zone> ZONES = new ArrayList<>();
    private static final List<Affliction> AFFLICTIONS = new ArrayList<>();
    private static final int MAX_ZONES = 96;
    private static int clock;

    /** Zona ardiente en el cráter: pulsa daño a quien la pise durante {@code seconds}. */
    public static void zone(ServerLevel level, Vec3 pos, double radius, double seconds,
                            float pulseDamage, ResourceKey<DamageType> type, boolean fire) {
        if (!WarConfig.SUSTAINED_DAMAGE.get()) {
            return;
        }
        if (ZONES.size() >= MAX_ZONES) {
            ZONES.remove(0);
        }
        ZONES.add(new Zone(level, pos, radius, (int) (seconds * 20.0D), pulseDamage, type, fire));
    }

    /** Aflicción directa sobre una entidad: daño que la persigue aunque corra. */
    public static void afflict(ServerLevel level, LivingEntity target, double seconds,
                               float pulseDamage, ResourceKey<DamageType> type, boolean fire) {
        if (!WarConfig.SUSTAINED_DAMAGE.get()) {
            return;
        }
        // Renovar en vez de apilar: una sola aflicción por objetivo y tipo.
        for (Affliction a : AFFLICTIONS) {
            if (a.target.equals(target.getUUID()) && a.type == type) {
                a.ticksLeft = Math.max(a.ticksLeft, (int) (seconds * 20.0D));
                return;
            }
        }
        AFFLICTIONS.add(new Affliction(level, target.getUUID(), (int) (seconds * 20.0D),
                pulseDamage, type, fire));
    }

    /** Aflige a todo ser vivo en el radio (con falloff en la duración). */
    public static void afflictArea(ServerLevel level, Vec3 center, double radius, double seconds,
                                   float pulseDamage, ResourceKey<DamageType> type, boolean fire) {
        AABB box = new AABB(center, center).inflate(radius);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box)) {
            double dist = living.position().distanceTo(center);
            if (dist <= radius) {
                double falloff = 1.0D - 0.5D * (dist / radius);
                afflict(level, living, seconds * falloff, pulseDamage, type, fire);
            }
        }
    }

    /** Llamar UNA vez por tick de servidor (desde SessionManager). */
    public static void serverTick() {
        if (ZONES.isEmpty() && AFFLICTIONS.isEmpty()) {
            clock = 0;
            return;
        }
        clock++;
        float mult = (float) (WarConfig.DAMAGE_MULTIPLIER.get() * WarConfig.DOT_MULTIPLIER.get());

        for (Zone z : new ArrayList<>(ZONES)) {
            if (--z.ticksLeft <= 0) {
                continue;
            }
            if (clock % FX_INTERVAL == 0) {
                // Suelo ardiendo VISIBLE mientras la zona vive.
                ModNetwork.fx(z.level, FxType.SCORCH, z.pos, (float) (z.radius / 3.0D));
            }
            if (clock % PULSE_INTERVAL == 0 && mult > 0.0F) {
                AABB box = new AABB(z.pos, z.pos).inflate(z.radius);
                for (LivingEntity living : z.level.getEntitiesOfClass(LivingEntity.class, box)) {
                    if (living.position().distanceTo(z.pos) <= z.radius) {
                        living.hurt(ModDamage.source(z.level, WarConfig.sustainedType(z.type), null),
                                z.pulseDamage * mult);
                        if (z.fire) {
                            living.setSecondsOnFire(2);
                        }
                    }
                }
            }
        }
        ZONES.removeIf(z -> z.ticksLeft <= 0);

        if (clock % PULSE_INTERVAL != 0) {
            return;
        }
        // SNAPSHOT anti-CME: un hurt puede matar → LivingDeathEvent → removeFor()
        // reentra en AFFLICTIONS dentro de este mismo tick.
        for (Affliction a : new ArrayList<>(AFFLICTIONS)) {
            a.ticksLeft -= PULSE_INTERVAL;
            Entity e = a.level.getEntity(a.target);
            if (a.ticksLeft <= 0 || !(e instanceof LivingEntity living) || !living.isAlive()) {
                a.expired = true;
                continue;
            }
            if (mult > 0.0F) {
                living.hurt(ModDamage.source(a.level, WarConfig.sustainedType(a.type), null),
                        a.pulseDamage * mult);
                if (a.fire) {
                    living.setSecondsOnFire(2);
                }
            }
        }
        AFFLICTIONS.removeIf(a -> a.expired || a.ticksLeft <= 0);
    }

    /** Cleanup duro (ServerStopping / stopall). */
    public static void clearAll() {
        int n = ZONES.size() + AFFLICTIONS.size();
        ZONES.clear();
        AFFLICTIONS.clear();
        if (n > 0) {
            IronTempest.LOGGER.info("[irontempest] SustainedDamage: {} zonas/aflicciones limpiadas", n);
        }
    }

    /** El objetivo murió o se desconectó: sus aflicciones mueren con él. */
    public static void removeFor(UUID target) {
        AFFLICTIONS.removeIf(a -> a.target.equals(target));
    }

    private SustainedDamage() {}
}
