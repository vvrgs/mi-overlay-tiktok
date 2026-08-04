package com.vvrgs.cataclysm.registry;

import com.vvrgs.cataclysm.Cataclysm;
import com.vvrgs.cataclysm.fx.FxEvent;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * Particulas 100% propias del motor de FX. TODAS con overrideLimiter=true:
 * con false el motor vanilla las cullea a mas de 32 bloques y los efectos
 * lejanos (frente de tsunami, columna del volcan) desaparecen.
 */
public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Cataclysm.MODID);

    /** Ceniza volcanica: flota, deriva lateral, vida larga. */
    public static final RegistryObject<SimpleParticleType> ASH = simple("ash");
    /** Brasa incandescente: sube a contraluz, drag alto, parpadea al morir. */
    public static final RegistryObject<SimpleParticleType> EMBER = simple("ember");
    /** Polvo balistico: sale despedido con gravedad real (fisuras, crateres). */
    public static final RegistryObject<SimpleParticleType> DUST = simple("dust");
    /** Plasma de bolido: estela brillante aditiva con spin. */
    public static final RegistryObject<SimpleParticleType> PLASMA = simple("plasma");
    /** Humo denso: columnas que oscurecen, crece al subir. */
    public static final RegistryObject<SimpleParticleType> SMOKE = simple("smoke");
    /** Gota de lluvia de la celula de tormenta / bandas del huracan. */
    public static final RegistryObject<SimpleParticleType> RAINDROP = simple("raindrop");
    /** Espuma y bruma de la cresta del tsunami. */
    public static final RegistryObject<SimpleParticleType> SPRAY = simple("spray");
    /** Mota electrica de ionizacion pre-rayo: asciende y vibra. */
    public static final RegistryObject<SimpleParticleType> SPARK = simple("spark");
    /** Mota dorada de telegraph: converge hacia el punto de impacto. */
    public static final RegistryObject<SimpleParticleType> TELEGRAPH = simple("telegraph");
    /** Globo de lava de las fuentes de erupcion: balistico, incandescente. */
    public static final RegistryObject<SimpleParticleType> LAVA_GLOB = simple("lava_glob");
    /** Escombro solido: trozo de bloque girando (tornado, eyecta). */
    public static final RegistryObject<SimpleParticleType> DEBRIS = simple("debris");
    /** Destello de pop de totem: burst dorado-verde propio. */
    public static final RegistryObject<SimpleParticleType> POP_FLASH = simple("pop_flash");

    /**
     * BONUS de consola: un ParticleType por evento de FX. Lanzar
     * "/particle cataclysm:fx_<evento> x y z" dispara en el cliente la
     * receta nativa completa + su effek del manifest — regalos de TikTok
     * directos sin pasar por los comandos del mod.
     */
    public static final Map<FxEvent, RegistryObject<SimpleParticleType>> FX_TRIGGERS =
            new EnumMap<>(FxEvent.class);

    static {
        for (FxEvent event : FxEvent.VALUES) {
            FX_TRIGGERS.put(event, simple("fx_" + event.manifestKey()));
        }
    }

    private static RegistryObject<SimpleParticleType> simple(String name) {
        // true == overrideLimiter: nunca cullear por distancia (bug real aprendido)
        return REGISTER.register(name, () -> new SimpleParticleType(true));
    }

    private ModParticles() {
    }
}
