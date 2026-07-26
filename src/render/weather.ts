/**
 * Estaciones y clima.
 *
 * Dos sistemas distintos que se combinan:
 *
 * - **Estación** (primavera, verano, otoño, invierno): cambia la paleta del
 *   terreno, el color del follaje, la nieve acumulada en el suelo y el tono del
 *   agua. Es un estado permanente de la ronda.
 * - **Clima** (despejado, lluvia, nieve, niebla, tormenta): añade partículas,
 *   viento, oscurece el cielo y moja el suelo y las unidades. Puede cambiar
 *   dentro de una misma estación.
 *
 * Las partículas de lluvia y nieve se dibujan en una **caja que sigue a la
 * cámara**: no tiene sentido simular precipitación en todo el mapa cuando solo
 * se ve un trozo. Cada partícula se posiciona con `mod()` sobre el tiempo, así
 * que caen eternamente sin que la CPU toque un solo valor.
 */

import * as THREE from 'three';
import { createRng } from '../shared/math';

export type Season = 'spring' | 'summer' | 'autumn' | 'winter';
export type WeatherKind = 'clear' | 'rain' | 'snow' | 'fog' | 'storm';

export const SEASONS: Season[] = ['spring', 'summer', 'autumn', 'winter'];
export const WEATHERS: WeatherKind[] = ['clear', 'rain', 'snow', 'fog', 'storm'];

export interface SeasonProfile {
  label: string;
  /** Multiplicador de color sobre la paleta base del terreno. */
  grassTint: THREE.Color;
  dirtTint: THREE.Color;
  /** Follaje de los árboles. */
  foliageA: THREE.Color;
  foliageB: THREE.Color;
  /**
   * Tono al que se lleva el suelo. `grassTint` solo escala canales y por eso
   * jamás puede convertir un verde en el ocre de otoño; este color sí, porque se
   * aplica como desplazamiento de tono conservando la luminancia.
   */
  groundHue: THREE.Color;
  /** 0 = terreno tal cual, 1 = totalmente teñido del color de la estación. */
  groundHueAmount: number;
  /** 0 = sin nieve, 1 = manto completo. */
  groundSnow: number;
  waterTint: THREE.Color;
  /** Ajuste de la luz solar y el ambiente. */
  sunTint: THREE.Color;
  skyTint: THREE.Color;
  /** Climas que pueden salir en esta estación, con su peso. */
  weatherWeights: Partial<Record<WeatherKind, number>>;
}

export const SEASON_PROFILES: Record<Season, SeasonProfile> = {
  spring: {
    label: 'Primavera',
    grassTint: new THREE.Color(1.02, 1.12, 0.86),
    dirtTint: new THREE.Color(0.98, 0.96, 0.92),
    groundHue: new THREE.Color('#6f9f3c'),
    groundHueAmount: 0.16,
    foliageA: new THREE.Color('#4e8c33'),
    foliageB: new THREE.Color('#75a83f'),
    groundSnow: 0,
    waterTint: new THREE.Color(1.0, 1.04, 1.02),
    sunTint: new THREE.Color(1.0, 1.0, 0.98),
    skyTint: new THREE.Color(1.0, 1.0, 1.02),
    weatherWeights: { clear: 45, rain: 35, fog: 12, storm: 8 },
  },
  summer: {
    label: 'Verano',
    grassTint: new THREE.Color(1.08, 1.02, 0.7),
    dirtTint: new THREE.Color(1.08, 1.02, 0.88),
    groundHue: new THREE.Color('#8d9a3a'),
    groundHueAmount: 0.22,
    foliageA: new THREE.Color('#3f7a2c'),
    foliageB: new THREE.Color('#5e9435'),
    groundSnow: 0,
    waterTint: new THREE.Color(1.0, 1.02, 1.06),
    sunTint: new THREE.Color(1.04, 1.0, 0.92),
    skyTint: new THREE.Color(1.0, 1.02, 1.05),
    weatherWeights: { clear: 62, rain: 14, fog: 6, storm: 18 },
  },
  autumn: {
    label: 'Otoño',
    grassTint: new THREE.Color(1.12, 0.9, 0.6),
    dirtTint: new THREE.Color(1.06, 0.94, 0.82),
    groundHue: new THREE.Color('#997840'),
    groundHueAmount: 0.45,
    foliageA: new THREE.Color('#a3661f'),
    foliageB: new THREE.Color('#c98a24'),
    groundSnow: 0,
    waterTint: new THREE.Color(0.98, 0.96, 0.94),
    sunTint: new THREE.Color(1.06, 0.96, 0.82),
    skyTint: new THREE.Color(1.0, 0.98, 0.96),
    weatherWeights: { clear: 34, rain: 32, fog: 22, storm: 12 },
  },
  winter: {
    label: 'Invierno',
    grassTint: new THREE.Color(0.86, 0.9, 0.94),
    dirtTint: new THREE.Color(0.88, 0.9, 0.94),
    groundHue: new THREE.Color('#7d8478'),
    groundHueAmount: 0.4,
    foliageA: new THREE.Color('#33512e'),
    foliageB: new THREE.Color('#46603c'),
    groundSnow: 0.62,
    waterTint: new THREE.Color(0.9, 0.96, 1.06),
    sunTint: new THREE.Color(0.94, 0.97, 1.06),
    skyTint: new THREE.Color(0.94, 0.97, 1.06),
    weatherWeights: { clear: 30, snow: 44, fog: 20, storm: 6 },
  },
};

