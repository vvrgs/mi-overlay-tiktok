package net.minecraft.client.renderer;

public class PostChain implements AutoCloseable {
    public PostChain(net.minecraft.client.renderer.texture.TextureManager textureManager, net.minecraft.server.packs.resources.ResourceManager resourceManager, com.mojang.blaze3d.pipeline.RenderTarget target, net.minecraft.resources.ResourceLocation location) throws java.io.IOException {}
    public void resize(int width, int height) {}
    public void process(float partialTick) {}
    @Override public void close() {}
}
