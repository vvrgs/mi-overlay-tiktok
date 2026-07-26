package com.vvrgs.irontempest.server.session;

import com.vvrgs.irontempest.entity.WarshipEntity;
import com.vvrgs.irontempest.net.FxType;
import com.vvrgs.irontempest.net.ModNetwork;
import com.vvrgs.irontempest.registry.ModDamage;
import com.vvrgs.irontempest.registry.ModEntities;
import com.vvrgs.irontempest.registry.ModSounds;
import com.vvrgs.irontempest.server.util.DamageUtil;
import com.vvrgs.irontempest.server.util.TerrainSculptor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Tier C cinemático. Cronología exacta:
 * T+0 warp-in → T+25 carga (motas convergentes, pitch ascendente) →
 * T+105 haz orbital que PERSIGUE al jugador a 0.35 b/t vaporizando el
 * terreno → T+225 pulso de sobrecarga AoE → T+245 warp-out → T+262 fin.
 */
public final class OrbitalStrikeSession extends WarSession {

    private static final int T_CHARGE = 25;
    private static final int T_BEAM = 105;
    private static final int T_OVERLOAD = 225;
    private static final int T_OUT = 245;
    private static final int T_END = 262;

    private static final double BEAM_CHASE_SPEED = 0.35D;
    private static final double BEAM_KILL_RADIUS = 2.0D;

    private WarshipEntity ship;
    private Vec3 beamPoint = Vec3.ZERO;

    OrbitalStrikeSession(ServerLevel level, ServerPlayer target) {
        super(level, target, 'C', "orbitalstrike");
        spawnShip(target);
        SessionManager.broadcastStarted(level, "orbitalstrike", this.targetName);
    }

    @Override
    protected int maxLifetime() {
        return 400;
    }

    private void spawnShip(ServerPlayer target) {
        double bearing = this.level.random.nextDouble() * Math.PI * 2.0D;
        Vec3 pos = target.position().add(Math.cos(bearing) * 6.0D, 38.0D, Math.sin(bearing) * 6.0D);

        WarshipEntity s = ModEntities.WARSHIP.get().create(this.level);
        if (s == null) {
            end("spawn_failed");
            return;
        }
        s.setPos(pos.x, pos.y, pos.z);
        s.setSessionId(this.id);
        s.setYRot((float) (Mth.atan2(-(target.getX() - pos.x), target.getZ() - pos.z) * Mth.RAD_TO_DEG));
        this.level.addFreshEntity(s);
        this.ship = s;

        // El haz nace 12 bloques por detrás del jugador y lo caza.
        this.beamPoint = target.position().add(Math.cos(bearing) * 12.0D, 0.0D, Math.sin(bearing) * 12.0D);

        ModNetwork.fx(this.level, FxType.WARP_IN, pos, 2.0F);
        this.level.playSound(null, pos.x, pos.y, pos.z,
                ModSounds.WARP_IN.get(), SoundSource.HOSTILE, 2.5F, 1.0F);
    }

    @Override
    protected void tickInternal(@Nullable ServerPlayer target) {
        if (this.ship == null || this.ship.isRemoved()) {
            end("ship_lost");
            return;
        }
        if (this.age == T_CHARGE) {
            this.ship.setPhase(WarshipEntity.PHASE_CHARGE);
            this.level.playSound(null, this.ship.getX(), this.ship.getY(), this.ship.getZ(),
                    ModSounds.LASER_CHARGE.get(), SoundSource.HOSTILE, 2.2F, 1.0F);
        }
        if (this.age > T_CHARGE && this.age < T_BEAM) {
            float progress = (this.age - T_CHARGE) / (float) (T_BEAM - T_CHARGE);
            this.ship.setCharge(progress);
            if (this.age % 10 == 0) {
                ModNetwork.fx(this.level, FxType.CHARGE_BURST, this.ship.cannonEmitter(), progress);
            }
        }
        if (this.age == T_BEAM) {
            this.ship.setPhase(WarshipEntity.PHASE_BEAM);
        }
        if (this.age > T_BEAM && this.age < T_OVERLOAD) {
            tickBeam(target);
        }
        if (this.age == T_OVERLOAD) {
            overload();
        }
        if (this.age == T_OUT) {
            this.ship.setPhase(WarshipEntity.PHASE_WARP_OUT);
            ModNetwork.fx(this.level, FxType.WARP_OUT, this.ship.position(), 2.0F);
            this.level.playSound(null, this.ship.getX(), this.ship.getY(), this.ship.getZ(),
                    ModSounds.WARP_OUT.get(), SoundSource.HOSTILE, 2.5F, 1.0F);
        }
        if (this.age >= T_END) {
            this.ship.discard();
            end("complete");
        }
    }

    private void tickBeam(@Nullable ServerPlayer target) {
        // El haz caza la posición ACTUAL del jugador: implacable pero esquivable.
        if (target != null) {
            Vec3 to = target.position().subtract(this.beamPoint).multiply(1.0D, 0.0D, 1.0D);
            double dist = to.length();
            if (dist > 0.01D) {
                this.beamPoint = this.beamPoint.add(to.normalize().scale(Math.min(BEAM_CHASE_SPEED, dist)));
            }
        }
        int groundY = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                (int) Math.floor(this.beamPoint.x), (int) Math.floor(this.beamPoint.z));
        this.beamPoint = new Vec3(this.beamPoint.x, groundY, this.beamPoint.z);
        this.ship.setBeamTarget(this.beamPoint);

        if (this.age % 3 == 0) {
            ModNetwork.fx(this.level, FxType.BEAM_SWEEP, this.beamPoint, 1.0F);
        }
        if (this.age % 8 == 0) {
            TerrainSculptor.crater(this.level, BlockPos.containing(this.beamPoint), 2, true);
        }
        if (this.age % 10 == 0) {
            // Columna del haz: letal (salvo tótem) dentro del radio.
            AABB column = new AABB(this.beamPoint.x - BEAM_KILL_RADIUS, groundY - 2.0D,
                    this.beamPoint.z - BEAM_KILL_RADIUS,
                    this.beamPoint.x + BEAM_KILL_RADIUS, this.ship.getY(),
                    this.beamPoint.z + BEAM_KILL_RADIUS);
            for (LivingEntity living : this.level.getEntitiesOfClass(LivingEntity.class, column)) {
                double horiz = Math.hypot(living.getX() - this.beamPoint.x, living.getZ() - this.beamPoint.z);
                if (horiz <= BEAM_KILL_RADIUS) {
                    DamageUtil.killIfNoTotem(this.level, living, ModDamage.ORBITAL_BEAM, this.ship);
                }
            }
        }
    }

    private void overload() {
        this.ship.setPhase(WarshipEntity.PHASE_OVERLOAD);
        ModNetwork.fx(this.level, FxType.OVERLOAD_PULSE, this.beamPoint, 2.5F);
        this.level.playSound(null, this.beamPoint.x, this.beamPoint.y, this.beamPoint.z,
                ModSounds.EXPLOSION_NEAR.get(), SoundSource.HOSTILE, 3.0F, 0.8F);
        TerrainSculptor.crater(this.level, BlockPos.containing(this.beamPoint), 5, true);
        DamageUtil.strikeDamage(this.level, this.beamPoint, 3.0D, 9.0D, 20.0F,
                ModDamage.ORBITAL_BEAM, this.ship);
    }

    @Override
    protected void onEnd(String reason) {
        if (this.ship != null && !this.ship.isRemoved()) {
            this.ship.discard();
        }
    }
}
