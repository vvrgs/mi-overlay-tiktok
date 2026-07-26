package net.minecraft.client.model.geom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class ModelPart {
    public float x;
    public float y;
    public float z;
    public float xRot;
    public float yRot;
    public float zRot;
    public boolean visible = true;

    public ModelPart getChild(String name) { throw new UnsupportedOperationException(); }
    public void render(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay) {}
    public void render(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                       float red, float green, float blue, float alpha) {}
}
