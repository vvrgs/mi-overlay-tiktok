package net.minecraftforge.client.event;

public class RenderLevelStageEvent extends net.minecraftforge.eventbus.api.Event {
    public static class Stage {
        public static final Stage AFTER_SKY = new Stage();
        public static final Stage AFTER_PARTICLES = new Stage();
        public static final Stage AFTER_LEVEL = new Stage();
    }
    public Stage getStage() { throw new UnsupportedOperationException(); }
    public com.mojang.blaze3d.vertex.PoseStack getPoseStack() { throw new UnsupportedOperationException(); }
    public float getPartialTick() { throw new UnsupportedOperationException(); }
}
