package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;

@FunctionalInterface
public interface EntityRendererProvider<T extends Entity> {
    EntityRenderer<T> create(Context context);

    public static class Context {
        public ModelPart bakeLayer(ModelLayerLocation location) { throw new UnsupportedOperationException(); }
        public EntityRenderDispatcher getEntityRenderDispatcher() { throw new UnsupportedOperationException(); }
    }
}
