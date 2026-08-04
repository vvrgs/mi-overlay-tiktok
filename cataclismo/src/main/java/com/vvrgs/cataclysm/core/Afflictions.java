package com.vvrgs.cataclysm.core;

import com.vvrgs.cataclysm.CataclysmConfig;
import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.registry.ModDamageTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aflicciones: dano sostenido que PERSIGUE a la entidad (pulso cada 10 ticks
 * aunque corra o lo teletransporten — resolucion via playerlist). El napalm
 * sigue quemando en el End.
 */
public final class Afflictions {

    public enum Kind {
        NAPALM(ModDamageTypes.NAPALM, FxEvent.EMBER_FIELD, true),
        ASH(ModDamageTypes.ASH, FxEvent.ASH_FALL, false),
        STORM_SOAK(ModDamageTypes.LIGHTNING, FxEvent.RAIN_CELL, false);

        final ResourceKey<DamageType> damage;
        final FxEvent fx;
        final boolean ignite;

        Kind(ResourceKey<DamageType> damage, FxEvent fx, boolean ignite) {
            this.damage = damage;
            this.fx = fx;
            this.ignite = ignite;
        }
    }

    private static final int PULSE_INTERVAL = 10;

    private static final class Affliction {
        final Kind kind;
        float damagePerPulse;
        int ticksLeft;
        int pulseTimer;

        Affliction(Kind kind, float damagePerPulse, int duration) {
            this.kind = kind;
            this.damagePerPulse = damagePerPulse;
            this.ticksLeft = duration;
            this.pulseTimer = PULSE_INTERVAL;
        }
    }

    private static final Map<UUID, List<Affliction>> ACTIVE = new HashMap<>();

    /** Aplica (o extiende) una afliccion. Merge: gana el dano mayor, se suma duracion con cap. */
    public static void apply(UUID target, Kind kind, float damagePerPulse, int durationTicks) {
        if (!CataclysmConfig.COMMON.sustainedDamage.get()) {
            return;
        }
        List<Affliction> list = ACTIVE.computeIfAbsent(target, k -> new ArrayList<>());
        for (Affliction a : list) {
            if (a.kind == kind) {
                a.damagePerPulse = Math.max(a.damagePerPulse, damagePerPulse);
                a.ticksLeft = Math.min(a.ticksLeft + durationTicks, 20 * 120);
                return;
            }
        }
        list.add(new Affliction(kind, damagePerPulse, durationTicks));
    }

    public static void clear(UUID target) {
        ACTIVE.remove(target);
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        // SNAPSHOT de claves: un pulso puede matar -> clear() reentrante
        for (UUID id : new ArrayList<>(ACTIVE.keySet())) {
            List<Affliction> list = ACTIVE.get(id);
            if (list == null || list.isEmpty()) {
                ACTIVE.remove(id);
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || player.isRemoved() || !player.isAlive()) {
                ACTIVE.remove(id);
                continue;
            }
            for (Affliction a : new ArrayList<>(list)) {
                a.ticksLeft--;
                if (--a.pulseTimer <= 0) {
                    a.pulseTimer = PULSE_INTERVAL;
                    pulse(player, a);
                    if (!ACTIVE.containsKey(id)) {
                        break; // el pulso mato al jugador: limpieza reentrante ya paso
                    }
                }
            }
            List<Affliction> current = ACTIVE.get(id);
            if (current != null) {
                Iterator<Affliction> it = current.iterator();
                while (it.hasNext()) {
                    if (it.next().ticksLeft <= 0) {
                        it.remove();
                    }
                }
                if (current.isEmpty()) {
                    ACTIVE.remove(id);
                }
            }
        }
    }

    private static void pulse(ServerPlayer player, Affliction a) {
        float damage = (float) (a.damagePerPulse * CataclysmConfig.COMMON.dotMultiplier.get());
        if (damage <= 0.0F) {
            return;
        }
        if (a.kind.ignite) {
            player.setSecondsOnFire(2);
        }
        player.invulnerableTime = 0;
        player.hurt(ModDamageTypes.source(player.level(), a.kind.damage), damage);
        if (player.isAlive()) {
            FxDirector.fire(player.serverLevel(), a.kind.fx, player.position(), 0.5F);
        }
    }

    private Afflictions() {
    }
}
