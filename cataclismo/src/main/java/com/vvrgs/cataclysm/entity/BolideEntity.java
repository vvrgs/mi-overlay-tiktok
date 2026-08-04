package com.vvrgs.cataclysm.entity;

import com.vvrgs.cataclysm.core.Physics;
import com.vvrgs.cataclysm.core.TerrainBudget;
import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.registry.ModDamageTypes;
import com.vvrgs.cataclysm.registry.ModParticles;
import com.vvrgs.cataclysm.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bolido: meteorito balistico con estela de plasma y spin. Impacta donde
 * marco el telegraph, excava crater amortizado, deja zona ardiente breve.
 */
public class BolideEntity extends DisasterEntity {

    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(BolideEntity.class, EntityDataSerializers.FLOAT);

    private float damage = 9.0F;
    private int craterRadius = 2;
    private float blastStrength = 1.2F;

    public BolideEntity(EntityType<?> type, Level level) {
        super(type, level);
        setLifeTicks(20 * 30);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SIZE, 1.0F);
    }

    public void configure(Vec3 velocity, float size, float damage, int craterRadius, float blastStrength) {
        this.setDeltaMovement(velocity);
        this.entityData.set(DATA_SIZE, size);
        this.damage = damage;
        this.craterRadius = craterRadius;
        this.blastStrength = blastStrength;
    }

    public float getSize() {
        return this.entityData.get(DATA_SIZE);
    }

    @Override
    protected void serverTick() {
        Vec3 motion = this.getDeltaMovement();
        Vec3 from = this.position();
        Vec3 to = from.add(motion);
        // raycast del paso completo: a 2+ b/t un solo checkeo de bloque se salta paredes
        BlockHitResult hit = this.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            impact(hit.getLocation());
            return;
        }
        this.setPos(to);
        this.setDeltaMovement(motion.add(0.0D, -0.015D, 0.0D));
        // orientar el morro a la trayectoria
        this.setYRot((float) (Mth.atan2(motion.x, motion.z) * (180.0D / Math.PI)));
        this.setXRot((float) (Mth.atan2(motion.y, motion.horizontalDistance()) * (180.0D / Math.PI)));
    }

    private void impact(Vec3 at) {
        ServerLevel level = (ServerLevel) this.level();
        float size = getSize();

        FxDirector.fire(level, FxEvent.METEOR_IMPACT, at, size);
        FxDirector.sound(level, at, ModSounds.IMPACT_BLAST.get(), 3.0F,
                1.1F - size * 0.2F + this.random.nextFloat() * 0.15F);

        // crater amortizado
        BlockPos center = BlockPos.containing(at);
        int r = craterRadius;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (pos.distSqr(center) <= (double) (r * r)) {
                TerrainBudget.carve(level, pos);
            }
        }
        // zona ardiente breve alrededor del crater
        for (int i = 0; i < 6; i++) {
            BlockPos firePos = center.offset(
                    this.random.nextInt(r * 2 + 3) - r - 1,
                    1,
                    this.random.nextInt(r * 2 + 3) - r - 1);
            if (level.isLoaded(firePos) && level.getBlockState(firePos).isAir()
                    && level.getBlockState(firePos.below()).isSolidRender(level, firePos.below())) {
                TerrainBudget.place(level, firePos, Blocks.FIRE.defaultBlockState());
            }
        }

        // dano + onda expansiva
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(4.0D + size * 2.0D))) {
            living.hurt(ModDamageTypes.source(level, ModDamageTypes.METEOR), damage);
            Physics.blast(living, at, 6.0D + size * 2.0D, blastStrength, 0.4D);
        }
        this.discard();
    }

    @Override
    protected void clientTick() {
        // estela de plasma con spin
        Vec3 motion = this.getDeltaMovement();
        float size = getSize();
        for (int i = 0; i < 2; i++) {
            double back = 0.5D * i;
            this.level().addParticle(ModParticles.PLASMA.get(),
                    this.getX() - motion.x * back + (this.random.nextDouble() - 0.5D) * size * 0.6D,
                    this.getY() - motion.y * back + (this.random.nextDouble() - 0.5D) * size * 0.6D,
                    this.getZ() - motion.z * back + (this.random.nextDouble() - 0.5D) * size * 0.6D,
                    -motion.x * 0.1D, -motion.y * 0.1D, -motion.z * 0.1D);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putInt("CraterRadius", craterRadius);
        tag.putFloat("BlastStrength", blastStrength);
        tag.putFloat("Size", getSize());
        Vec3 motion = getDeltaMovement();
        tag.putDouble("VelX", motion.x);
        tag.putDouble("VelY", motion.y);
        tag.putDouble("VelZ", motion.z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
        craterRadius = tag.getInt("CraterRadius");
        blastStrength = tag.getFloat("BlastStrength");
        this.entityData.set(DATA_SIZE, tag.getFloat("Size"));
        this.setDeltaMovement(tag.getDouble("VelX"), tag.getDouble("VelY"), tag.getDouble("VelZ"));
    }
}
