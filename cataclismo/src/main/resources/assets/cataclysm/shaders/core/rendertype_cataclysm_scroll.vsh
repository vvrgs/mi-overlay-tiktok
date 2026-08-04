#version 150

// Cataclismo: lamina con scroll de UV por GameTime (muro de agua del
// tsunami). La textura muestreada debe ser periodica en Y en TODO el lienzo:
// el fract() del fragment tesela la textura completa.

#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform float GameTime;

out float vertexDistance;
out vec4 vertexColor;
out vec4 overlayColor;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexDistance = fog_distance(ModelViewMat, Position, FogShape);
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    // GameTime = ticks/24000: x1200 => ~1 ciclo por segundo de juego
    texCoord0 = UV0 + vec2(0.0, -GameTime * 1200.0 * 0.045);
}
