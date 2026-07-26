package com.vvrgs.irontempest.client.sound;

import com.vvrgs.irontempest.entity.TankEntity;
import com.vvrgs.irontempest.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** Orugas: loop que solo suena cuando el tanque se desplaza de verdad. */
public class TankTracksSound extends AbstractTickableSoundInstance {

    private final TankEntity tank;
    private float smoothed;

    public TankTracksSound(TankEntity tank) {
        super(ModSounds.TANK_TRACKS.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.tank = tank;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.x = tank.getX();
        this.y = tank.getY();
        this.z = tank.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (this.tank.isRemoved()) {
            stop();
            return;
        }
        this.x = this.tank.getX();
        this.y = this.tank.getY();
        this.z = this.tank.getZ();
        float speed = (float) this.tank.horizontalSpeed();
        float targetVol = Mth.clamp(speed / 0.055F, 0.0F, 1.0F) * 1.1F;
        this.smoothed += (targetVol - this.smoothed) * 0.2F;
        this.volume = this.smoothed < 0.02F ? 0.0F : this.smoothed;
    }
}
