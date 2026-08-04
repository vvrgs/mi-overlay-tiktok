package net.minecraft.client.renderer.entity;

public interface EntityRendererProvider<T extends net.minecraft.world.entity.Entity> {
    EntityRenderer<T> create(Context context);
    class Context {
        public net.minecraft.client.model.geom.ModelPart bakeLayer(net.minecraft.client.model.geom.ModelLayerLocation layer) { throw new UnsupportedOperationException(); }
    }
}
