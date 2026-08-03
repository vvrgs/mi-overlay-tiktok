package com.vvrgs.irontempest.entity;

import com.vvrgs.irontempest.net.FxType;
import com.vvrgs.irontempest.net.ModNetwork;
import com.vvrgs.irontempest.registry.ModDamage;
import com.vvrgs.irontempest.registry.ModParticles;
import com.vvrgs.irontempest.server.session.SessionManager;
import com.vvrgs.irontempest.server.util.DamageUtil;
import com.vvrgs.irontempest.server.util.SustainedDamage;
import com.vvrgs.irontempest.server.util.TerrainSculptor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Obús perforante de tanque: tiro tenso, trazador, cráter r=3. */
public class TankShellEntity extends AbstractWarProjectile {

    public TankShellEntity(EntityType<? extends TankShellEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected double gravity() {
        return 0.02D;
    }

    @Override
    protected double drag() {
        return 0.995D; // arrastre aerodinámico real
    }

    @Override
    protected int maxLife() {
        return 200;
    }

    @Override
    protected void onImpact(HitResult hit) {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 pos = hit.getLocation();
        ModNetwork.fx(server, FxType.EXPLOSION_LARGE, pos, getDeltaMovement().normalize(), 1.2F);
        TerrainSculptor.crater(server, BlockPos.containing(pos), 3, true);
        DamageUtil.strikeDamage(server, pos, 1.6D, 5.5D, 22.0F, ModDamage.TANK_SHELL, this);
        DamageUtil.blastImpulse(server, pos, 7.0D, 1.1D);
        // Daño SOSTENIDO: el cráter arde 4 s y los alcanzados siguen quemándose 3 s.
        SustainedDamage.zone(server, pos, 4.0D, 4.0D, 3.0F, ModDamage.TANK_SHELL, true);
        SustainedDamage.afflictArea(server, pos, 5.5D, 3.0D, 2.5F, ModDamage.TANK_SHELL, true);
        SessionManager.notifyProjectileImpact(this.sessionId);
        discard();
    }

    @Override
    protected void clientTrail() {
        Vec3 vel = getDeltaMovement();
        Vec3 back = position().subtract(vel.scale(0.5D));
        this.level().addParticle(ModParticles.TRACER.get(), back.x, back.y, back.z,
                vel.x * 0.2D, vel.y * 0.2D, vel.z * 0.2D);
        if (this.life % 2 == 0) {
            this.level().addParticle(ModParticles.SMOKE.get(), back.x, back.y, back.z,
                    0.0D, 0.01D, 0.0D);
        }
    }
}
