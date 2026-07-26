package net.minecraft.client.renderer;

import com.mojang.blaze3d.shaders.AbstractUniform;
import java.io.IOException;
import java.util.function.IntSupplier;
import net.minecraft.server.packs.resources.ResourceManager;

public class EffectInstance {
    public EffectInstance(ResourceManager resourceManager, String name) throws IOException {}

    public void setSampler(String name, IntSupplier sampler) {}
    public AbstractUniform safeGetUniform(String name) { throw new UnsupportedOperationException(); }
    public void apply() {}
    public void clear() {}
    public void close() {}
}
