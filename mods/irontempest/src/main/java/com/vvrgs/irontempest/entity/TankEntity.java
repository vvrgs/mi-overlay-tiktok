package com.vvrgs.irontempest.entity;

import com.vvrgs.irontempest.registry.ModParticles;
import com.vvrgs.irontempest.server.session.SessionManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Tanque de asalto cinemático. El servidor (TankBlitzSession) manda:
 * torreta absoluta, elevación del cañón, secuencia de disparo (para el
 * retroceso en cliente) y estado del motor. El cliente interpola todo.
 */
public class TankEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_TURRET_YAW =
            SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BARREL_PITCH =
            SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_FIRE_SEQ =
            SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_AIMING =
            SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ENGINE =
            SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_DRIVE =
            SynchedEntityData.defineId(TankEntity.class, EntityDataSerializers.FLOAT);

    private int sessionId = -1;
    /** Velocidad de avance ORDENADA por la sesión (bloques/tick, solo server). */
    private double driveTarget;
    /** Velocidad real tras la rampa de aceleración/frenado (solo server). */
    private double driveSpeed;

    // Estado SOLO cliente para interpolación/retroceso.
    public float turretYawO;
    public float barrelPitchO;
    private int lastFireSeq;
    private int clientFireGameTick = Integer.MIN_VALUE;

    // Interpolación de red estilo Boat: sin ella, el yaw (cuantizado a ~1.4°
    // por el byte del packet) haría el giro de casco a saltos visibles.
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYRot;

    // Animación cliente v6: distancia REAL acumulada para ruedas y orugas
    // (con diferencial por lado al girar), aceleración suavizada para el
    // cabeceo del casco y estado del escaneo de cúpula / retirada.
    public float wheelRot;
    public float wheelRotO;
    public float beltL;
    public float beltLO;
    public float beltR;
    public float beltRO;
    public float accelSmooth;
    public float scanAmp;
    public int leaveStart = -1;
    private float driveO;

    public TankEntity(EntityType<? extends TankEntity> type, Level level) {
        super(type, level);
    }

    public void setSessionId(int id) {
        this.sessionId = id;
    }

    // ------------------------------------------------------------ synched API
    public float getTurretYaw() {
        return this.entityData.get(DATA_TURRET_YAW);
    }

    public void setTurretYaw(float yaw) {
        this.entityData.set(DATA_TURRET_YAW, yaw);
    }

    public float getBarrelPitch() {
        return this.entityData.get(DATA_BARREL_PITCH);
    }

    public void setBarrelPitch(float pitch) {
        this.entityData.set(DATA_BARREL_PITCH, pitch);
    }

    public boolean isAiming() {
        return this.entityData.get(DATA_AIMING);
    }

    public void setAiming(boolean aiming) {
        this.entityData.set(DATA_AIMING, aiming);
    }

    public boolean isEngineOn() {
        return this.entityData.get(DATA_ENGINE);
    }

    public void setEngineOn(boolean on) {
        this.entityData.set(DATA_ENGINE, on);
    }

    /** Server: marca un disparo (el cliente reacciona con retroceso). */
    public void markFired() {
        this.entityData.set(DATA_FIRE_SEQ, this.entityData.get(DATA_FIRE_SEQ) + 1);
    }

    /** Server: orden de avance de la sesión (0 = detenido); la rampa hace el resto. */
    public void setDriveSpeed(double speed) {
        this.driveTarget = speed;
    }

    /** Cliente: velocidad de avance real sincronizada (cabeceo, sonido de motor). */
    public float getDriveSpeed() {
        return this.entityData.get(DATA_DRIVE);
    }

    /** Velocidad horizontal real de este tick (útil en cliente vía posiciones). */
    public double horizontalSpeed() {
        double dx = getX() - this.xo;
        double dz = getZ() - this.zo;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Cliente: ticks (con parcial) desde el último disparo, o MAX_VALUE. */
    public float ticksSinceFire(float partialTick) {
        if (this.clientFireGameTick == Integer.MIN_VALUE) {
            return Float.MAX_VALUE;
        }
        return (this.tickCount - this.clientFireGameTick) + partialTick;
    }

    /**
     * Dirección del cañón. Convención vanilla de yaw (0 = +Z):
     * dir = (-sin(yaw), sin(pitch), cos(yaw)); pitch positivo = hacia ARRIBA.
     */
    public Vec3 barrelDirection() {
        float yaw = getTurretYaw() * Mth.DEG_TO_RAD;
        float pitch = getBarrelPitch() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
    }

    /**
     * Punto de la boca del cañón en coordenadas de mundo (para FX/disparo).
     * Derivado del modelo renderizado: eje del cañón a y=1.72 (pivote torreta
     * y=7, cañón −3.5, suelo 24) y boca a 2.75 bl (z −44/16 del root).
     */
    public Vec3 muzzlePoint() {
        return position().add(0.0D, 1.72D, 0.0D).add(barrelDirection().scale(2.75D));
    }

    // ------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            if (this.lerpSteps > 0) {
                double nx = getX() + (this.lerpX - getX()) / this.lerpSteps;
                double ny = getY() + (this.lerpY - getY()) / this.lerpSteps;
                double nz = getZ() + (this.lerpZ - getZ()) / this.lerpSteps;
                setYRot(getYRot() + Mth.wrapDegrees(this.lerpYRot - getYRot()) / this.lerpSteps);
                setPos(nx, ny, nz);
                this.lerpSteps--;
            }
            this.turretYawO = getTurretYaw();
            this.barrelPitchO = getBarrelPitch();
            int seq = this.entityData.get(DATA_FIRE_SEQ);
            if (seq != this.lastFireSeq) {
                this.lastFireSeq = seq;
                this.clientFireGameTick = this.tickCount;
            }
            // v6: ruedas/orugas por distancia REAL (patinar contra un muro se ve),
            // diferencial de orugas al girar (pivot-turn) y cabeceo por aceleración.
            this.wheelRotO = this.wheelRot;
            this.beltLO = this.beltL;
            this.beltRO = this.beltR;
            float dist = (float) horizontalSpeed() * 16.0F; // unidades de modelo
            float yawRate = Mth.wrapDegrees(getYRot() - this.yRotO) * Mth.DEG_TO_RAD;
            float diffTrack = yawRate * 1.22F * 16.0F;
            this.wheelRot += dist / 3.5F; // radio de rueda 3.5 u
            this.beltL += dist + diffTrack;
            this.beltR += dist - diffTrack;
            float driveNow = getDriveSpeed();
            this.accelSmooth += ((driveNow - this.driveO) - this.accelSmooth) * 0.25F;
            this.driveO = driveNow;
            this.scanAmp += ((isAiming() ? 0.0F : 1.0F) - this.scanAmp) * 0.05F;
            // Retirada detectada con datos ya sincronizados: guion de 5 disparos
            // completo y la mira apagada → los lanzahumos se yerguen.
            if (this.leaveStart < 0 && this.lastFireSeq >= 5 && !isAiming()) {
                this.leaveStart = this.tickCount;
            }
            clientAmbient();
            // turretYawO se actualiza ANTES de leer el nuevo valor sincronizado:
            // el renderer lerpa entre turretYawO y getTurretYaw().
            return;
        }

        if (this.sessionId >= 0 && !SessionManager.isSessionActive(this.sessionId)) {
            discard();
            return;
        }

        // FÍSICA: rampa de aceleración/frenado — el tanque arranca y frena con
        // masa (acel ~0.7 s, freno ~0.45 s), nunca on/off instantáneo.
        double rate = this.driveTarget > this.driveSpeed ? 0.0040D : 0.0065D;
        double diff = this.driveTarget - this.driveSpeed;
        if (diff > rate) {
            this.driveSpeed += rate;
        } else if (diff < -rate) {
            this.driveSpeed -= rate;
        } else {
            this.driveSpeed = this.driveTarget;
        }
        this.entityData.set(DATA_DRIVE, (float) this.driveSpeed);

        // Gravedad + colisión (caída del drop-pod) + avance ordenado por la sesión.
        if (!onGround()) {
            setDeltaMovement(getDeltaMovement().add(0.0D, -0.08D, 0.0D));
        } else if (this.driveSpeed > 1.0E-3D) {
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            setDeltaMovement(-Mth.sin(yaw) * this.driveSpeed, -0.08D, Mth.cos(yaw) * this.driveSpeed);
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        move(MoverType.SELF, getDeltaMovement());
    }

    private void clientAmbient() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        if (isEngineOn() && this.random.nextFloat() <= 0.35F) {
            // Humo de escape trasero: la parte de atrás es -facing = (sin(yaw), ·, -cos(yaw)).
            Vec3 rear = position().add(new Vec3(Mth.sin(yaw) * 2.1D, 1.15D, -Mth.cos(yaw) * 2.1D));
            this.level().addParticle(ModParticles.SMOKE.get(),
                    rear.x + this.random.nextGaussian() * 0.1D, rear.y, rear.z + this.random.nextGaussian() * 0.1D,
                    0.0D, 0.05D + this.random.nextFloat() * 0.03D, 0.0D);
        }
        // Polvo levantado por las orugas cuando el tanque avanza.
        if (horizontalSpeed() > 0.02D) {
            Vec3 side = new Vec3(Mth.cos(yaw), 0.0D, -Mth.sin(yaw));
            Vec3 rear = position().add(Mth.sin(yaw) * 2.0D, 0.15D, -Mth.cos(yaw) * 2.0D);
            for (int i = 0; i < 2; i++) {
                double s = this.random.nextBoolean() ? 1.25D : -1.25D;
                Vec3 p = rear.add(side.scale(s));
                this.level().addParticle(ModParticles.SMOKE.get(), p.x, p.y, p.z,
                        this.random.nextGaussian() * 0.03D, 0.05D, this.random.nextGaussian() * 0.03D);
            }
        }
        // Coreografía post-disparo: casquillo eyectado + humo de recámara/boca.
        float sinceFire = ticksSinceFire(0.0F);
        if (sinceFire == 2.0F) {
            Vec3 breech = position().add(0.0D, 1.6D, 0.0D);
            Vec3 side = new Vec3(Mth.cos(getTurretYaw() * Mth.DEG_TO_RAD), 0.0D,
                    -Mth.sin(getTurretYaw() * Mth.DEG_TO_RAD));
            this.level().addParticle(ModParticles.DEBRIS.get(),
                    breech.x + side.x * 0.8D, breech.y, breech.z + side.z * 0.8D,
                    side.x * 0.18D, 0.25D, side.z * 0.18D);
            this.level().playLocalSound(breech.x, breech.y, breech.z,
                    com.vvrgs.irontempest.registry.ModSounds.SHELL_CASING.get(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 0.9F,
                    0.95F + this.random.nextFloat() * 0.1F, false);
        }
        if (sinceFire > 3.0F && sinceFire < 26.0F && this.random.nextFloat() < 0.45F) {
            Vec3 muzzle = muzzlePoint();
            this.level().addParticle(ModParticles.SMOKE.get(), muzzle.x, muzzle.y, muzzle.z,
                    this.random.nextGaussian() * 0.015D, 0.04D, this.random.nextGaussian() * 0.015D);
        }
    }

    // ------------------------------------------------------------ plumbing
    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TURRET_YAW, 0.0F);
        this.entityData.define(DATA_BARREL_PITCH, 0.0F);
        this.entityData.define(DATA_FIRE_SEQ, 0);
        this.entityData.define(DATA_AIMING, false);
        this.entityData.define(DATA_ENGINE, false);
        this.entityData.define(DATA_DRIVE, 0.0F);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot,
                       int steps, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpSteps = 3;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** setRemoved y NO remove(): la descarga de chunks llama setRemoved
     *  directamente y dejaría la membresía huérfana en scoreboard.dat. */
    @Override
    public void setRemoved(RemovalReason reason) {
        if (!this.level().isClientSide) {
            com.vvrgs.irontempest.server.util.WarTeam.leave(this);
        }
        super.setRemoved(reason);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
