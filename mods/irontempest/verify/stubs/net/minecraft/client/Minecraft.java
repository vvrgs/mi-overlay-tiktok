package net.minecraft.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

public class Minecraft {
    public static final boolean ON_OSX = false;

    @Nullable
    public ClientLevel level;
    public final GameRenderer gameRenderer = null;

    public static Minecraft getInstance() { throw new UnsupportedOperationException(); }
    public RenderTarget getMainRenderTarget() { throw new UnsupportedOperationException(); }
    public ResourceManager getResourceManager() { throw new UnsupportedOperationException(); }
    public SoundManager getSoundManager() { throw new UnsupportedOperationException(); }
}