export interface WeatherProfile {
  label: string;
  /** 0 = nada, 1 = aguacero. */
  precipitation: number;
  /** 0 = lluvia, 1 = nieve. Define la forma y la velocidad de la partícula. */
  snowy: number;
  /** Multiplicador sobre la densidad de niebla base. */
  fogScale: number;
  /** Oscurecimiento del cielo y del sol. */
  gloom: number;
  /** Cuánto se moja el suelo y la ropa. */
  wetness: number;
  windStrength: number;
  lightningPerMinute: number;
  cloudBoost: number;
}

export const WEATHER_PROFILES: Record<WeatherKind, WeatherProfile> = {
  clear: { label: 'Despejado', precipitation: 0, snowy: 0, fogScale: 1, gloom: 0, wetness: 0, windStrength: 0.12, lightningPerMinute: 0, cloudBoost: 0 },
  rain: { label: 'Lluvia', precipitation: 0.8, snowy: 0, fogScale: 1.7, gloom: 0.4, wetness: 0.85, windStrength: 0.4, lightningPerMinute: 0, cloudBoost: 0.5 },
  snow: { label: 'Nieve', precipitation: 0.6, snowy: 1, fogScale: 2.1, gloom: 0.22, wetness: 0.25, windStrength: 0.3, lightningPerMinute: 0, cloudBoost: 0.45 },
  // Ojo con fogScale en niebla: por encima de ~3 el ejército del fondo se borra
  // del todo y el espectador deja de ver contra quién pelea.
  fog: { label: 'Niebla', precipitation: 0, snowy: 0, fogScale: 3.0, gloom: 0.3, wetness: 0.3, windStrength: 0.08, lightningPerMinute: 0, cloudBoost: 0.3 },
  storm: { label: 'Tormenta', precipitation: 1, snowy: 0, fogScale: 2.2, gloom: 0.62, wetness: 1, windStrength: 0.9, lightningPerMinute: 14, cloudBoost: 0.8 },
};

const PRECIP_VERTEX = /* glsl */ `
  attribute vec3 aSeed;   // x,z = posición en la celda, y = fase
  attribute float aSize;
  uniform float uTime;
  uniform vec3 uCenter;
  uniform vec3 uExtent;
  uniform float uFallSpeed;
  uniform float uSnowy;
  uniform vec2 uWind;
  varying float vFade;
  varying vec2 vQuad;

  void main() {
    // La partícula cae en bucle dentro de una caja anclada a la cámara. El
    // mod() sobre el tiempo la recicla eternamente sin tocar nada desde CPU.
    float fall = mod(aSeed.y + uTime * uFallSpeed, 1.0);
    float y = uExtent.y * (1.0 - fall);

    vec3 world = vec3(
      uCenter.x + (aSeed.x - 0.5) * uExtent.x,
      uCenter.y + y,
      uCenter.z + (aSeed.z - 0.5) * uExtent.z
    );

    // El viento arrastra; la nieve además serpentea al caer.
    world.xz += uWind * (uExtent.y - y) * 0.12;
    if (uSnowy > 0.5) {
      world.x += sin(uTime * 1.6 + aSeed.x * 40.0) * 0.5;
      world.z += cos(uTime * 1.3 + aSeed.z * 37.0) * 0.5;
    }

    vec4 mv = modelViewMatrix * vec4(world, 1.0);

    // El quad se escala CON la distancia para compensar la perspectiva. Sin esto
    // la gota que te pasa al lado se convierte en un tablón blanco de medio metro
    // y la del fondo desaparece. El clamp evita ambos extremos.
    float persp = clamp(-mv.z * 0.055, 0.55, 2.4);
    // La lluvia se estira en una raya vertical fina; la nieve es un copo redondo
    // y más gordo, porque si no a esa escala no se ve nada.
    float stretch = mix(11.0, 1.0, uSnowy);
    float width = aSize * persp * mix(0.3, 2.1, uSnowy);
    float height = aSize * persp * stretch * mix(1.0, 2.1, uSnowy);
    mv.xy += vec2(position.x * width, position.y * height);

    // Se desvanece al entrar y al salir de la caja para que no aparezca de golpe,
    // y también muy cerca de la cámara: una gota a 30 cm solo sería una mancha.
    vFade = smoothstep(0.0, 0.08, fall) * smoothstep(1.0, 0.86, fall) * smoothstep(1.5, 6.0, -mv.z);
    vQuad = position.xy * 2.0;
    gl_Position = projectionMatrix * mv;
  }
`;

