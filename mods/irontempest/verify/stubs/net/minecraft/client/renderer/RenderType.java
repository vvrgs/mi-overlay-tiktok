package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.ResourceLocation;

public abstract class RenderType extends RenderStateShard {
    protected RenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                         boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, setupState, clearState);
    }

    public static RenderType create(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                    boolean affectsCrumbling, boolean sortOnUpload, CompositeState state) {
        throw new UnsupportedOperationException();
    }

    public static RenderType entitySolid(ResourceLocation texture) { throw new UnsupportedOperationException(); }
    public static RenderType entityCutout(ResourceLocation texture) { throw new UnsupportedOperationException(); }
    public static RenderType entityCutoutNoCull(ResourceLocation texture) { throw new UnsupportedOperationException(); }
    public static RenderType entityTranslucent(ResourceLocation texture) { throw new UnsupportedOperationException(); }
    public static RenderType eyes(ResourceLocation texture) { throw new UnsupportedOperationException(); }
    public static RenderType lightning() { throw new UnsupportedOperationException(); }

    public static final class CompositeState {
        public static CompositeStateBuilder builder() { throw new UnsupportedOperationException(); }

        public static final class CompositeStateBuilder {
            public CompositeStateBuilder setShaderState(ShaderStateShard shard) { return this; }
            public CompositeStateBuilder setTextureState(EmptyTextureStateShard shard) { return this; }
            public CompositeStateBuilder setTransparencyState(TransparencyStateShard shard) { return this; }
            public CompositeStateBuilder setWriteMaskState(WriteMaskStateShard shard) { return this; }
            public CompositeStateBuilder setCullState(CullStateShard shard) { return this; }
            public CompositeState createCompositeState(boolean affectsOutline) { throw new UnsupportedOperationException(); }
        }
    }
}
