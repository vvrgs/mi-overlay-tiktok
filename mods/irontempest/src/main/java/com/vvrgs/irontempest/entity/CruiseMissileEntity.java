package com.vvrgs.irontempest.entity;

import com.vvrgs.irontempest.net.FxType;
import com.vvrgs.irontempest.net.ModNetwork;
import com.vvrgs.irontempest.registry.ModDamage;
import com.vvrgs.irontempest.registry.ModParticles;
import com.vvrgs.irontempest.server.session.SessionManager;
import com.vvrgs.irontempest.server.util.DamageUtil;
import com.vvrgs.irontempest.server.util.SustainedDamage;
import com.vvrgs.irontempest.server.util.TerrainSculptor;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Misil de crucero guiado. Fases cronológicas:
 * BOOST (vertical) → TIPOVER (giro balístico) → CRUISE (altitud fija) →
 * TERMINAL (homing proporcional con tasa de giro limitada) → detonación
 * de proximidad o impacto.
 */
public class CruiseMissileEntity extends AbstractWarProjectile {

    public static final byte PHASE_BOOST = 0;
    public static final byte PHASE_TIPOVER = 1;
    public static final byte PHASE_CRUISE = 2;
    public static final byte PHASE_TERMINAL = 3;

    private static final EntityDataAccessor<Byte> DATA_PHASE =
            SynchedEntityData.defineId(CruiseMissileEntity.class, EntityDataSerializers.BYTE);

    /** Tasa máxima de giro en fase terminal (radianes/tick). */
    private static final double TURN_RATE = Math.toRadians(6.0D); // esquivable con strafe duro, letal si corres recto
    private static final double PROX_FUSE = 3.5D;

    @Nullable
    private UUID targetId;
    private Vec3 lastKnownTarget = Vec3.ZERO;
    private int phaseTicks;

    public CruiseMissileEntity(EntityType<? extends CruiseMissileEntity> type, Level level) {
        super(type, level);
    }

    public void launch(UUID target, int sessionId) {
        this.targetId = target;
        setSessionId(sessionId);
        setDeltaMovement(0.0D, 0.05D, 0.0D);
        setPhase(PHASE_BOOST);
    }

    /** Re-anclaje: arrancar directamente en crucero (el guiado hace el resto). */
    public void startInCruise() {
        setPhase(PHASE_CRUISE);
    }

    public byte getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    private void setPhase(byte phase) {
        this.entityData.set(DATA_PHASE, phase);
        this.phaseTicks = 0;
    }

    @Override
    protected double gravity() {
        return 0.0D; // el guiado controla la trayectoria por completo
    }

    @Override
    protected int maxLife() {
        return 700;
    }

    @Nullable
    private Vec3 targetPos() {
        // Resolución GLOBAL (anti-TP): solo se actualiza si el jugador sigue en
        // ESTA dimensión; si se fue, el misil retiene el último punto conocido y
        // la sesión decide si lo re-warpea.
        if (this.targetId != null && this.level() instanceof ServerLevel server) {
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(this.targetId);
            if (player != null && player.isAlive() && !player.isSpectator()
                    && player.serverLevel() == server) {
                this.lastKnownTarget = player.position().add(0.0D, 1.0D, 0.0D);
            }
        }
        return this.lastKnownTarget == Vec3.ZERO ? null : this.lastKnownTarget;
    }

    @Override
    public void tick() {
        this.phaseTicks++;
        if (!this.level().isClientSide) {
            steer();
            if (this.isRemoved()) {
                return; // el fusible de proximidad ya detonó: no integrar ni re-impactar
            }
        }
        super.tick();
    }

