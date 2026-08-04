package com.mojang.blaze3d.vertex;

public class PoseStack {
    public static final class Pose {
        public org.joml.Matrix4f pose() { throw new UnsupportedOperationException(); }
    }
    public void pushPose() {}
    public void popPose() {}
    public void translate(double x, double y, double z) {}
    public void scale(float x, float y, float z) {}
    public void mulPose(org.joml.Quaternionf rotation) {}
    public Pose last() { throw new UnsupportedOperationException(); }
}
