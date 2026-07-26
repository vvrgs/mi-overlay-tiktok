package net.minecraft.server.level;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ServerLevel extends Level {
    @Nullable
    public Entity getEntity(UUID uuid) { return null; }

    @Override
    public MinecraftServer getServer() { throw new UnsupportedOperationException(); }
}
