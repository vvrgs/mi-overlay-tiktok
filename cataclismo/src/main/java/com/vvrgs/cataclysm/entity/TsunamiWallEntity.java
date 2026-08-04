package com.vvrgs.cataclysm.entity;

import com.vvrgs.cataclysm.core.Anchors;
import com.vvrgs.cataclysm.core.Physics;
import com.vvrgs.cataclysm.registry.ModDamageTypes;
import com.vvrgs.cataclysm.registry.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Segmento del muro de agua (la sesion pone 5-6 lado a lado = frente de 40).
 * Avanza pegado al terreno, arrasa con impulso masivo + dano, y la resaca
 * la gestiona la sesion. El agua real temporal tambien es de la sesion
 * (colocada/retirada amortizadamente).
 */
public class TsunamiWallEntity extends DisasterEntity {

    public static final double SPEED = 0.45D;

    /** rumbo del avance en radianes (se guarda aparte del yaw de render) */
    private double headingRad;
    private int sweepTimer;

    public TsunamiWallEntity(EntityType<?> type, Level level) {
        super(type, level);
        setLifeTicks(20 * 45);
    }

    @Override
    protected void defineSynchedData() {
    }

    public void setHeading(double radians) {
        this.headingRad = radians;
        this.setYRot((float) (radians * 180.0D / Math.PI));
    }

    public double getHeading() {
        return headingRad;
    }

    @Override
    protected void serverTick() {
        ServerLevel level = (ServerLevel) this.level();
        double dx = Math.sin(headingRad) * SPEED;
        double dz = Math.cos(headingRad) * SPEED;
        double nx = this.getX() + dx;
        double nz = this.getZ() + dz;
        double surface = Anchors.surfaceY(level, Mth.floor(nx), Mth.floor(nz));
        // el muro remonta suavemente el terreno; un acantilado de 4+ lo rompe
        if (surface - this.getY() > 4.0D) {
            this.discard();
            return;
        }
        double ny = this.getY() + Mth.clamp(surface - this.getY(), -0.6D, 0.6D);
        this.setPos(nx, ny, nz);

        // arrasa: impulso masivo en la direccion del avance + dano periodico
        Vec3 push = new Vec3(dx, 0.0D, dz).normalize().scale(0.55D).add(0.0D, 0.28D, 0.0D);
        boolean damageTick = ++sweepTimer >= 10;
        if (damageTick) {
            sweepTimer = 0;
        }
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(2.0D, 3.0D, 2.0D))) {
            Physics.wind(living, push);
            if (damageTick) {
                living.hurt(ModDamageTypes.source(level, ModDamageTypes.TSUNAMI), 4.0F);
            }
        }
    }

    @Override
    protected void clientTick() {
        // espuma y bruma en la cresta. headingRad es campo de SERVER: en el
        // cliente se deriva del yRot (que si viaja por red via setHeading)
        double heading = Math.toRadians(this.getYRot());
        float height = this.getBbHeight();
        for (int i = 0; i < 3; i++) {
            this.level().addParticle(ModParticles.SPRAY.get(),
                    this.getX() + (this.random.nextDouble() - 0.5D) * this.getBbWidth(),
                    this.getY() + height * (0.75D + this.random.nextDouble() * 0.3D),
                    this.getZ() + (this.random.nextDouble() - 0.5D) * this.getBbWidth(),
                    Math.sin(heading) * 0.3D,
                    0.1D + this.random.nextDouble() * 0.1D,
                    Math.cos(heading) * 0.3D);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("Heading", headingRad);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setHeading(tag.getDouble("Heading"));
    }
}
