package com.vvrgs.irontempest.server.session;

import com.vvrgs.irontempest.config.WarConfig;
import com.vvrgs.irontempest.entity.MlrsRocketEntity;
import com.vvrgs.irontempest.net.FxType;
import com.vvrgs.irontempest.net.ModNetwork;
import com.vvrgs.irontempest.registry.ModEntities;
import com.vvrgs.irontempest.registry.ModSounds;
import com.vvrgs.irontempest.server.util.Announcer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Tier S (spameable). Regla de oro: 100 comandos = 100 incrementos de UNA
 * cola por jugador que drena a ritmo fijo — lluvia continua, jamás 100
 * sesiones. Sin cámara, destrucción solo cosmética.
 */
public final class RocketRainSession extends WarSession {

    private static final int DROP_INTERVAL = 4;
    private static final int DRAIN_GRACE = 60;

    private int pending;
    private int lastSpawnAge;

    RocketRainSession(ServerLevel level, ServerPlayer target) {
        super(level, target, 'S', "rocketrain");
    }

    void addSalvos(int salvos) {
        int rockets = salvos * WarConfig.ROCKETS_PER_SALVO.get();
        this.pending = Math.min(this.pending + rockets, WarConfig.ROCKET_MAX_PENDING.get());
    }

    @Override
    protected int maxLifetime() {
        return 6000; // 5 min duros; el spam posterior crea una sesión nueva
    }

    @Override
    protected void tickInternal(@Nullable ServerPlayer target) {
        if (target == null) {
            return;
        }
        if (this.pending > 0 && this.age % DROP_INTERVAL == 0) {
            this.pending--;
            this.lastSpawnAge = this.age;
            spawnRocket(target);
        }
        // Contador de cola en actionbar cada 2 s: sin títulos (spameable).
        if (this.pending > 0 && this.age % 40 == 0) {
            Announcer.actionbar(target,
                    Component.translatable("irontempest.actionbar.rockets", this.pending));
        }
        if (this.pending == 0 && this.age - this.lastSpawnAge > DRAIN_GRACE) {
            end("drained");
        }
    }

    private void spawnRocket(ServerPlayer target) {
        // Anillo aleatorio alrededor del jugador; deriva de viento leve.
        double angle = this.level.random.nextDouble() * Math.PI * 2.0D;
        double dist = 3.0D + this.level.random.nextDouble() * 11.0D;
        double ix = target.getX() + Math.cos(angle) * dist;
        double iz = target.getZ() + Math.sin(angle) * dist;
        int groundY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(ix), (int) Math.floor(iz));
        Vec3 impact = new Vec3(ix, groundY, iz);
        Vec3 spawn = impact.add(this.level.random.nextGaussian() * 4.0D,
                30.0D + this.level.random.nextDouble() * 10.0D,
                this.level.random.nextGaussian() * 4.0D);

        // Telegraph: retícula en el punto de impacto ~1 s antes de que caiga.
        ModNetwork.fx(this.level, FxType.TARGET_MARKER, impact.add(0.0D, 0.1D, 0.0D), 1.0F);

        MlrsRocketEntity rocket = ModEntities.MLRS_ROCKET.get().create(this.level);
        if (rocket == null) {
            return;
        }
        rocket.setPos(spawn.x, spawn.y, spawn.z);
        rocket.setSessionId(this.id);
        rocket.setDeltaMovement(impact.subtract(spawn).normalize().scale(1.5D));
        rocket.alignToVelocity();
        this.level.addFreshEntity(rocket);
        // Volumen 2.5 → radio 40: el spawn está a 30-43 bl del jugador.
        this.level.playSound(null, spawn.x, spawn.y, spawn.z,
                ModSounds.MLRS_LAUNCH.get(), SoundSource.HOSTILE,
                2.5F, 0.9F + this.level.random.nextFloat() * 0.2F);
    }
}
