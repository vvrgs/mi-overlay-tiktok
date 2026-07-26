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

    private int sessionId = -1;

    // Estado SOLO cliente para interpolación/retroceso.
    public float turretYawO;
    public float barrelPitchO;
    private int lastFireSeq;
    private int clientFireGameTick = Integer.MIN_VALUE;

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

    /** Punto de la boca del cañón en coordenadas de mundo (para FX/disparo). */
    public Vec3 muzzlePoint() {
        return position().add(0.0D, 1.35D, 0.0D).add(barrelDirection().scale(2.6D));
    }

    // ------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            this.turretYawO = getTurretYaw();
            this.barrelPitchO = getBarrelPitch();
            int seq = this.entityData.get(DATA_FIRE_SEQ);
            if (seq != this.lastFireSeq) {
                this.lastFireSeq = seq;
                this.clientFireGameTick = this.tickCount;
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

        // Gravedad + colisión (caída del drop-pod).
        if (!onGround()) {
            setDeltaMovement(getDeltaMovement().add(0.0D, -0.08D, 0.0D));
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        move(MoverType.SELF, getDeltaMovement());
    }

    private void clientAmbient() {
        if (!isEngineOn() || this.random.nextFloat() > 0.35F) {
            return;
        }
        // Humo de escape trasero: la parte de atrás es -facing = (sin(yaw), ·, -cos(yaw)).
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 rear = position().add(new Vec3(Mth.sin(yaw) * 2.1D, 1.15D, -Mth.cos(yaw) * 2.1D));
        this.level().addParticle(ModParticles.SMOKE.get(),
                rear.x + this.random.nextGaussian() * 0.1D, rear.y, rear.z + this.random.nextGaussian() * 0.1D,
                0.0D, 0.05D + this.random.nextFloat() * 0.03D, 0.0D);
    }

    // ------------------------------------------------------------ plumbing
    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_TURRET_YAW, 0.0F);
        this.entityData.define(DATA_BARREL_PITCH, 0.0F);
        this.entityData.define(DATA_FIRE_SEQ, 0);
        this.entityData.define(DATA_AIMING, false);
        this.entityData.define(DATA_ENGINE, false);
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

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
