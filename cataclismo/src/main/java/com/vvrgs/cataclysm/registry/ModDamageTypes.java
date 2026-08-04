package com.vvrgs.cataclysm.registry;

import com.vvrgs.cataclysm.Cataclysm;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/**
 * Damage types data-driven (JSON en data/cataclysm/damage_type + tags en
 * data/minecraft/tags/damage_type).
 *
 * REGLA SAGRADA: NINGUNO lleva bypasses_invulnerability — el totem SIEMPRE
 * puede salvar. El espectaculo del live es ver los totems reventar en cadena.
 */
public final class ModDamageTypes {

    /** Impacto de meteorito / bomba volcanica (is_explosion). */
    public static final ResourceKey<DamageType> METEOR = key("meteor");
    /** Rayo dirigido propio (is_lightning). */
    public static final ResourceKey<DamageType> LIGHTNING = key("lightning");
    /** DoT napalm de la tormenta de fuego (is_fire, perfora todo menos el totem). */
    public static final ResourceKey<DamageType> NAPALM = key("napalm");
    /** Aplastamiento / fisura del terremoto. */
    public static final ResourceKey<DamageType> QUAKE = key("quake");
    /** Golpe de agua del tsunami (is_drowning). */
    public static final ResourceKey<DamageType> TSUNAMI = key("tsunami");
    /** Flujo piroclastico del volcan (is_fire, letal salvo totem). */
    public static final ResourceKey<DamageType> PYROCLASTIC = key("pyroclastic");
    /** Onda expansiva del impacto planetario (is_explosion). */
    public static final ResourceKey<DamageType> SHOCKWAVE = key("shockwave");
    /** Golpe letal de la trituradora: 10000 de dano, el totem salva. */
    public static final ResourceKey<DamageType> EXECUTION = key("execution");
    /** Escombros del tornado / viento con metralla. */
    public static final ResourceKey<DamageType> DEBRIS = key("debris");
    /** Ceniza asfixiante post-erupcion (DoT suave). */
    public static final ResourceKey<DamageType> ASH = key("ash");

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Cataclysm.MODID, name));
    }

    /** DamageSource sin atacante ni posicion: sin knockback direccional vanilla. */
    public static DamageSource source(Level level, ResourceKey<DamageType> key) {
        return new DamageSource(level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(key));
    }

    private ModDamageTypes() {
    }
}
