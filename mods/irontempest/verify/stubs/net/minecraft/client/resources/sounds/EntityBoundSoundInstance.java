package net.minecraft.client.resources.sounds;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

public class EntityBoundSoundInstance extends AbstractTickableSoundInstance {
    public EntityBoundSoundInstance(SoundEvent soundEvent, SoundSource source, float volume, float pitch,
                                    Entity entity, long seed) {
        super(soundEvent, source, SoundInstance.createUnseededRandom());
    }

    @Override
    public void tick() {}
}
