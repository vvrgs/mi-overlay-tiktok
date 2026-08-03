package net.minecraft.world.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public abstract class LivingEntity extends Entity {
    public net.minecraft.world.item.ItemStack getMainHandItem() { throw new UnsupportedOperationException(); }
    public net.minecraft.world.item.ItemStack getOffhandItem() { throw new UnsupportedOperationException(); }
    public void setItemInHand(net.minecraft.world.InteractionHand hand, net.minecraft.world.item.ItemStack stack) {}

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
