package net.minecraft.client.renderer;

public abstract class RenderStateShard {
    public static class ShaderStateShard extends RenderStateShard {
        public ShaderStateShard(java.util.function.Supplier<ShaderInstance> shader) { super("", () -> {}, () -> {}); }
    }
    public static class TextureStateShard extends RenderStateShard {
        public TextureStateShard(net.minecraft.resources.ResourceLocation texture, boolean blur, boolean mipmap) { super("", () -> {}, () -> {}); }
    }
    public static class TransparencyStateShard extends RenderStateShard {
        public TransparencyStateShard(String name, Runnable setup, Runnable clear) { super(name, setup, clear); }
    }
    public static class CullStateShard extends RenderStateShard {
        public CullStateShard(boolean cull) { super("", () -> {}, () -> {}); }
    }
    public static class LightmapStateShard extends RenderStateShard {
        public LightmapStateShard(boolean lightmap) { super("", () -> {}, () -> {}); }
    }
    public static class OverlayStateShard extends RenderStateShard {
        public OverlayStateShard(boolean overlay) { super("", () -> {}, () -> {}); }
    }
    protected static final TransparencyStateShard TRANSLUCENT_TRANSPARENCY = new TransparencyStateShard("", () -> {}, () -> {});
    protected static final CullStateShard NO_CULL = new CullStateShard(false);
    protected static final LightmapStateShard LIGHTMAP = new LightmapStateShard(true);
    protected static final OverlayStateShard OVERLAY = new OverlayStateShard(true);
    protected final String name;
    public RenderStateShard(String name, Runnable setupState, Runnable clearState) { this.name = name; }
}
