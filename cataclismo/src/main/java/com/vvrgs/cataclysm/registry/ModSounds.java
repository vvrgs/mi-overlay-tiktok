package com.vvrgs.cataclysm.registry;

import com.vvrgs.cataclysm.Cataclysm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sonidos sintetizados propios (tools/gen_sounds.py, sin copyright).
 * Radio audible = 16 x volumen: los avisos lejanos se reproducen con
 * volumen 3.0+; los globales van por playNotifySound a cada jugador.
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> REGISTER =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Cataclysm.MODID);

    /** Sirena de aviso global (tier U). */
    public static final RegistryObject<SoundEvent> SIREN = sound("siren");
    /** Klaxon de ejecucion. */
    public static final RegistryObject<SoundEvent> KLAXON = sound("klaxon");
    /** Retumbo subterraneo del terremoto (loop sin costura). */
    public static final RegistryObject<SoundEvent> RUMBLE = sound("rumble");
    /** Crujido de roca al abrirse la fisura. */
    public static final RegistryObject<SoundEvent> ROCK_CRACK = sound("rock_crack");
    /** Silbido de bolido cayendo. */
    public static final RegistryObject<SoundEvent> METEOR_WHISTLE = sound("meteor_whistle");
    /** Explosion de impacto (meteorito, bomba volcanica). */
    public static final RegistryObject<SoundEvent> IMPACT_BLAST = sound("impact_blast");
    /** Trueno propio, seco y cercano. */
    public static final RegistryObject<SoundEvent> THUNDER = sound("thunder");
    /** Carga de ionizacion pre-rayo (zumbido ascendente). */
    public static final RegistryObject<SoundEvent> IONIZE = sound("ionize");
    /** Tren del tornado (loop sin costura). */
    public static final RegistryObject<SoundEvent> TORNADO_LOOP = sound("tornado_loop");
    /** Rafaga de viento del huracan. */
    public static final RegistryObject<SoundEvent> WIND_GUST = sound("wind_gust");
    /** Borboteo de lava (loop sin costura). */
    public static final RegistryObject<SoundEvent> LAVA_LOOP = sound("lava_loop");
    /** Erupcion volcanica (boom grave + rugido). */
    public static final RegistryObject<SoundEvent> ERUPTION = sound("eruption");
    /** Rugido del muro de agua acercandose. */
    public static final RegistryObject<SoundEvent> TSUNAMI_ROAR = sound("tsunami_roar");
    /** Ping del telegraph (retIcula 1-2 s antes del impacto). */
    public static final RegistryObject<SoundEvent> TELEGRAPH_PING = sound("telegraph_ping");
    /** Pop de totem propio (crack + campanilla). */
    public static final RegistryObject<SoundEvent> TOTEM_POP = sound("totem_pop");
    /** Rasgado de entrada atmosferica del impacto planetario. */
    public static final RegistryObject<SoundEvent> ATMOSPHERE_TEAR = sound("atmosphere_tear");

    private static RegistryObject<SoundEvent> sound(String name) {
        return REGISTER.register(name, () ->
                SoundEvent.createVariableRangeEvent(new ResourceLocation(Cataclysm.MODID, name)));
    }

    private ModSounds() {
    }
}
