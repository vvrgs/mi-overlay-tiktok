package com.vvrgs.cataclysm.entity;

import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.registry.ModParticles;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * El impactor planetario en su fase de entrada atmosferica: entidad real
 * con render a escala (no una particula) que cruza el cielo rasgandolo.
 * La sesion detecta la llegada a la altura de impacto y dispara el evento;
 * la fase previa (punto de luz que crece) es un SkyFx global.
 */
public class ImpactorEntity extends DisasterEntity {

    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(ImpactorEntity.class, EntityDataSerializers.FLOAT);

    private double impactY;

    public ImpactorEntity(EntityType<?> type, Level level) {
        super(type, level);
        setLifeTicks(20 * 20);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SIZE, 3.0F);
    }

    public void configure(Vec3 velocity, float size, double impactY) {
        this.setDeltaMovement(velocity);
        this.entityData.set(DATA_SIZE, size);
        this.impactY = impactY;
        Vec3 motion = velocity;
        this.setYRot((float) (Mth.atan2(motion.x, motion.z) * (180.0D / Math.PI)));
        this.setXRot((float) (Mth.atan2(motion.y, motion.horizontalDistance()) * (180.0D / Math.PI)));
    }

    public float getSize() {
        return this.entityData.get(DATA_SIZE);
    }

    /** true cuando ya llego (o paso) la altura de impacto: la sesion dispara el evento. */
    public boolean hasArrived() {
        return this.getY() <= impactY;
    }

    @Override
    protected void serverTick() {
        Vec3 motion = this.getDeltaMovement();
        this.setPos(this.position().add(motion));
        ServerLevel level = (ServerLevel) this.level();
        if (this.tickCount % 15 == 0) {
            FxDirector.sound(level, this.position(), ModSounds.ATMOSPHERE_TEAR.get(), 4.0F, 0.9F);
        }
        // la sesion hace el poll de hasArrived(); aqui solo failsafe
        if (this.getY() < level.getMinBuildHeight()) {
            this.discard();
        }
    }

    @Override
    protected void clientTick() {
        Vec3 motion = this.getDeltaMovement();
        float size = getSize();
        for (int i = 0; i < 4; i++) {
            double back = 0.8D * i + this.random.nextDouble() * 0.8D;
            this.level().addParticle(ModParticles.PLASMA.get(),
                    this.getX() - motion.x * back + (this.random.nextDouble() - 0.5D) * size,
                    this.getY() - motion.y * back + (this.random.nextDouble() - 0.5D) * size,
                    this.getZ() - motion.z * back + (this.random.nextDouble() - 0.5D) * size,
                    -motion.x * 0.05D, -motion.y * 0.05D, -motion.z * 0.05D);
        }
        this.level().addParticle(ModParticles.SMOKE.get(),
                this.getX() - motion.x * 4.0D,
                this.getY() - motion.y * 4.0D,
                this.getZ() - motion.z * 4.0D,
                0.0D, 0.0D, 0.0D);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("ImpactY", impactY);
        tag.putFloat("Size", getSize());
        Vec3 motion = getDeltaMovement();
        tag.putDouble("VelX", motion.x);
        tag.putDouble("VelY", motion.y);
        tag.putDouble("VelZ", motion.z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        impactY = tag.getDouble("ImpactY");
        this.entityData.set(DATA_SIZE, tag.getFloat("Size"));
        this.setDeltaMovement(tag.getDouble("VelX"), tag.getDouble("VelY"), tag.getDouble("VelZ"));
    }
}
