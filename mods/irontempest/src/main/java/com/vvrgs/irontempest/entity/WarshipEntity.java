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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Nave de guerra orbital. Fases: WARP_IN → CHARGE → BEAM → OVERLOAD → WARP_OUT.
 * El servidor sincroniza fase, progreso de carga y el punto de barrido del haz;
 * el renderer dibuja el haz con shader propio desde el cañón ventral.
 */
public class WarshipEntity extends Entity {

    public static final byte PHASE_WARP_IN = 0;
    public static final byte PHASE_CHARGE = 1;
    public static final byte PHASE_BEAM = 2;
    public static final byte PHASE_OVERLOAD = 3;
    public static final byte PHASE_WARP_OUT = 4;

    private static final EntityDataAccessor<Byte> DATA_PHASE =
            SynchedEntityData.defineId(WarshipEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_CHARGE =
            SynchedEntityData.defineId(WarshipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_X =
            SynchedEntityData.defineId(WarshipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_Y =
            SynchedEntityData.defineId(WarshipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BEAM_Z =
            SynchedEntityData.defineId(WarshipEntity.class, EntityDataSerializers.FLOAT);

    private int sessionId = -1;

    public WarshipEntity(EntityType<? extends WarshipEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setSessionId(int id) {
        this.sessionId = id;
    }

    public byte getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    public void setPhase(byte phase) {
        this.entityData.set(DATA_PHASE, phase);
    }

    public float getCharge() {
        return this.entityData.get(DATA_CHARGE);
    }

    public void setCharge(float charge) {
        this.entityData.set(DATA_CHARGE, charge);
    }

    public Vec3 getBeamTarget() {
        return new Vec3(this.entityData.get(DATA_BEAM_X),
                this.entityData.get(DATA_BEAM_Y),
                this.entityData.get(DATA_BEAM_Z));
    }

    public void setBeamTarget(Vec3 pos) {
        this.entityData.set(DATA_BEAM_X, (float) pos.x);
        this.entityData.set(DATA_BEAM_Y, (float) pos.y);
        this.entityData.set(DATA_BEAM_Z, (float) pos.z);
    }

    /**
     * Emisor del cañón ventral en mundo (origen del haz). El cañón del modelo
     * está 0.375 bl hacia la PROA: el offset debe rotar con el yaw de la nave
     * (forward = (-sin, 0, cos) en convención living).
     */
    public Vec3 cannonEmitter() {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        return position().add(-Mth.sin(yaw) * 0.375D, -0.9D, Mth.cos(yaw) * 0.375D);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            clientAmbient();
            return;
        }

        if (this.sessionId >= 0 && !SessionManager.isSessionActive(this.sessionId)) {
            discard();
        }
    }

    private void clientAmbient() {
        byte phase = getPhase();
        if (phase == PHASE_WARP_IN || phase == PHASE_WARP_OUT) {
            return;
        }
        // Goteo de energía de las góndolas (posición aproximada de nacelles).
        if (this.random.nextFloat() < 0.6F) {
            float yaw = getYRot() * Mth.DEG_TO_RAD;
            double side = this.random.nextBoolean() ? 1.0D : -1.0D;
            Vec3 right = new Vec3(Mth.cos(yaw), 0.0D, -Mth.sin(yaw)).scale(side * 1.0D);
            // Popa = -forward = (sin(yaw), 0, -cos(yaw)): las góndolas están atrás.
            Vec3 back = new Vec3(Mth.sin(yaw), 0.0D, -Mth.cos(yaw)).scale(2.4D);
            Vec3 p = position().add(right).add(back).add(0.0D, 0.1D, 0.0D);
            this.level().addParticle(ModParticles.CHARGE_MOTE.get(), p.x, p.y, p.z,
                    -back.x * 0.02D, -0.01D, -back.z * 0.02D);
        }
        // Carga: motas convergiendo al cañón ventral.
        if (phase == PHASE_CHARGE && this.random.nextFloat() < 0.9F) {
            Vec3 emitter = cannonEmitter();
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double dist = 2.5D + this.random.nextDouble() * 3.5D;
            Vec3 from = emitter.add(Math.cos(angle) * dist,
                    (this.random.nextDouble() - 0.3D) * 2.0D, Math.sin(angle) * dist);
            Vec3 vel = emitter.subtract(from).scale(0.12D);
            this.level().addParticle(ModParticles.CHARGE_MOTE.get(),
                    from.x, from.y, from.z, vel.x, vel.y, vel.z);
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_PHASE, PHASE_WARP_IN);
        this.entityData.define(DATA_CHARGE, 0.0F);
        this.entityData.define(DATA_BEAM_X, 0.0F);
        this.entityData.define(DATA_BEAM_Y, 0.0F);
        this.entityData.define(DATA_BEAM_Z, 0.0F);
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
