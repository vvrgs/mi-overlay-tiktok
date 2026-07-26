/**
 * Post-procesado.
 *
 * La escena se dibuja en un render target de coma flotante y en espacio LINEAL,
 * sin recortar a 1.0. Eso permite que explosiones, meteoros y el sol emitan
 * valores por encima del blanco, que es de donde sale un bloom creíble: no un
 * desenfoque genérico sobre toda la imagen, sino luz que rebosa solo de lo que
 * de verdad brilla.
 *
 * Cadena: escena → bright-pass (¼) → blur separable (¼) → blur ancho (⅛)
 *         → composición + tonemapping ACES + viñeta + grading + sRGB.
 *
 * Aquí es donde ocurre la conversión final a sRGB. Por eso los shaders de
 * terreno, unidades, agua y cielo trabajan en lineal y NO codifican ellos:
 * hacerlo dos veces lavaría la imagen entera.
 */

import * as THREE from 'three';

const FULLSCREEN_VERTEX = /* glsl */ `
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = vec4(position.xy, 0.0, 1.0);
  }
`;

/** Extrae solo lo que supera el umbral, con una rodilla suave para evitar parpadeos. */
const BRIGHT_FRAGMENT = /* glsl */ `
  precision highp float;
  uniform sampler2D uScene;
  uniform float uThreshold;
  uniform float uKnee;
  varying vec2 vUv;

  void main() {
    vec3 color = texture2D(uScene, vUv).rgb;
    float brightness = max(color.r, max(color.g, color.b));
    // Curva suave alrededor del umbral: un corte duro haría que los píxeles
    // entren y salgan del bloom parpadeando al moverse la cámara.
    float soft = clamp(brightness - uThreshold + uKnee, 0.0, 2.0 * uKnee);
    soft = soft * soft / (4.0 * uKnee + 0.0001);
    float contribution = max(soft, brightness - uThreshold) / max(brightness, 0.0001);
    gl_FragColor = vec4(color * contribution, 1.0);
  }
`;

/** Blur gaussiano separable de 9 tomas (se ejecuta dos veces: horizontal y vertical). */
const BLUR_FRAGMENT = /* glsl */ `
  precision highp float;
  uniform sampler2D uSource;
  uniform vec2 uDirection;
  varying vec2 vUv;

  void main() {
    // Pesos de un gaussiano aprovechando el filtrado bilineal (5 muestras = 9 tomas).
    const float o1 = 1.3846153846;
    const float o2 = 3.2307692308;
    vec3 result = texture2D(uSource, vUv).rgb * 0.2270270270;
    result += texture2D(uSource, vUv + uDirection * o1).rgb * 0.3162162162;
    result += texture2D(uSource, vUv - uDirection * o1).rgb * 0.3162162162;
    result += texture2D(uSource, vUv + uDirection * o2).rgb * 0.0702702703;
    result += texture2D(uSource, vUv - uDirection * o2).rgb * 0.0702702703;
    gl_FragColor = vec4(result, 1.0);
  }
`;

