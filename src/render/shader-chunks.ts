/**
 * Fragmentos GLSL compartidos.
 *
 * Regla del pipeline: **todos los shaders de escena trabajan en espacio lineal y
 * NO codifican a sRGB**. La conversión final ocurre una sola vez, en el paso de
 * composición de `post.ts`. Codificar dos veces lava la imagen entera.
 *
 * three.js ya entrega los colores hexadecimales convertidos a lineal (con
 * ColorManagement activo), así que los uniformes de color llegan correctos. Lo
 * único que hay que convertir a mano son las constantes escritas dentro del
 * shader, y para eso está `srgbToLinear`.
 */

/** Convierte una constante escrita "a ojo" en sRGB al espacio lineal del render. */
export const SRGB_TO_LINEAR = /* glsl */ `
  vec3 srgbToLinear(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
  }
`;

/** Ruido de valor 2D: mismo algoritmo en terreno, agua y cielo para que casen. */
export const NOISE_2D = /* glsl */ `
  float hash21(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
  }

  float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), u.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), u.x), u.y);
  }

  float fbm2(vec2 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 6; i++) {
      if (i >= octaves) break;
      sum += noise2(p) * amp;
      p *= 2.02;
      amp *= 0.5;
    }
    return sum;
  }
`;

/** Niebla exponencial al cuadrado, idéntica en terreno, agua y unidades. */
export const FOG_MIX = /* glsl */ `
  vec3 applyFog(vec3 color, vec3 fogColor, float density, float depth) {
    float factor = 1.0 - exp(-density * density * depth * depth);
    return mix(color, fogColor, clamp(factor, 0.0, 1.0));
  }
`;
