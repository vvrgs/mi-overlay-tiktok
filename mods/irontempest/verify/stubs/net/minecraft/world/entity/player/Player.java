package net.minecraft.world.entity.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public abstract class Player extends LivingEntity {
    protected Player(Level level) {
        super(null, level);
    }

    public GameProfile getGameProfile() { throw new UnsupportedOperationException(); }
}
