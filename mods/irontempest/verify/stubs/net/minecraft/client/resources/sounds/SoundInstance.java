package net.minecraft.client.resources.sounds;

import net.minecraft.util.RandomSource;

public interface SoundInstance {
    static RandomSource createUnseededRandom() { throw new UnsupportedOperationException(); }

    default boolean canStartSilent() { return false; }
}
