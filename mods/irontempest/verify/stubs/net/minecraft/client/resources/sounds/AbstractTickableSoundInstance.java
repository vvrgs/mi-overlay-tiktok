package net.minecraft.client.resources.sounds;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public abstract class AbstractTickableSoundInstance extends AbstractSoundInstance implements TickableSoundInstance {
    private boolean stopped;

    protected AbstractTickableSoundInstance(SoundEvent soundEvent, SoundSource source, RandomSource random) {
        super(soundEvent, source, random);
    }

    @Override
    public boolean isStopped() { return this.stopped; }

    protected final void stop() { this.stopped = true; }
}
