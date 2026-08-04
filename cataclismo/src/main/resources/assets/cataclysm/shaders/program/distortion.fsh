#version 150

// Cataclismo: distorsion radial en ondas (ondas expansivas, erupciones)

uniform sampler2D DiffuseSampler;
uniform float Time;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 centered = texCoord - vec2(0.5);
    float dist = length(centered);
    // ondas concentricas que viajan hacia fuera
    float wave = sin(dist * 42.0 - Time * 28.0) * 0.0045;
    // mas fuerte lejos del centro, nula en el borde exacto
    float envelope = smoothstep(0.05, 0.35, dist) * (1.0 - smoothstep(0.6, 0.9, dist));
    vec2 offset = normalize(centered + vec2(1e-6)) * wave * envelope;
    fragColor = texture(DiffuseSampler, texCoord + offset);
}