const PRECIP_FRAGMENT = /* glsl */ `
  // highp para que uSnowy tenga la misma precisión que en el vértice: si difieren,
  // el enlazado del programa falla ("Precisions of uniform differ").
  precision highp float;
  uniform vec3 uColor;
  uniform float uOpacity;
  uniform float uSnowy;
  varying float vFade;
  varying vec2 vQuad;
  void main() {
    // Sin esta máscara el quad se ve tal cual: un rectángulo blanco de bordes
    // duros. La gota se afila por los extremos y el copo es un disco difuso.
    float drop = smoothstep(1.0, 0.05, abs(vQuad.y)) * smoothstep(1.0, 0.25, abs(vQuad.x));
    float flake = smoothstep(1.0, 0.15, length(vQuad));
    float mask = mix(drop, flake, uSnowy);

    float alpha = vFade * uOpacity * mask;
    if (alpha <= 0.01) discard;
    gl_FragColor = vec4(uColor, alpha);
  }
`;

export interface WeatherOptions {
  particleCount: number;
  boxSize: number;
  boxHeight: number;
}

export class Weather {
  readonly root = new THREE.Group();
  private mesh: THREE.Mesh;
  private material: THREE.ShaderMaterial;
  private geometry: THREE.InstancedBufferGeometry;
  private capacity: number;

  private season: Season = 'summer';
  private weather: WeatherKind = 'clear';
  private profile: WeatherProfile = WEATHER_PROFILES.clear;

  /** Viento actual, en unidades de mundo. Lo consumen capas, árboles y lluvia. */
  readonly wind = new THREE.Vector2();
  private windTarget = new THREE.Vector2();
  private windTimer = 0;
  private rng = createRng(20260726);

  private lightningTimer = 0;
  /** Intensidad del relámpago de este frame; el renderer la lee y la aplica. */
  lightning = 0;

  constructor(private options: WeatherOptions) {
    this.capacity = Math.max(0, options.particleCount);

    const base = new THREE.BufferGeometry();
    base.setAttribute('position', new THREE.Float32BufferAttribute([-0.5, -0.5, 0, 0.5, -0.5, 0, 0.5, 0.5, 0, -0.5, 0.5, 0], 3));
    base.setIndex([0, 1, 2, 0, 2, 3]);

    this.geometry = new THREE.InstancedBufferGeometry();
    this.geometry.setIndex(base.index);
    this.geometry.setAttribute('position', base.getAttribute('position'));

    const seeds = new Float32Array(this.capacity * 3);
    const sizes = new Float32Array(this.capacity);
    for (let i = 0; i < this.capacity; i++) {
      seeds[i * 3] = this.rng();
      seeds[i * 3 + 1] = this.rng();
      seeds[i * 3 + 2] = this.rng();
      sizes[i] = this.rng.range(0.03, 0.075);
    }
    this.geometry.setAttribute('aSeed', new THREE.InstancedBufferAttribute(seeds, 3));
    this.geometry.setAttribute('aSize', new THREE.InstancedBufferAttribute(sizes, 1));
    this.geometry.instanceCount = 0;
    this.geometry.boundingSphere = new THREE.Sphere(new THREE.Vector3(), 1e6);
    base.dispose();

    this.material = new THREE.ShaderMaterial({
      vertexShader: PRECIP_VERTEX,
      fragmentShader: PRECIP_FRAGMENT,
      uniforms: {
        uTime: { value: 0 },
        uCenter: { value: new THREE.Vector3() },
        uExtent: { value: new THREE.Vector3(options.boxSize, options.boxHeight, options.boxSize) },
        uFallSpeed: { value: 0.5 },
        uSnowy: { value: 0 },
        uWind: { value: new THREE.Vector2() },
        uColor: { value: new THREE.Color('#cddcea') },
        uOpacity: { value: 0 },
      },
      transparent: true,
      depthWrite: false,
    });

    this.mesh = new THREE.Mesh(this.geometry, this.material);
    this.mesh.frustumCulled = false;
    this.mesh.renderOrder = 10;
    this.root.add(this.mesh);
  }

