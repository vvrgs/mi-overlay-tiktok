package net.minecraft.world.entity.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public abstract class Player extends LivingEntity {
    public boolean isCreative() { return false; }
    public Inventory getInventory() { throw new UnsupportedOperationException(); }

    public void playNotifySound(net.minecraft.sounds.SoundEvent sound, net.minecraft.sounds.SoundSource source, float volume, float pitch) { throw new UnsupportedOperationException(); }

    protected Player(Level level) {
        super(null, level);
    }

    public GameProfile getGameProfile() { throw new UnsupportedOperationException(); }
}
