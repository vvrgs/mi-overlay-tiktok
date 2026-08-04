package com.vvrgs.cataclysm.entity;

import com.vvrgs.cataclysm.core.Anchors;
import com.vvrgs.cataclysm.core.Physics;
import com.vvrgs.cataclysm.core.TerrainBudget;
import com.vvrgs.cataclysm.fx.FxDirector;
import com.vvrgs.cataclysm.fx.FxEvent;
import com.vvrgs.cataclysm.registry.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Embudo procedural: PERSIGUE al chase target a 0.4 b/t siguiendo el
 * terreno, succiona fisicamente entidades e items en radio 12 (impulso
 * tangencial + ascendente real), arranca bloques de superficie (amortizado)
 * que orbitan visualmente el embudo, y lanza al cielo a quien atrapa.
 */
public class TornadoEntity extends DisasterEntity {

    private static final EntityDataAccessor<Float> DATA_INTENSITY =
            SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);

    public static final double CHASE_SPEED = 0.4D;
    public static final double SUCTION_RADIUS = 12.0D;
    private static final double CATCH_RADIUS = 2.8D;
    private static final double LAUNCH_VELOCITY = 2.6D;

    private double chaseX;
    private double chaseY;
    private double chaseZ;
    private int debrisTimer;
    private int damageTimer;

    public TornadoEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_INTENSITY, 0.0F);
    }

    public void setIntensity(float intensity) {
        this.entityData.set(DATA_INTENSITY, Mth.clamp(intensity, 0.0F, 1.0F));
    }

    public float getIntensity() {
        return this.entityData.get(DATA_INTENSITY);
    }

    public void setChaseTarget(Vec3 pos) {
        this.chaseX = pos.x;
        this.chaseY = pos.y;
        this.chaseZ = pos.z;
    }

    @Override
    protected void serverTick() {
        ServerLevel level = (ServerLevel) this.level();
        float intensity = getIntensity();

        // persecucion horizontal a velocidad fija, pegado al terreno
        Vec3 pos = this.position();
        Vec3 toChase = new Vec3(chaseX - pos.x, 0.0D, chaseZ - pos.z);
        double dist = toChase.length();
        if (dist > 1.0D) {
            Vec3 step = toChase.scale(CHASE_SPEED / dist);
            double nx = pos.x + step.x;
            double nz = pos.z + step.z;
            double ny = Anchors.surfaceY(level, Mth.floor(nx), Mth.floor(nz));
            // el embudo no escala paredes verticales de golpe: suaviza
            ny = pos.y + Mth.clamp(ny - pos.y, -1.0D, 1.0D);
            this.setPos(nx, ny, nz);
        }
        this.setYRot(this.getYRot() + 17.0F); // spin del yaw para el render

        if (intensity < 0.05F) {
            return; // aun naciendo / muriendo: sin fisica
        }

        // succion fisica real sobre entidades e items
        AABB range = this.getBoundingBox().inflate(SUCTION_RADIUS, SUCTION_RADIUS * 0.7D, SUCTION_RADIUS);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, range,
                e -> e != this && !(e instanceof DisasterEntity)
                        && (e instanceof LivingEntity || e instanceof ItemEntity))) {
            double d = entity.position().distanceTo(this.position());
            if (d < CATCH_RADIUS && entity instanceof Player) {
                // atrapado: lanzamiento al cielo
                entity.setDeltaMovement(entity.getDeltaMovement()
                        .add(0.0D, LAUNCH_VELOCITY * intensity, 0.0D));
                entity.hurtMarked = true;
            } else {
                Physics.suction(entity, this.position().add(0, 2, 0), SUCTION_RADIUS,
                        0.10D * intensity, 0.22D * intensity, 0.06D * intensity);
            }
        }

        // metralla periodica a quien orbite cerca
        if (++damageTimer >= 10) {
            damageTimer = 0;
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class,
                    this.getBoundingBox().inflate(4.0D))) {
                living.hurt(ModDamageTypes.source(level, ModDamageTypes.DEBRIS), 2.0F * intensity);
            }
        }

        // arranca bloques de superficie (amortizado) que orbitan el embudo
        if (++debrisTimer >= 3) {
            debrisTimer = 0;
            int radius = 4 + this.random.nextInt(5);
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            int bx = Mth.floor(this.getX() + Math.cos(angle) * radius);
            int bz = Mth.floor(this.getZ() + Math.sin(angle) * radius);
            int by = Anchors.surfaceY(level, bx, bz) - 1;
            BlockPos target = new BlockPos(bx, by, bz);
            BlockState state = level.getBlockState(target);
            if (!state.isAir() && state.getDestroySpeed(level, target) >= 0.0F
                    && state.getFluidState().isEmpty()) {
                TerrainBudget.carve(level, target);
                FxDirector.fire(level, FxEvent.TORNADO_DEBRIS, this.position(),
                        intensity, Block.getId(state));
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("ChaseX", chaseX);
        tag.putDouble("ChaseY", chaseY);
        tag.putDouble("ChaseZ", chaseZ);
        tag.putFloat("Intensity", getIntensity());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        chaseX = tag.getDouble("ChaseX");
        chaseY = tag.getDouble("ChaseY");
        chaseZ = tag.getDouble("ChaseZ");
        setIntensity(tag.getFloat("Intensity"));
    }
}
