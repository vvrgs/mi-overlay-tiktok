package com.mojang.blaze3d.vertex;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class PoseStack {
    public void pushPose() {}
    public void popPose() {}
    public void translate(double x, double y, double z) {}
    public void translate(float x, float y, float z) {}
    public void scale(float x, float y, float z) {}
    public void mulPose(Quaternionf quaternion) {}
    public Pose last() { throw new UnsupportedOperationException(); }

    public static final class Pose {
        public Matrix4f pose() { throw new UnsupportedOperationException(); }
    }
}
