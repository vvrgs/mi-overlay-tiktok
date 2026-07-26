package com.vvrgs.irontempest.client.sound;

import com.vvrgs.irontempest.entity.TankEntity;
import com.vvrgs.irontempest.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Ralentí del tanque: loop que sigue a la entidad y muere con el motor. */
public class TankEngineSound extends AbstractTickableSoundInstance {

    private final TankEntity tank;

    public TankEngineSound(TankEntity tank) {
        super(ModSounds.TANK_ENGINE.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.tank = tank;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.2F;
        this.x = tank.getX();
        this.y = tank.getY();
        this.z = tank.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true; // el motor puede arrancar apagado y encenderse después
    }

    @Override
    public void tick() {
        if (this.tank.isRemoved()) {
            this.stop();
            return;
        }
        // stop() es definitivo: con el motor apagado (caída del drop-pod) solo
        // se silencia, y arranca de verdad cuando la sesión enciende el motor.
        this.volume = this.tank.isEngineOn() ? 1.2F : 0.0F;
        this.x = this.tank.getX();
        this.y = this.tank.getY();
        this.z = this.tank.getZ();
    }
}
