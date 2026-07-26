package net.minecraft.client.renderer;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

public abstract class RenderStateShard {
    protected final String name;

    protected static final TransparencyStateShard NO_TRANSPARENCY = new TransparencyStateShard("no_transparency", () -> {}, () -> {});
    protected static final TransparencyStateShard ADDITIVE_TRANSPARENCY = new TransparencyStateShard("additive_transparency", () -> {}, () -> {});
    protected static final TransparencyStateShard LIGHTNING_TRANSPARENCY = new TransparencyStateShard("lightning_transparency", () -> {}, () -> {});
    protected static final TransparencyStateShard TRANSLUCENT_TRANSPARENCY = new TransparencyStateShard("translucent_transparency", () -> {}, () -> {});
    protected static final CullStateShard CULL = new CullStateShard(true);
    protected static final CullStateShard NO_CULL = new CullStateShard(false);
    protected static final WriteMaskStateShard COLOR_DEPTH_WRITE = new WriteMaskStateShard(true, true);
    protected static final WriteMaskStateShard COLOR_WRITE = new WriteMaskStateShard(true, false);
    protected static final WriteMaskStateShard DEPTH_WRITE = new WriteMaskStateShard(false, true);

    public RenderStateShard(String name, Runnable setupState, Runnable clearState) {
        this.name = name;
    }

    protected static class TransparencyStateShard extends RenderStateShard {
        public TransparencyStateShard(String name, Runnable setupState, Runnable clearState) {
            super(name, setupState, clearState);
        }
    }

    protected static class CullStateShard extends RenderStateShard {
        public CullStateShard(boolean cull) {
            super("cull", () -> {}, () -> {});
        }
    }

    protected static class WriteMaskStateShard extends RenderStateShard {
        public WriteMaskStateShard(boolean writeColor, boolean writeDepth) {
            super("write_mask_state", () -> {}, () -> {});
        }
    }

    protected static class ShaderStateShard extends RenderStateShard {
        public ShaderStateShard() {
            super("shader", () -> {}, () -> {});
        }

        public ShaderStateShard(Supplier<ShaderInstance> shader) {
            super("shader", () -> {}, () -> {});
        }
    }

    protected static class EmptyTextureStateShard extends RenderStateShard {
        public EmptyTextureStateShard(Runnable setupState, Runnable clearState) {
            super("texture", setupState, clearState);
        }

        public EmptyTextureStateShard() {
            super("texture", () -> {}, () -> {});
        }
    }

    protected static class TextureStateShard extends EmptyTextureStateShard {
        public TextureStateShard(ResourceLocation texture, boolean blur, boolean mipmap) {
        }
    }
}
