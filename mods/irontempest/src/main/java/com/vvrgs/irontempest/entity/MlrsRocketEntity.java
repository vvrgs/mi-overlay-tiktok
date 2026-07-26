package com.vvrgs.irontempest.entity;

import com.vvrgs.irontempest.net.FxType;
import com.vvrgs.irontempest.net.ModNetwork;
import com.vvrgs.irontempest.registry.ModDamage;
import com.vvrgs.irontempest.registry.ModParticles;
import com.vvrgs.irontempest.server.util.DamageUtil;
import com.vvrgs.irontempest.server.util.TerrainSculptor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Cohete MLRS del tier S: cae en arco cerrado con silbido, impacto pequeño. */
public class MlrsRocketEntity extends AbstractWarProjectile {

    public MlrsRocketEntity(EntityType<? extends MlrsRocketEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected double gravity() {
        return 0.03D;
    }

    @Override
    protected int maxLife() {
        return 140;
    }

    @Override
    protected void onImpact(HitResult hit) {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 pos = hit.getLocation();
        ModNetwork.fx(server, FxType.EXPLOSION_SMALL, pos, getDeltaMovement().normalize(), 1.0F);
        TerrainSculptor.scorch(server, BlockPos.containing(pos).below());
        // Tier S: daño radial honesto, sin insta-kill (spameable).
        DamageUtil.radialDamage(server, pos, 4.5D, 11.0F, ModDamage.ROCKET, this);
        discard();
    }

    @Override
    protected void clientTrail() {
        Vec3 vel = getDeltaMovement();
        Vec3 back = position().subtract(vel.scale(0.6D));
        this.level().addParticle(ModParticles.EMBER.get(), back.x, back.y, back.z,
                -vel.x * 0.1D, -vel.y * 0.1D, -vel.z * 0.1D);
        this.level().addParticle(ModParticles.SMOKE.get(), back.x, back.y, back.z,
                this.random.nextGaussian() * 0.02D, 0.02D, this.random.nextGaussian() * 0.02D);
    }
}
