package com.vvrgs.irontempest.client.sound;

import com.vvrgs.irontempest.entity.WarshipEntity;
import com.vvrgs.irontempest.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * Zumbido del haz orbital. Se crea al aparecer la nave y se auto-gestiona:
 * muda (volume 0) hasta la fase BEAM, fade-in de 0.1/tick hasta 1.8, sigue
 * sonando en OVERLOAD y muere al pasar a WARP_OUT o al removerse la nave.
 */
public class BeamHumSound extends AbstractTickableSoundInstance {

    private static final float MAX_VOLUME = 1.8F;

    private final WarshipEntity ship;

    public BeamHumSound(WarshipEntity ship) {
        super(ModSounds.LASER_BEAM.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.ship = ship;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.x = ship.getX();
        this.y = ship.getY();
        this.z = ship.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true; // imprescindible: nace con volume 0 y el SoundEngine lo descartaría
    }

    @Override
    public void tick() {
        if (this.ship.isRemoved() || this.ship.getPhase() > WarshipEntity.PHASE_OVERLOAD) {
            this.stop();
            return;
        }
        this.x = this.ship.getX();
        this.y = this.ship.getY();
        this.z = this.ship.getZ();
        byte phase = this.ship.getPhase();
        if (phase == WarshipEntity.PHASE_BEAM || phase == WarshipEntity.PHASE_OVERLOAD) {
            this.volume = Math.min(MAX_VOLUME, this.volume + 0.1F);
        } else {
            this.volume = 0.0F;
        }
    }
}
