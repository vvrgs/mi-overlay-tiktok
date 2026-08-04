package com.vvrgs.cataclysm.registry;

import com.vvrgs.cataclysm.Cataclysm;
import com.vvrgs.cataclysm.entity.BolideEntity;
import com.vvrgs.cataclysm.entity.ImpactorEntity;
import com.vvrgs.cataclysm.entity.TornadoEntity;
import com.vvrgs.cataclysm.entity.TsunamiWallEntity;
import com.vvrgs.cataclysm.entity.VolcanicBombEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entidades set-piece. Todas: updateInterval 1 (interpolacion fina),
 * fireImmune, sin summon natural. El tracking range va en CHUNKS.
 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Cataclysm.MODID);

    public static final RegistryObject<EntityType<TornadoEntity>> TORNADO =
            REGISTER.register("tornado", () -> EntityType.Builder
                    .of(TornadoEntity::new, MobCategory.MISC)
                    .sized(5.0F, 14.0F)
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .fireImmune()
                    .build("tornado"));

    public static final RegistryObject<EntityType<BolideEntity>> BOLIDE =
            REGISTER.register("bolide", () -> EntityType.Builder
                    .of(BolideEntity::new, MobCategory.MISC)
                    .sized(1.6F, 1.6F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build("bolide"));

    public static final RegistryObject<EntityType<VolcanicBombEntity>> VOLCANIC_BOMB =
            REGISTER.register("volcanic_bomb", () -> EntityType.Builder
                    .of(VolcanicBombEntity::new, MobCategory.MISC)
                    .sized(1.1F, 1.1F)
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .fireImmune()
                    .build("volcanic_bomb"));

    public static final RegistryObject<EntityType<TsunamiWallEntity>> TSUNAMI_WALL =
            REGISTER.register("tsunami_wall", () -> EntityType.Builder
                    .of(TsunamiWallEntity::new, MobCategory.MISC)
                    .sized(7.5F, 11.0F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build("tsunami_wall"));

    public static final RegistryObject<EntityType<ImpactorEntity>> IMPACTOR =
            REGISTER.register("impactor", () -> EntityType.Builder
                    .of(ImpactorEntity::new, MobCategory.MISC)
                    .sized(4.0F, 4.0F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .fireImmune()
                    .build("impactor"));

    private ModEntities() {
    }
}
