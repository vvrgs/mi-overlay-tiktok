package net.minecraft.world.entity;

public abstract class LivingEntity extends Entity {
    protected LivingEntity(EntityType<? extends LivingEntity> type, net.minecraft.world.level.Level level) { super(type, level); }
    public float getHealth() { throw new UnsupportedOperationException(); }
    public void setItemSlot(EquipmentSlot slot, net.minecraft.world.item.ItemStack stack) {}
    public net.minecraft.world.item.ItemStack getOffhandItem() { throw new UnsupportedOperationException(); }
    public net.minecraft.world.item.ItemStack getMainHandItem() { throw new UnsupportedOperationException(); }
}
