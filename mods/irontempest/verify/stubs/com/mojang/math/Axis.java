package com.mojang.math;

import org.joml.Quaternionf;

@FunctionalInterface
public interface Axis {
    Axis XN = radians -> new Quaternionf();
    Axis XP = radians -> new Quaternionf();
    Axis YN = radians -> new Quaternionf();
    Axis YP = radians -> new Quaternionf();
    Axis ZN = radians -> new Quaternionf();
    Axis ZP = radians -> new Quaternionf();

    Quaternionf rotation(float radians);

    default Quaternionf rotationDegrees(float degrees) {
        return this.rotation(degrees * ((float) Math.PI / 180.0F));
    }
}
