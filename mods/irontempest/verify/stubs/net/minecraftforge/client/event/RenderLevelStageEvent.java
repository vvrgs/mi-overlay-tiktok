package net.minecraftforge.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraftforge.eventbus.api.Event;
import org.joml.Matrix4f;

public class RenderLevelStageEvent extends Event {
    public Stage getStage() { throw new UnsupportedOperationException(); }
    public float getPartialTick() { return 0.0F; }
    public Camera getCamera() { throw new UnsupportedOperationException(); }
    public PoseStack getPoseStack() { throw new UnsupportedOperationException(); }
    public Matrix4f getProjectionMatrix() { throw new UnsupportedOperationException(); }

    public static class Stage {
        public static final Stage AFTER_SKY = new Stage();
        public static final Stage AFTER_SOLID_BLOCKS = new Stage();
        public static final Stage AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS = new Stage();
        public static final Stage AFTER_CUTOUT_BLOCKS = new Stage();
        public static final Stage AFTER_ENTITIES = new Stage();
        public static final Stage AFTER_BLOCK_ENTITIES = new Stage();
        public static final Stage AFTER_TRANSLUCENT_BLOCKS = new Stage();
        public static final Stage AFTER_TRIPWIRE_BLOCKS = new Stage();
        public static final Stage AFTER_PARTICLES = new Stage();
        public static final Stage AFTER_WEATHER = new Stage();
        public static final Stage AFTER_LEVEL = new Stage();
    }
}
