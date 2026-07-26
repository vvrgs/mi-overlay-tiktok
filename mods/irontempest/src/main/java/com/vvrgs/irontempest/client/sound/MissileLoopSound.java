package com.vvrgs.irontempest.client.sound;

import com.vvrgs.irontempest.entity.CruiseMissileEntity;
import com.vvrgs.irontempest.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Zumbido del misil de crucero: sigue al misil y agudiza en fase terminal. */
public class MissileLoopSound extends AbstractTickableSoundInstance {

    private final CruiseMissileEntity missile;

    public MissileLoopSound(CruiseMissileEntity missile) {
        super(ModSounds.MISSILE_LOOP.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.missile = missile;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0F;
        this.pitch = 1.0F;
        this.x = missile.getX();
        this.y = missile.getY();
        this.z = missile.getZ();
    }

    @Override
    public void tick() {
        if (this.missile.isRemoved()) {
            this.stop();
            return;
        }
        this.x = this.missile.getX();
        this.y = this.missile.getY();
        this.z = this.missile.getZ();
        this.pitch = this.missile.getPhase() == CruiseMissileEntity.PHASE_TERMINAL ? 1.15F : 1.0F;
    }
}