const COMPOSITE_FRAGMENT = /* glsl */ `
  precision highp float;
  uniform sampler2D uScene;
  uniform sampler2D uBloomNear;
  uniform sampler2D uBloomWide;
  uniform vec2 uTexel;
  uniform float uBloomIntensity;
  uniform float uVignette;
  uniform float uSaturation;
  uniform float uContrast;
  uniform float uExposure;
  uniform vec3 uTint;
  uniform float uFlash;
  uniform vec3 uFlashColor;
  uniform float uSharpen;
  varying vec2 vUv;

  // Aproximación ACES de Narkowicz: mantiene el color en las altas luces en vez
  // de quemarlas a blanco puro, que es justo lo que pasa con explosiones.
  vec3 acesToneMap(vec3 x) {
    const float a = 2.51;
    const float b = 0.03;
    const float c = 2.43;
    const float d = 0.59;
    const float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
  }

  vec3 linearToSRGB(vec3 c) {
    vec3 v = clamp(c, 0.0, 1.0);
    return mix(v * 12.92, 1.055 * pow(v, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), v));
  }

  void main() {
    vec3 color = texture2D(uScene, vUv).rgb;

    // Realce de contorno muy suave: compensa el suavizado del escalado en OBS.
    if (uSharpen > 0.0) {
      vec3 blur = texture2D(uScene, vUv + vec2(uTexel.x, 0.0)).rgb
                + texture2D(uScene, vUv - vec2(uTexel.x, 0.0)).rgb
                + texture2D(uScene, vUv + vec2(0.0, uTexel.y)).rgb
                + texture2D(uScene, vUv - vec2(0.0, uTexel.y)).rgb;
      color += (color - blur * 0.25) * uSharpen;
    }

    vec3 bloom = texture2D(uBloomNear, vUv).rgb + texture2D(uBloomWide, vUv).rgb * 0.7;
    color += bloom * uBloomIntensity;

    color *= uExposure;
    color = acesToneMap(color);

    // Saturación y contraste en espacio ya tonemapeado.
    float lum = dot(color, vec3(0.2126, 0.7152, 0.0722));
    color = mix(vec3(lum), color, uSaturation);
    color = (color - 0.5) * uContrast + 0.5;
    color *= uTint;

    // Destello de pantalla para las ultimates.
    color = mix(color, uFlashColor, clamp(uFlash, 0.0, 1.0));

    // Viñeta: concentra la mirada en el centro, donde está la batalla.
    vec2 centered = vUv - 0.5;
    float vig = 1.0 - dot(centered, centered) * uVignette;
    color *= clamp(vig, 0.0, 1.0);

    gl_FragColor = vec4(linearToSRGB(max(color, 0.0)), 1.0);
  }
`;

export interface PostOptions {
  enabled: boolean;
  bloomThreshold: number;
  bloomIntensity: number;
  vignette: number;
  saturation: number;
  contrast: number;
  exposure: number;
  sharpen: number;
  tint: [number, number, number];
}

export const DEFAULT_POST: PostOptions = {
  enabled: true,
  bloomThreshold: 1.15,
  bloomIntensity: 0.6,
  vignette: 0.55,
  saturation: 1.12,
  contrast: 1.06,
  exposure: 1.05,
  sharpen: 0.22,
  tint: [1, 1, 1],
};

export class PostProcessing {
  private sceneTarget: THREE.WebGLRenderTarget;
  private bloomNearA: THREE.WebGLRenderTarget;
  private bloomNearB: THREE.WebGLRenderTarget;
  private bloomWideA: THREE.WebGLRenderTarget;
  private bloomWideB: THREE.WebGLRenderTarget;

  private quad: THREE.Mesh;
  private quadScene = new THREE.Scene();
  private quadCamera = new THREE.OrthographicCamera(-1, 1, 1, -1, 0, 1);

  private brightMaterial: THREE.ShaderMaterial;
  private blurMaterial: THREE.ShaderMaterial;
  private compositeMaterial: THREE.ShaderMaterial;

  private width = 1;
  private height = 1;
  private flash = 0;
  private lastFlashTime = 0;
  private options: PostOptions;

