package com.vvrgs.cataclysm.entity;

import com.vvrgs.cataclysm.core.GlowTeams;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Base de toda entidad set-piece.
 *
 * Reglas aprendidas a golpes:
 *  - Glow cleanup en setRemoved, NUNCA en remove(): la descarga de chunks no
 *    pasa por remove() y deja UUIDs huerfanos en scoreboard.dat.
 *  - Interpolacion de red estilo Boat (lerpTo con 3 pasos): sin ella el yaw
 *    cuantizado a ~1.4 grados salta.
 *  - NBT COMPLETO: el re-anclaje cross-dim recrea la entidad via NBT; un
 *    addAdditionalSaveData vacio = estado perdido = sesion huerfana.
 *  - lifeTicks failsafe propio: aunque la sesion muera sin limpiar, la
 *    entidad se descarta sola.
 */
public abstract class DisasterEntity extends Entity {

    protected int lifeTicks = 20 * 180;
    private boolean glowRegistered;

    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;
    private int lerpSteps;

    protected DisasterEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setLifeTicks(int ticks) {
        this.lifeTicks = ticks;
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
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public void setRemoved(Entity.RemovalReason reason) {
        if (!this.level().isClientSide()) {
            GlowTeams.unregister(this);
        }
        super.setRemoved(reason);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot,
                       int steps, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = 3;
    }

    private void tickLerp() {
        if (this.lerpSteps <= 0) {
            return;
        }
        double x = this.getX() + (this.lerpX - this.getX()) / this.lerpSteps;
        double y = this.getY() + (this.lerpY - this.getY()) / this.lerpSteps;
        double z = this.getZ() + (this.lerpZ - this.getZ()) / this.lerpSteps;
        this.setYRot(this.getYRot() + (float) Mth.wrapDegrees(this.lerpYRot - this.getYRot()) / this.lerpSteps);
        this.setXRot(this.getXRot() + (float) (this.lerpXRot - this.getXRot()) / this.lerpSteps);
        this.lerpSteps--;
        this.setPos(x, y, z);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            tickLerp();
            clientTick();
            return;
        }
        if (!glowRegistered) {
            glowRegistered = true;
            GlowTeams.register(this);
        }
        if (--lifeTicks <= 0) {
            this.discard();
            return;
        }
        serverTick();
    }

    /** Particulas/sonido locales de la entidad (solo cliente). */
    protected void clientTick() {
    }

    protected abstract void serverTick();

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("LifeTicks", lifeTicks);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("LifeTicks")) {
            lifeTicks = tag.getInt("LifeTicks");
        }
    }
}
