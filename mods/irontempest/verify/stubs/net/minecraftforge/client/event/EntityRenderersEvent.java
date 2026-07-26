package net.minecraftforge.client.event;

import java.util.function.Supplier;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.eventbus.api.Event;

public abstract class EntityRenderersEvent extends Event {
    public static class RegisterRenderers extends EntityRenderersEvent {
        public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type,
                                                              EntityRendererProvider<T> provider) {}
    }

    public static class RegisterLayerDefinitions extends EntityRenderersEvent {
        public void registerLayerDefinition(ModelLayerLocation location, Supplier<LayerDefinition> supplier) {}
    }
}
