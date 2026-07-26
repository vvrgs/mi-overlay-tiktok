package com.vvrgs.irontempest.registry;

import com.vvrgs.irontempest.IronTempest;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 11 tipos de partícula propios — política "cero partículas vanilla". */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, IronTempest.MODID);

    public static final RegistryObject<SimpleParticleType> FIREBALL = simple("fireball");
    public static final RegistryObject<SimpleParticleType> FLASH = simple("flash");
    public static final RegistryObject<SimpleParticleType> SHOCKWAVE = simple("shockwave");
    public static final RegistryObject<SimpleParticleType> SMOKE = simple("smoke");
    public static final RegistryObject<SimpleParticleType> SPARK = simple("spark");
    public static final RegistryObject<SimpleParticleType> DEBRIS = simple("debris");
    public static final RegistryObject<SimpleParticleType> TRACER = simple("tracer");
    public static final RegistryObject<SimpleParticleType> MUZZLE_FLASH = simple("muzzle_flash");
    public static final RegistryObject<SimpleParticleType> CHARGE_MOTE = simple("charge_mote");
    public static final RegistryObject<SimpleParticleType> WARP_FLASH = simple("warp_flash");
    public static final RegistryObject<SimpleParticleType> EMBER = simple("ember");

    private static RegistryObject<SimpleParticleType> simple(String name) {
        // true = overrideLimiter/alwaysShow: sin esto el LevelRenderer culla toda
        // partícula a >32 bloques y los FX cinemáticos lejanos (nave orbital a 38,
        // misiles a 45) serían invisibles. También ignora el ajuste "Mínimo".
        return REGISTER.register(name, () -> new SimpleParticleType(true));
    }

    private ModParticles() {}
}
