package net.minecraft.world.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public abstract class LivingEntity extends Entity {
    protected LivingEntity(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