  constructor(options: Partial<PostOptions> = {}) {
    this.options = { ...DEFAULT_POST, ...options };

    const makeTarget = (scale: number) =>
      new THREE.WebGLRenderTarget(1, 1, {
        type: THREE.HalfFloatType,
        format: THREE.RGBAFormat,
        minFilter: THREE.LinearFilter,
        magFilter: THREE.LinearFilter,
        depthBuffer: scale === 1,
        stencilBuffer: false,
        generateMipmaps: false,
      });

    this.sceneTarget = makeTarget(1);
    this.bloomNearA = makeTarget(0.25);
    this.bloomNearB = makeTarget(0.25);
    this.bloomWideA = makeTarget(0.125);
    this.bloomWideB = makeTarget(0.125);

    this.brightMaterial = new THREE.ShaderMaterial({
      vertexShader: FULLSCREEN_VERTEX,
      fragmentShader: BRIGHT_FRAGMENT,
      uniforms: {
        uScene: { value: null },
        uThreshold: { value: this.options.bloomThreshold },
        uKnee: { value: 0.45 },
      },
      depthTest: false,
      depthWrite: false,
    });

    this.blurMaterial = new THREE.ShaderMaterial({
      vertexShader: FULLSCREEN_VERTEX,
      fragmentShader: BLUR_FRAGMENT,
      uniforms: {
        uSource: { value: null },
        uDirection: { value: new THREE.Vector2() },
      },
      depthTest: false,
      depthWrite: false,
    });

    this.compositeMaterial = new THREE.ShaderMaterial({
      vertexShader: FULLSCREEN_VERTEX,
      fragmentShader: COMPOSITE_FRAGMENT,
      uniforms: {
        uScene: { value: null },
        uBloomNear: { value: null },
        uBloomWide: { value: null },
        uTexel: { value: new THREE.Vector2() },
        uBloomIntensity: { value: this.options.bloomIntensity },
        uVignette: { value: this.options.vignette },
        uSaturation: { value: this.options.saturation },
        uContrast: { value: this.options.contrast },
        uExposure: { value: this.options.exposure },
        uSharpen: { value: this.options.sharpen },
        uTint: { value: new THREE.Color(...this.options.tint) },
        uFlash: { value: 0 },
        uFlashColor: { value: new THREE.Color(1, 0.94, 0.82) },
      },
      depthTest: false,
      depthWrite: false,
    });

    // Triángulo de pantalla completa: una primitiva menos que dos triángulos y
    // evita la costura diagonal en el centro de la imagen.
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.Float32BufferAttribute([-1, -1, 0, 3, -1, 0, -1, 3, 0], 3));
    geometry.setAttribute('uv', new THREE.Float32BufferAttribute([0, 0, 2, 0, 0, 2], 2));
    this.quad = new THREE.Mesh(geometry, this.brightMaterial);
    this.quad.frustumCulled = false;
    this.quadScene.add(this.quad);
  }

  setSize(width: number, height: number, pixelRatio: number): void {
    this.width = Math.max(1, Math.floor(width * pixelRatio));
    this.height = Math.max(1, Math.floor(height * pixelRatio));
    this.sceneTarget.setSize(this.width, this.height);
    this.bloomNearA.setSize(Math.max(1, this.width >> 2), Math.max(1, this.height >> 2));
    this.bloomNearB.setSize(Math.max(1, this.width >> 2), Math.max(1, this.height >> 2));
    this.bloomWideA.setSize(Math.max(1, this.width >> 3), Math.max(1, this.height >> 3));
    this.bloomWideB.setSize(Math.max(1, this.width >> 3), Math.max(1, this.height >> 3));
    this.compositeMaterial.uniforms.uTexel.value.set(1 / this.width, 1 / this.height);
  }

  /** Destello de pantalla; lo disparan las ultimates y los meteoros grandes. */
  addFlash(amount: number, color?: THREE.Color): void {
    // Tope bajo a propósito: un fogonazo debe subrayar el momento, no tapar la
    // batalla. Si se acumulan varias ultimates seguidas, no puede quedarse blanco.
    this.flash = Math.min(0.45, this.flash + amount);
    if (color) this.compositeMaterial.uniforms.uFlashColor.value.copy(color);
  }

  update(): void {
    if (this.flash <= 0) return;
    // Decae con el reloj real, no con el delta de render: si el equipo pierde FPS,
    // el destello tiene que apagarse igual de rápido en segundos de verdad.
    const now = performance.now();
    const dt = this.lastFlashTime === 0 ? 0.016 : Math.min(0.25, (now - this.lastFlashTime) / 1000);
    this.lastFlashTime = now;
    this.flash = Math.max(0, this.flash - dt * 3.2);
    this.compositeMaterial.uniforms.uFlash.value = this.flash;
  }

  private blit(renderer: THREE.WebGLRenderer, material: THREE.ShaderMaterial, target: THREE.WebGLRenderTarget | null): void {
    this.quad.material = material;
    renderer.setRenderTarget(target);
    renderer.render(this.quadScene, this.quadCamera);
  }

  render(renderer: THREE.WebGLRenderer, scene: THREE.Scene, camera: THREE.Camera): void {
    if (!this.options.enabled) {
      renderer.setRenderTarget(null);
      renderer.render(scene, camera);
      return;
    }

    // 1. Escena en HDR lineal.
    renderer.setRenderTarget(this.sceneTarget);
    renderer.clear();
    renderer.render(scene, camera);

    // 2. Bright-pass a un cuarto de resolución.
    this.brightMaterial.uniforms.uScene.value = this.sceneTarget.texture;
    this.blit(renderer, this.brightMaterial, this.bloomNearA);

    // 3. Blur separable: horizontal y vertical.
    const nearW = this.bloomNearA.width;
    const nearH = this.bloomNearA.height;
    this.blurMaterial.uniforms.uSource.value = this.bloomNearA.texture;
    this.blurMaterial.uniforms.uDirection.value.set(1 / nearW, 0);
    this.blit(renderer, this.blurMaterial, this.bloomNearB);

    this.blurMaterial.uniforms.uSource.value = this.bloomNearB.texture;
    this.blurMaterial.uniforms.uDirection.value.set(0, 1 / nearH);
    this.blit(renderer, this.blurMaterial, this.bloomNearA);

    // 4. Segundo nivel más pequeño = halo ancho y suave alrededor del fuego.
    this.blurMaterial.uniforms.uSource.value = this.bloomNearA.texture;
    this.blurMaterial.uniforms.uDirection.value.set(1 / this.bloomWideA.width, 0);
    this.blit(renderer, this.blurMaterial, this.bloomWideA);

    this.blurMaterial.uniforms.uSource.value = this.bloomWideA.texture;
    this.blurMaterial.uniforms.uDirection.value.set(0, 1 / this.bloomWideA.height);
    this.blit(renderer, this.blurMaterial, this.bloomWideB);

    // 5. Composición final a pantalla.
    this.compositeMaterial.uniforms.uScene.value = this.sceneTarget.texture;
    this.compositeMaterial.uniforms.uBloomNear.value = this.bloomNearA.texture;
    this.compositeMaterial.uniforms.uBloomWide.value = this.bloomWideB.texture;
    this.blit(renderer, this.compositeMaterial, null);
  }

  setOptions(options: Partial<PostOptions>): void {
    this.options = { ...this.options, ...options };
    this.brightMaterial.uniforms.uThreshold.value = this.options.bloomThreshold;
    const c = this.compositeMaterial.uniforms;
    c.uBloomIntensity.value = this.options.bloomIntensity;
    c.uVignette.value = this.options.vignette;
    c.uSaturation.value = this.options.saturation;
    c.uContrast.value = this.options.contrast;
    c.uExposure.value = this.options.exposure;
    c.uSharpen.value = this.options.sharpen;
    c.uTint.value.setRGB(this.options.tint[0], this.options.tint[1], this.options.tint[2]);
  }

  get enabled(): boolean {
    return this.options.enabled;
  }

  dispose(): void {
    for (const target of [this.sceneTarget, this.bloomNearA, this.bloomNearB, this.bloomWideA, this.bloomWideB]) {
      target.dispose();
    }
    this.brightMaterial.dispose();
    this.blurMaterial.dispose();
    this.compositeMaterial.dispose();
    this.quad.geometry.dispose();
  }
}
