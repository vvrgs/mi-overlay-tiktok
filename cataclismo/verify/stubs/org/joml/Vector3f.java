package org.joml;

public class Vector3f {
    public float x;
    public float y;
    public float z;
    public Vector3f() {}
    public Vector3f(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
    public Vector3f(Vector3f other) { this(other.x, other.y, other.z); }
    public Vector3f normalize() { return this; }
    public Vector3f cross(Vector3f other) { return this; }
    public Vector3f mul(float scalar) { return this; }
    public Vector3f lerp(Vector3f other, float t) { return this; }
}
