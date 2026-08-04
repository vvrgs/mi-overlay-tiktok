#version 150

// Cataclismo: aberracion cromatica radial (el flash del impacto planetario)

uniform sampler2D DiffuseSampler;
uniform float Time;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 dir = texCoord - vec2(0.5);
    // separacion RGB que late y decae con la distancia al centro
    float amount = 0.0065 * (0.7 + 0.3 * sin(Time * 22.0)) * length(dir) * 2.0;
    float r = texture(DiffuseSampler, texCoord + dir * amount).r;
    float g = texture(DiffuseSampler, texCoord).g;
    float b = texture(DiffuseSampler, texCoord - dir * amount).b;
    float a = texture(DiffuseSampler, texCoord).a;
    fragColor = vec4(r, g, b, a);
}
