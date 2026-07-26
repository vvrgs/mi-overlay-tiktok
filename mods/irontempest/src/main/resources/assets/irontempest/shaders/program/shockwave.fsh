#version 150

uniform sampler2D DiffuseSampler;

uniform vec2 Center;
uniform float Radius;
uniform float Strength;
uniform float Aspect;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 d = texCoord - Center;
    d.x *= Aspect;
    float dist = length(d);

    // Frente de onda gaussiano alrededor del radio actual.
    float wave = exp(-pow((dist - Radius) * 14.0, 2.0));

    vec2 dir = dist > 0.0001 ? d / dist : vec2(0.0);
    vec2 offset = dir * wave * Strength * 0.030;
    offset.x /= Aspect;

    vec3 color = texture(DiffuseSampler, clamp(texCoord - offset, 0.001, 0.999)).rgb;
    color += wave * Strength * 0.10; // brillo sutil del frente

    fragColor = vec4(color, 1.0);
}