  setSeason(season: Season): void {
    this.season = season;
  }

  setWeather(weather: WeatherKind): void {
    this.weather = weather;
    this.profile = WEATHER_PROFILES[weather] ?? WEATHER_PROFILES.clear;

    const uniforms = this.material.uniforms;
    uniforms.uSnowy.value = this.profile.snowy;
    uniforms.uFallSpeed.value = this.profile.snowy > 0.5 ? 0.12 : 0.62;
    // La nieve aguanta más opacidad porque el copo es un disco pequeño; la gota
    // ocupa mucha pantalla al estirarse y con más alfa parecía tiza.
    uniforms.uOpacity.value = this.profile.precipitation * (this.profile.snowy > 0.5 ? 0.95 : 0.5);
    uniforms.uColor.value.set(this.profile.snowy > 0.5 ? '#f2f7ff' : '#b9cfe4');
    this.geometry.instanceCount = Math.round(this.capacity * this.profile.precipitation);
    this.windTarget.set(this.profile.windStrength, this.profile.windStrength * 0.4);
  }

  /** Elige un clima al azar entre los posibles de la estación actual. */
  rollWeather(): WeatherKind {
    const weights = SEASON_PROFILES[this.season].weatherWeights;
    const entries = Object.entries(weights) as Array<[WeatherKind, number]>;
    const total = entries.reduce((sum, [, w]) => sum + w, 0);
    let roll = this.rng() * total;
    for (const [kind, weight] of entries) {
      roll -= weight;
      if (roll <= 0) return kind;
    }
    return 'clear';
  }

  update(dt: number, elapsed: number, cameraPosition: THREE.Vector3): void {
    this.material.uniforms.uTime.value = elapsed;
    // La caja de precipitación sigue a la cámara: simular lluvia en todo el mapa
    // sería tirar partículas donde nadie mira.
    this.material.uniforms.uCenter.value.set(
      cameraPosition.x,
      cameraPosition.y - this.options.boxHeight * 0.45,
      cameraPosition.z,
    );

    // El viento deriva poco a poco en vez de saltar entre valores.
    this.windTimer -= dt;
    if (this.windTimer <= 0) {
      this.windTimer = this.rng.range(4, 11);
      const strength = this.profile.windStrength;
      const angle = this.rng.range(0, Math.PI * 2);
      this.windTarget.set(Math.cos(angle) * strength, Math.sin(angle) * strength);
    }
    this.wind.lerp(this.windTarget, Math.min(1, dt * 0.6));
    this.material.uniforms.uWind.value.copy(this.wind);

    // Relámpagos: solo en tormenta, y a ráfagas.
    this.lightning = Math.max(0, this.lightning - dt * 4.5);
    if (this.profile.lightningPerMinute > 0) {
      this.lightningTimer -= dt;
      if (this.lightningTimer <= 0) {
        this.lightningTimer = this.rng.range(30, 90) / this.profile.lightningPerMinute;
        this.lightning = this.rng.range(0.45, 0.9);
      }
    }
  }

  get seasonProfile(): SeasonProfile {
    return SEASON_PROFILES[this.season];
  }

  get weatherProfile(): WeatherProfile {
    return this.profile;
  }

  get currentSeason(): Season {
    return this.season;
  }

  get currentWeather(): WeatherKind {
    return this.weather;
  }

  dispose(): void {
    this.geometry.dispose();
    this.material.dispose();
  }
}
