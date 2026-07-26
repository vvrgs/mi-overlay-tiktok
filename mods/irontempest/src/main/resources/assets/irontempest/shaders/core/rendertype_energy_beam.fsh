#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    // GameTime vanilla cicla en [0,1) cada 24000 ticks: se multiplica en
    // grande para obtener velocidades de scroll/parpadeo utilizables.
    vec4 tex = texture(Sampler0, texCoord0);
    // Segundo muestreo desplazado por GameTime: interferencia de dos capas.
    vec4 tex2 = texture(Sampler0, vec2(texCoord0.x, texCoord0.y * 1.7 + GameTime * 1200.0));

    vec4 color = (tex * 0.75 + tex2 * 0.45) * vertexColor;
    // Nucleo brillante con parpadeo de energia sutil a lo largo del haz.
    color.rgb *= 1.4 + sin(GameTime * 3000.0 + texCoord0.y * 40.0) * 0.15;

    float alpha = tex.a * vertexColor.a;
    if (alpha < 0.01) {
        discard;
    }
    fragColor = vec4(color.rgb, alpha) * ColorModulator;
}
