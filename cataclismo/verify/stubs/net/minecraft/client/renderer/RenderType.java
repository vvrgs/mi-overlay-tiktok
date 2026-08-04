package net.minecraft.client.renderer;

public abstract class RenderType extends RenderStateShard {
    public static final class CompositeState {
        public static CompositeStateBuilder builder() { throw new UnsupportedOperationException(); }
    }
    public static final class CompositeStateBuilder {
        public CompositeStateBuilder setShaderState(ShaderStateShard shard) { return this; }
        public CompositeStateBuilder setTextureState(TextureStateShard shard) { return this; }
        public CompositeStateBuilder setTransparencyState(TransparencyStateShard shard) { return this; }
        public CompositeStateBuilder setCullState(CullStateShard shard) { return this; }
        public CompositeStateBuilder setLightmapState(LightmapStateShard shard) { return this; }
        public CompositeStateBuilder setOverlayState(OverlayStateShard shard) { return this; }
        public CompositeState createCompositeState(boolean outline) { throw new UnsupportedOperationException(); }
    }
    public RenderType(String name, com.mojang.blaze3d.vertex.VertexFormat format, com.mojang.blaze3d.vertex.VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) { super(name, setupState, clearState); }
    public static RenderType create(String name, com.mojang.blaze3d.vertex.VertexFormat format, com.mojang.blaze3d.vertex.VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, CompositeState state) { throw new UnsupportedOperationException(); }
    public static RenderType entityCutoutNoCull(net.minecraft.resources.ResourceLocation texture) { throw new UnsupportedOperationException(); }
    public static RenderType entityTranslucent(net.minecraft.resources.ResourceLocation texture) { throw new UnsupportedOperationException(); }
    public static RenderType eyes(net.minecraft.resources.ResourceLocation texture) { throw new UnsupportedOperationException(); }
    public static RenderType lightning() { throw new UnsupportedOperationException(); }
}
