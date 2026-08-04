package net.minecraft.world.entity.item;

public class ItemEntity extends net.minecraft.world.entity.Entity {
    public ItemEntity(net.minecraft.world.entity.EntityType<? extends ItemEntity> type, net.minecraft.world.level.Level level) { super(type, level); }
    @Override protected void defineSynchedData() {}
}
