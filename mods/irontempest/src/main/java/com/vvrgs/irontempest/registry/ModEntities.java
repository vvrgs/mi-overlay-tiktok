package com.vvrgs.irontempest.registry;

import com.vvrgs.irontempest.IronTempest;
import com.vvrgs.irontempest.entity.CruiseMissileEntity;
import com.vvrgs.irontempest.entity.MlrsRocketEntity;
import com.vvrgs.irontempest.entity.TankEntity;
import com.vvrgs.irontempest.entity.TankShellEntity;
import com.vvrgs.irontempest.entity.WarshipEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, IronTempest.MODID);

    public static final RegistryObject<EntityType<TankEntity>> WAR_TANK = REGISTER.register("war_tank",
            () -> EntityType.Builder.<TankEntity>of(TankEntity::new, MobCategory.MISC)
                    .sized(3.1F, 1.9F)
                    .clientTrackingRange(12)
                    .updateInterval(2)
                    .fireImmune()
                    .build("war_tank"));

    public static final RegistryObject<EntityType<TankShellEntity>> TANK_SHELL = REGISTER.register("tank_shell",
            () -> EntityType.Builder.<TankShellEntity>of(TankShellEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .fireImmune()
                    .build("tank_shell"));

    public static final RegistryObject<EntityType<CruiseMissileEntity>> CRUISE_MISSILE = REGISTER.register("cruise_missile",
            () -> EntityType.Builder.<CruiseMissileEntity>of(CruiseMissileEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.9F)
                    .clientTrackingRange(14)
                    .updateInterval(1)
                    .fireImmune()
                    .build("cruise_missile"));

    public static final RegistryObject<EntityType<MlrsRocketEntity>> MLRS_ROCKET = REGISTER.register("mlrs_rocket",
            () -> EntityType.Builder.<MlrsRocketEntity>of(MlrsRocketEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .fireImmune()
                    .build("mlrs_rocket"));

    public static final RegistryObject<EntityType<WarshipEntity>> WARSHIP = REGISTER.register("warship",
            () -> EntityType.Builder.<WarshipEntity>of(WarshipEntity::new, MobCategory.MISC)
                    .sized(6.0F, 2.5F)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .fireImmune()
                    .build("warship"));

    private ModEntities() {}
}
