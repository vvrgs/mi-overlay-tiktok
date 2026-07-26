package com.vvrgs.irontempest.entity;

import com.vvrgs.irontempest.server.session.SessionManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Proyectil de guerra con integración balística propia (raycast por tick,
 * bloqueo + entidades). Sin física vanilla: control total del vuelo.
 */
public abstract class AbstractWarProjectile extends Entity {

    protected int sessionId = -1;
    protected int life;

    protected AbstractWarProjectile(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setSessionId(int id) {
        this.sessionId = id;
    }

    /** Gravedad en bloques/tick². */
    protected abstract double gravity();

    /** Vida máxima en ticks (seguro anti-huérfanos). */
    protected abstract int maxLife();

    /** Impacto contra bloque o entidad (solo servidor). */
    protected abstract void onImpact(HitResult hit);

    /** Estela en cliente, cada tick. */
    protected abstract void clientTrail();

    @Override
    public void tick() {
        super.tick();
        this.life++;

        if (this.level().isClientSide) {
            clientTrail();
            return;
        }

        // Sesión muerta o vida agotada → nunca dejar proyectiles huérfanos.
        if (this.life > maxLife()
                || (this.sessionId >= 0 && !SessionManager.isSessionActive(this.sessionId))) {
            discard();
            return;
        }

        Vec3 vel = getDeltaMovement().add(0.0D, -gravity(), 0.0D);
        setDeltaMovement(vel);

        Vec3 from = position();
        Vec3 to = from.add(vel);

        HitResult hit = this.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = hit.getType() != HitResult.Type.MISS ? hit.getLocation() : to;

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(this.level(), this, from, end,
                getBoundingBox().expandTowards(vel).inflate(1.0D),
                e -> e instanceof LivingEntity && !e.isSpectator() && e.isAlive());
        if (entityHit != null) {
            hit = entityHit;
        }

        if (hit.getType() != HitResult.Type.MISS) {
            onImpact(hit);
            return;
        }

        setPos(to);
        updateRotationFromVelocity();
    }

    /**
     * Alinear rotación con la velocidad ANTES de addFreshEntity: el paquete de
     * spawn lleva yaw/pitch, sin esto el cliente ve 1-2 frames el modelo
     * horizontal mirando al sur antes del primer tick.
     */
    public void alignToVelocity() {
        updateRotationFromVelocity();
        this.yRotO = getYRot();
        this.xRotO = getXRot();
    }

    protected void updateRotationFromVelocity() {
        Vec3 vel = getDeltaMovement();
        if (vel.lengthSqr() > 1.0E-6D) {
            double horiz = vel.horizontalDistance();
            // Convención FLECHA (yaw = atan2(vx, vz), como AbstractArrow); los
            // renderers de proyectiles aplican YP(180+yaw) sobre modelos con el
            // morro a -Z. Pitch positivo = subiendo. NO cambiar sin tocar renderers.
            float newYaw = (float) (Mth.atan2(vel.x, vel.z) * Mth.RAD_TO_DEG);
            float newPitch = (float) (Mth.atan2(vel.y, horiz) * Mth.RAD_TO_DEG);
            this.yRotO = getYRot();
            this.xRotO = getXRot();
            setYRot(newYaw);
            setXRot(newPitch);
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false; // los ataques nunca sobreviven a un reinicio
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
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
