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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bomba volcanica: proyectil balistico en arco alto dirigido AL jugador
 * (con telegraph donde caera), estela de brasas y humo, salpicadura de
 * fuego al impactar.
 */
public class VolcanicBombEntity extends DisasterEntity {

    private static final double GRAVITY = -0.045D;
    private float damage = 8.0F;

    public VolcanicBombEntity(EntityType<?> type, Level level) {
        super(type, level);
        setLifeTicks(20 * 20);
    }

    @Override
    protected void defineSynchedData() {
    }

    public void configure(Vec3 velocity, float damage) {
        this.setDeltaMovement(velocity);
        this.damage = damage;
    }

    /** Velocidad inicial para caer sobre un objetivo en flightTicks ticks (arco balistico). */
    public static Vec3 arcVelocity(Vec3 from, Vec3 to, int flightTicks) {
        double t = flightTicks;
        double vx = (to.x - from.x) / t;
        double vz = (to.z - from.z) / t;
        // y(t) = y0 + vy*t + g*t^2/2  =>  vy = (dy - g*t^2/2) / t
        double vy = ((to.y - from.y) - GRAVITY * t * t / 2.0D) / t;
        return new Vec3(vx, vy, vz);
    }

    @Override
    protected void serverTick() {
        Vec3 motion = this.getDeltaMovement();
        Vec3 from = this.position();
        Vec3 to = from.add(motion);
        BlockHitResult hit = this.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS) {
            impact(hit.getLocation());
            return;
        }
        this.setPos(to);
        this.setDeltaMovement(motion.add(0.0D, GRAVITY, 0.0D));
        this.setYRot(this.getYRot() + 23.0F); // spin caotico
    }

    private void impact(Vec3 at) {
        ServerLevel level = (ServerLevel) this.level();
        FxDirector.fire(level, FxEvent.LAVA_FOUNTAIN, at, 0.7F);
        FxDirector.sound(level, at, ModSounds.IMPACT_BLAST.get(), 2.2F,
                1.3F + this.random.nextFloat() * 0.2F);

        BlockPos center = BlockPos.containing(at);
        // pequeno socavon + salpicadura de fuego
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 0, 1))) {
            TerrainBudget.carve(level, pos);
        }
        for (int i = 0; i < 4; i++) {
            BlockPos firePos = center.offset(this.random.nextInt(5) - 2, 1, this.random.nextInt(5) - 2);
            if (level.isLoaded(firePos) && level.getBlockState(firePos).isAir()
                    && level.getBlockState(firePos.below()).isSolidRender(level, firePos.below())) {
                TerrainBudget.place(level, firePos, Blocks.FIRE.defaultBlockState());
            }
        }
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(3.5D))) {
            living.invulnerableTime = 0;
            living.hurt(ModDamageTypes.source(level, ModDamageTypes.METEOR), damage);
            living.setSecondsOnFire(3);
            Physics.blast(living, at, 5.0D, 0.8D, 0.35D);
        }
        this.discard();
    }

    @Override
    protected void clientTick() {
        this.level().addParticle(ModParticles.EMBER.get(),
                this.getX() + (this.random.nextDouble() - 0.5D) * 0.8D,
                this.getY() + (this.random.nextDouble() - 0.5D) * 0.8D,
                this.getZ() + (this.random.nextDouble() - 0.5D) * 0.8D,
                0.0D, 0.02D, 0.0D);
        if (this.tickCount % 2 == 0) {
            this.level().addParticle(ModParticles.SMOKE.get(),
                    this.getX(), this.getY() + 0.4D, this.getZ(),
                    0.0D, 0.05D, 0.0D);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        Vec3 motion = getDeltaMovement();
        tag.putDouble("VelX", motion.x);
        tag.putDouble("VelY", motion.y);
        tag.putDouble("VelZ", motion.z);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
        this.setDeltaMovement(tag.getDouble("VelX"), tag.getDouble("VelY"), tag.getDouble("VelZ"));
    }
}
