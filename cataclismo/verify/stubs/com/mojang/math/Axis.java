package com.mojang.math;

public interface Axis {
    Axis XP = of();
    Axis YP = of();
    Axis ZP = of();
    static Axis of() { return null; }
    org.joml.Quaternionf rotationDegrees(float degrees);
}
