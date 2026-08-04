package net.minecraft.world.entity;

public abstract class Entity {
    public enum RemovalReason { KILLED, DISCARDED, UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER, CHANGED_DIMENSION }

    public int tickCount;
    public int invulnerableTime;
    public boolean noPhysics;
    public boolean hurtMarked;
    protected final net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create();
    protected final net.minecraft.network.syncher.SynchedEntityData entityData = new net.minecraft.network.syncher.SynchedEntityData();

    public Entity(EntityType<?> type, net.minecraft.world.level.Level level) {}

    protected abstract void defineSynchedData();
    protected void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}
    protected void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {}

    public void tick() {}
    public net.minecraft.world.level.Level level() { throw new UnsupportedOperationException(); }
    public net.minecraft.server.MinecraftServer getServer() { throw new UnsupportedOperationException(); }
    public net.minecraft.world.phys.Vec3 position() { throw new UnsupportedOperationException(); }
    public double getX() { throw new UnsupportedOperationException(); }
    public double getY() { throw new UnsupportedOperationException(); }
    public double getZ() { throw new UnsupportedOperationException(); }
    public net.minecraft.core.BlockPos blockPosition() { throw new UnsupportedOperationException(); }
    public void setPos(double x, double y, double z) {}
    public void setPos(net.minecraft.world.phys.Vec3 pos) {}
    public net.minecraft.world.phys.Vec3 getDeltaMovement() { throw new UnsupportedOperationException(); }
    public void setDeltaMovement(net.minecraft.world.phys.Vec3 motion) {}
    public void setDeltaMovement(double x, double y, double z) {}
    public float getYRot() { throw new UnsupportedOperationException(); }
    public void setYRot(float yRot) {}
    public float getXRot() { throw new UnsupportedOperationException(); }
    public void setXRot(float xRot) {}
    public boolean onGround() { throw new UnsupportedOperationException(); }
    public void discard() {}
    public boolean isRemoved() { throw new UnsupportedOperationException(); }
    public boolean isAlive() { throw new UnsupportedOperationException(); }
    public java.util.UUID getUUID() { throw new UnsupportedOperationException(); }
    public String getStringUUID() { throw new UnsupportedOperationException(); }
    public void setGlowingTag(boolean glowing) {}
    public net.minecraft.world.phys.AABB getBoundingBox() { throw new UnsupportedOperationException(); }
    public float getBbWidth() { throw new UnsupportedOperationException(); }
    public float getBbHeight() { throw new UnsupportedOperationException(); }
    public void setNoGravity(boolean noGravity) {}
    public void setSecondsOnFire(int seconds) {}
    public net.minecraft.util.RandomSource getRandom() { throw new UnsupportedOperationException(); }
    public net.minecraft.network.chat.Component getDisplayName() { throw new UnsupportedOperationException(); }
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) { throw new UnsupportedOperationException(); }
    public boolean isPickable() { throw new UnsupportedOperationException(); }
    public boolean isPushable() { throw new UnsupportedOperationException(); }
    public boolean isAttackable() { throw new UnsupportedOperationException(); }
    public void setRemoved(RemovalReason reason) {}
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {}
}