    private void steer() {
        Vec3 vel = getDeltaMovement();
        Vec3 target = targetPos();
        byte phase = getPhase();
        switch (phase) {
            case PHASE_BOOST -> {
                setDeltaMovement(0.0D, Math.min(1.2D, vel.y + 0.06D), 0.0D);
                if (this.phaseTicks >= 35) {
                    setPhase(PHASE_TIPOVER);
                }
            }
            case PHASE_TIPOVER -> {
                if (target == null) {
                    setPhase(PHASE_CRUISE);
                    return;
                }
                Vec3 toTarget = new Vec3(target.x - getX(), 0.0D, target.z - getZ()).normalize();
                double t = Math.min(1.0D, this.phaseTicks / 20.0D);
                Vec3 dir = new Vec3(
                        toTarget.x * t, 1.0D - 0.9D * t, toTarget.z * t).normalize();
                setDeltaMovement(dir.scale(1.2D + 0.4D * t));
                if (this.phaseTicks >= 20) {
                    setPhase(PHASE_CRUISE);
                }
            }
            case PHASE_CRUISE -> {
                if (target == null) {
                    return; // vuela recto hasta timeout
                }
                double cruiseY = target.y + 22.0D;
                Vec3 horiz = new Vec3(target.x - getX(), 0.0D, target.z - getZ());
                double horizDist = horiz.length();
                Vec3 dir = new Vec3(horiz.x, (cruiseY - getY()) * 0.08D, horiz.z).normalize();
                setDeltaMovement(dir.scale(1.6D));
                if (horizDist < 26.0D) {
                    setPhase(PHASE_TERMINAL);
                    // Telegraph: retícula en el objetivo al iniciar el picado.
                    if (this.level() instanceof ServerLevel sl) {
                        ModNetwork.fx(sl, FxType.TARGET_MARKER,
                                target.add(0.0D, -0.9D, 0.0D), 1.5F);
                    }
                }
            }
            case PHASE_TERMINAL -> {
                if (target != null) {
                    // El objetivo saltó lejos (TP intra-dim): re-adquisición limpia
                    // en crucero en vez de un giro eterno a 6°/tick.
                    if (new Vec3(target.x - getX(), 0.0D, target.z - getZ()).length() > 40.0D) {
                        setPhase(PHASE_CRUISE);
                        return;
                    }
                    if (position().distanceTo(target) < PROX_FUSE) {
                        detonate(position());
                        return;
                    }
                    Vec3 current = vel.normalize();
                    Vec3 desired = target.subtract(position()).normalize();
                    double angle = Math.acos(Math.max(-1.0D, Math.min(1.0D, current.dot(desired))));
                    Vec3 newDir;
                    if (angle <= TURN_RATE || angle < 1.0E-4D) {
                        newDir = desired;
                    } else {
                        double f = TURN_RATE / angle;
                        newDir = current.lerp(desired, f).normalize();
                    }
                    // FÍSICA: serpenteo terminal (weave) perpendicular — el misil
                    // se lee vivo y es más difícil de leer, sin volverse imposible.
                    Vec3 side = newDir.cross(new Vec3(0.0D, 1.0D, 0.0D));
                    if (side.lengthSqr() > 1.0E-4D) {
                        double weave = Math.sin(this.life * 0.35D) * 0.14D;
                        newDir = newDir.add(side.normalize().scale(weave)).normalize();
                    }
                    setDeltaMovement(newDir.scale(1.9D));
                }
            }
            default -> {}
        }
    }

    @Override
    protected void onImpact(HitResult hit) {
        detonate(hit.getLocation());
    }

    private void detonate(Vec3 pos) {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        int ground = server.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                (int) Math.floor(pos.x), (int) Math.floor(pos.z));
        boolean airburst = pos.y - ground > 2.5D;
        ModNetwork.fx(server, airburst ? FxType.AIRBURST : FxType.EXPLOSION_LARGE,
                pos, getDeltaMovement().normalize(), 2.0F);
        TerrainSculptor.crater(server, BlockPos.containing(pos),
                com.vvrgs.irontempest.config.WarConfig.craterRadius(4), true);
        DamageUtil.strikeDamage(server, pos, 2.5D, 8.0D, 26.0F, ModDamage.MISSILE, this);
        DamageUtil.blastImpulse(server, pos, 9.0D, 1.6D);
        // El punto cero arde 6 s; los supervivientes siguen ardiendo 4 s.
        SustainedDamage.zone(server, pos, 5.0D, 6.0D, 4.0F, ModDamage.MISSILE, true);
        SustainedDamage.afflictArea(server, pos, 8.0D, 4.0D, 3.0F, ModDamage.MISSILE, true);
        com.vvrgs.irontempest.server.util.TotemShredder.shredArea(server, pos, 4.0D, "cruisemissile", 3);
        SessionManager.notifyProjectileImpact(this.sessionId);
        discard();
    }

    @Override
    protected void clientTrail() {
        Vec3 vel = getDeltaMovement();
        Vec3 back = position().subtract(vel.normalize().scale(1.2D));
        byte phase = getPhase();
        // Motor: fuego pulsante + columna de humo (denso en boost, fino en crucero)
        this.level().addParticle(ModParticles.EMBER.get(), back.x, back.y, back.z,
                -vel.x * 0.15D, -vel.y * 0.15D, -vel.z * 0.15D);
        if (phase == PHASE_BOOST) {
            for (int i = 0; i < 3; i++) {
                this.level().addParticle(ModParticles.FIREBALL.get(),
                        back.x + this.random.nextGaussian() * 0.15D,
                        back.y + this.random.nextGaussian() * 0.15D,
                        back.z + this.random.nextGaussian() * 0.15D,
                        -vel.x * 0.2D, -vel.y * 0.25D, -vel.z * 0.2D);
                this.level().addParticle(ModParticles.SMOKE.get(),
                        back.x + this.random.nextGaussian() * 0.3D,
                        back.y - 0.4D * i,
                        back.z + this.random.nextGaussian() * 0.3D,
                        0.0D, -0.02D, 0.0D);
            }
        } else {
            this.level().addParticle(ModParticles.FIREBALL.get(), back.x, back.y, back.z,
                    -vel.x * 0.1D, -vel.y * 0.1D, -vel.z * 0.1D);
            if (this.life % 2 == 0) {
                this.level().addParticle(ModParticles.SMOKE.get(), back.x, back.y, back.z,
                        this.random.nextGaussian() * 0.02D, 0.01D, this.random.nextGaussian() * 0.02D);
            }
            if (phase == PHASE_TERMINAL) {
                this.level().addParticle(ModParticles.SPARK.get(), back.x, back.y, back.z,
                        this.random.nextGaussian() * 0.05D,
                        this.random.nextGaussian() * 0.05D,
                        this.random.nextGaussian() * 0.05D);
            }
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_PHASE, PHASE_BOOST);
    }
}
