/**
 * Fragmentos GLSL compartidos.
 *
 * three.js (con ColorManagement activo, que es el valor por defecto) convierte
 * cualquier color hexadecimal de sRGB a espacio lineal al crear el `Color`. Los
 * materiales estándar deshacen esa conversión al escribir el píxel, pero un
 * ShaderMaterial propio no: si escribes el color lineal tal cual, toda la escena
 * sale muy oscura. Estos helpers hacen la codificación final a sRGB para que los
 * colores que pones en la config sean exactamente los que se ven.
 */

export const SRGB_ENCODE = /* glsl */ `
  vec3 linearToSRGB(vec3 c) {
    vec3 clamped = clamp(c, 0.0, 1.0);
    return mix(clamped * 12.92, 1.055 * pow(clamped, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), clamped));
  }
`;

/** Niebla exponencial al cuadrado, idéntica en terreno, agua y unidades. */
export const FOG_MIX = /* glsl */ `
  vec3 applyFog(vec3 color, vec3 fogColor, float density, float depth) {
    float factor = 1.0 - exp(-density * density * depth * depth);
    return mix(color, fogColor, clamp(factor, 0.0, 1.0));
  }
`;
