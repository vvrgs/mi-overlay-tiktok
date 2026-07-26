package net.minecraft.client.resources.sounds;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public abstract class AbstractSoundInstance implements SoundInstance {
    protected float volume = 1.0F;
    protected float pitch = 1.0F;
    protected double x;
    protected double y;
    protected double z;
    protected boolean looping;
    protected int delay;

    protected AbstractSoundInstance(SoundEvent soundEvent, SoundSource source, RandomSource random) {}
}
