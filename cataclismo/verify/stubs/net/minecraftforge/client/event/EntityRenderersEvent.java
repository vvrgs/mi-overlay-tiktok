package net.minecraftforge.client.event;

public class EntityRenderersEvent extends net.minecraftforge.eventbus.api.Event {
    public static class RegisterRenderers extends EntityRenderersEvent {
        public <T extends net.minecraft.world.entity.Entity> void registerEntityRenderer(net.minecraft.world.entity.EntityType<? extends T> type, net.minecraft.client.renderer.entity.EntityRendererProvider<T> provider) {}
    }
    public static class RegisterLayerDefinitions extends EntityRenderersEvent {
        public void registerLayerDefinition(net.minecraft.client.model.geom.ModelLayerLocation layer, java.util.function.Supplier<net.minecraft.client.model.geom.builders.LayerDefinition> supplier) {}
    }
}
