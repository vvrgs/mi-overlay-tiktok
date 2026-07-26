package com.vvrgs.irontempest.registry;

import com.vvrgs.irontempest.IronTempest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTER =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, IronTempest.MODID);

    public static final RegistryObject<SoundEvent> EXPLOSION_NEAR = sound("explosion_near");
    public static final RegistryObject<SoundEvent> EXPLOSION_FAR = sound("explosion_far");
    public static final RegistryObject<SoundEvent> SHELL_WHISTLE = sound("shell_whistle");
    public static final RegistryObject<SoundEvent> CANNON_FIRE = sound("cannon_fire");
    public static final RegistryObject<SoundEvent> TANK_ENGINE = sound("tank_engine");
    public static final RegistryObject<SoundEvent> TANK_LANDING = sound("tank_landing");
    public static final RegistryObject<SoundEvent> MISSILE_LAUNCH = sound("missile_launch");
    public static final RegistryObject<SoundEvent> MISSILE_LOOP = sound("missile_loop");
    public static final RegistryObject<SoundEvent> MLRS_LAUNCH = sound("mlrs_launch");
    public static final RegistryObject<SoundEvent> LASER_CHARGE = sound("laser_charge");
    public static final RegistryObject<SoundEvent> LASER_BEAM = sound("laser_beam");
    public static final RegistryObject<SoundEvent> WARP_IN = sound("warp_in");
    public static final RegistryObject<SoundEvent> WARP_OUT = sound("warp_out");
    public static final RegistryObject<SoundEvent> KLAXON = sound("klaxon");
    public static final RegistryObject<SoundEvent> ULTRA_SIREN = sound("ultra_siren");
    public static final RegistryObject<SoundEvent> DEBRIS_CLANK = sound("debris_clank");
    public static final RegistryObject<SoundEvent> TURRET_SERVO = sound("turret_servo");
    public static final RegistryObject<SoundEvent> TANK_TRACKS = sound("tank_tracks");
    public static final RegistryObject<SoundEvent> SHELL_CASING = sound("shell_casing");
    public static final RegistryObject<SoundEvent> CRATER_SIZZLE = sound("crater_sizzle");

    private static RegistryObject<SoundEvent> sound(String name) {
        return REGISTER.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(IronTempest.MODID, name)));
    }

    private ModSounds() {}
}
