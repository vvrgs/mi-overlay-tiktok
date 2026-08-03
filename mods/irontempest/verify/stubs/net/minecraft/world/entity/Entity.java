package net.minecraft.world.entity;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public abstract class Entity {
    public final RandomSource random = null;
    protected final SynchedEntityData entityData = null;
    public boolean noPhysics;
    public int tickCount;
    public float yRotO;
    public float xRotO;

    protected Entity(EntityType<?> type, Level level) {}

    public Level level() { throw new UnsupportedOperationException(); }
    public void tick() {}
    public void discard() {}
    public void remove(RemovalReason reason) {}
    public boolean isRemoved() { return false; }
    public Vec3 getDeltaMovement() { throw new UnsupportedOperationException(); }
    public void setDeltaMovement(Vec3 motion) {}
    public void setDeltaMovement(double x, double y, double z) {}
    public Vec3 position() { throw new UnsupportedOperationException(); }
    public Vec3 getPosition(float partialTicks) { throw new UnsupportedOperationException(); }
    public void setPos(double x, double y, double z) {}
    public void setPos(Vec3 pos) {}
    // Posición del tick anterior (API real 1.20.1: campos públicos xo/yo/zo y xOld/yOld/zOld)
    public double xo;
    public double yo;
    public double zo;
    public double xOld;
    public double yOld;
    public double zOld;

    public boolean hurtMarked;
    public int invulnerableTime;
    public void setSecondsOnFire(int seconds) {}

    public double getX() { return 0.0D; }
    public double getY() { return 0.0D; }
    public double getZ() { return 0.0D; }
    public int getBlockX() { return 0; }
    public int getBlockY() { return 0; }
    public int getBlockZ() { return 0; }
    public float getYRot() { return 0.0F; }
    public void setYRot(float yRot) {}
    public float getXRot() { return 0.0F; }
    public void setXRot(float xRot) {}
    public boolean onGround() { return false; }
    public void move(MoverType type, Vec3 movement) {}
    public AABB getBoundingBox() { throw new UnsupportedOperationException(); }
    public boolean isSpectator() { return false; }
    public boolean isAlive() { return false; }
    public boolean isPickable() { return false; }
    public boolean isPushable() { return false; }
    public boolean shouldBeSaved() { return true; }
    public UUID getUUID() { throw new UnsupportedOperationException(); }
    public String getStringUUID() { throw new UnsupportedOperationException(); }
    public void setGlowingTag(boolean glowing) {}

    public enum RemovalReason { KILLED, DISCARDED, UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER, CHANGED_DIMENSION }
    public int getId() { return 0; }
    public float getBbHeight() { return 0.0F; }
    public float getBbWidth() { return 0.0F; }
    public double distanceToSqr(double x, double y, double z) { return 0.0D; }
    public double distanceToSqr(Entity other) { return 0.0D; }
    public boolean hurt(DamageSource source, float amount) { return false; }

    protected abstract void defineSynchedData();
    protected abstract void readAdditionalSaveData(CompoundTag tag);
    protected abstract void addAdditionalSaveData(CompoundTag tag);
}
