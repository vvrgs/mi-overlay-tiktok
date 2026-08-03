package com.vvrgs.irontempest.registry;

import com.vvrgs.irontempest.IronTempest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** Damage types data-driven (JSON en data/irontempest/damage_type). */
public final class ModDamage {
    public static final ResourceKey<DamageType> TANK_SHELL = key("tank_shell");
    public static final ResourceKey<DamageType> MISSILE = key("missile");
    public static final ResourceKey<DamageType> ROCKET = key("rocket");
    public static final ResourceKey<DamageType> ORBITAL_BEAM = key("orbital_beam");
    public static final ResourceKey<DamageType> SHOCKWAVE = key("shockwave");
    /** Pulso trituradora de tótems: bypasses_cooldown+armor+enchantments, JAMÁS bypasses_invulnerability. */
    public static final ResourceKey<DamageType> TOTEM_SHRED = key("totem_shred");
    /** DoT perforante del modo streamer (ignora armadura y Protection). */
    public static final ResourceKey<DamageType> NAPALM = key("napalm");

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(IronTempest.MODID, name));
    }

    public static DamageSource source(ServerLevel level, ResourceKey<DamageType> key, @Nullable Entity direct) {
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(key);
        return new DamageSource(holder, direct);
    }

    private ModDamage() {}
}
