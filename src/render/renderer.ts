/**
 * Escena y bucle de render.
 *
 * Une el terreno, las unidades, los efectos y la cámara, y traduce cada
 * snapshot del worker en lo que se ve. También vigila los FPS: si el equipo del
 * streamer no da abasto, baja la resolución interna antes de que el directo
 * empiece a tironear.
 */

import * as THREE from 'three';
import type { GameConfig, TeamId } from '../shared/config';
import { clamp } from '../shared/math';
import { createTerrain, type Terrain } from '../shared/terrain';
import { PROJECTILE_STRIDE, type Snapshot } from '../sim/protocol';
import { CameraDirector } from './camera-director';
import { Effects } from './effects';
import { Nametags, type ChampionInfo } from './nametags';
import { PostProcessing } from './post';
import { Props } from './props';
import { Weather, SEASON_PROFILES, type Season, type WeatherKind } from './weather';
import { NOISE_2D } from './shader-chunks';
import { buildArchetypes } from '../game/archetypes';
import { PALETTES, TerrainRenderer, type TerrainPalette } from './terrain-mesh';
import { UnitsRenderer } from './units-renderer';

const SKY_VERTEX = /* glsl */ `
  varying vec3 vDirection;
  void main() {
    vDirection = normalize(position);
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`;

const SKY_FRAGMENT = /* glsl */ `
  precision highp float;
${NOISE_2D}
  uniform vec3 uSky;
  uniform vec3 uHorizon;
  uniform vec3 uSun;
  uniform vec3 uSunDir;
  uniform float uTime;
  uniform float uCloudAmount;
  varying vec3 vDirection;

  void main() {
    vec3 dir = normalize(vDirection);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 color = mix(uHorizon, uSky, pow(h, 0.75));

    float sunDot = max(dot(dir, normalize(uSunDir)), 0.0);

    if (uCloudAmount > 0.0 && dir.y > 0.02) {
      // Proyección de la dirección sobre un plano a altura fija: es la forma
      // barata de tener nubes que se abren hacia el horizonte, como de verdad.
      vec2 plane = dir.xz / dir.y;
      vec2 uv = plane * 0.05 + vec2(uTime * 0.004, uTime * 0.0022);

      // Dos capas a distinta velocidad dan sensación de profundidad.
      float low = fbm2(uv, 5);
      float high = fbm2(uv * 2.3 + vec2(uTime * 0.006, 0.0), 4);
      float density = smoothstep(0.46, 0.78, low * 0.7 + high * 0.4);

      // Se desvanecen al acercarse al horizonte para que no aparezca el corte.
      density *= smoothstep(0.02, 0.32, dir.y) * uCloudAmount;

      // Borde iluminado por el sol: sin esto las nubes parecen manchas planas.
      float rim = smoothstep(0.4, 0.85, low) * pow(sunDot * 0.5 + 0.5, 3.0);
      vec3 cloud = mix(vec3(0.62, 0.66, 0.72), vec3(1.0, 0.98, 0.94), rim);
      cloud += uSun * rim * 0.5;

      color = mix(color, cloud, clamp(density, 0.0, 0.95));
    }

    // Disco solar y halo. El disco emite muy por encima del blanco: es la
    // fuente principal de bloom del cielo.
    float disc = pow(sunDot, 900.0) * 14.0;
    float glow = pow(sunDot, 6.0) * 0.28;
    color += uSun * (disc + glow);

    gl_FragColor = vec4(color, 1.0);
  }
`;

export class GameRenderer {
  readonly scene = new THREE.Scene();
  readonly camera: THREE.PerspectiveCamera;
  readonly director: CameraDirector;
  readonly nametags: Nametags;

  private renderer: THREE.WebGLRenderer;
  private terrainRenderer: TerrainRenderer;
  private unitsRenderer: UnitsRenderer;
  private effects: Effects;
  private sky: THREE.Mesh;
  private skyMaterial: THREE.ShaderMaterial;
  private post: PostProcessing;
  private props: Props;
  private weather: Weather;
  private terrain: Terrain;
  private palette: TerrainPalette;
  private elapsed = 0;
  private basePixelRatio: number;
  private currentPixelRatio: number;
  private fpsSamples: number[] = [];
  private adaptTimer = 0;

  fps = 60;

  constructor(
    private canvas: HTMLCanvasElement,
    private config: GameConfig,
    nametagContainer: HTMLElement,
  ) {
    this.terrain = createTerrain({
      seed: config.simulator.seed,
      width: config.battle.fieldWidth,
      depth: config.battle.fieldDepth,
      riverWidth: config.battle.riverWidth,
    });
    this.palette = PALETTES[config.graphics.timeOfDay] ?? PALETTES.day;

    this.renderer = new THREE.WebGLRenderer({
      canvas,
      antialias: config.graphics.quality === 'high' || config.graphics.quality === 'ultra',
      alpha: false,
      powerPreference: 'high-performance',
      stencil: false,
    });
    this.basePixelRatio = Math.min(window.devicePixelRatio || 1, config.graphics.pixelRatioCap);
    this.currentPixelRatio = this.basePixelRatio;
    this.renderer.setPixelRatio(this.currentPixelRatio);
    this.renderer.setClearColor(this.palette.fog, 1);

    this.camera = new THREE.PerspectiveCamera(config.camera.fov, 16 / 9, 0.5, 2400);

    // --- Cielo ---
    this.skyMaterial = new THREE.ShaderMaterial({
      vertexShader: SKY_VERTEX,
      fragmentShader: SKY_FRAGMENT,
      uniforms: {
        uSky: { value: this.palette.sky.clone() },
        uHorizon: { value: this.palette.horizon.clone() },
        uSun: { value: this.palette.sun.clone() },
        uSunDir: { value: this.palette.lightDir.clone().normalize() },
        uTime: { value: 0 },
        uCloudAmount: { value: config.graphics.clouds ?? 0.85 },
      },
      side: THREE.BackSide,
      depthWrite: false,
    });
    this.sky = new THREE.Mesh(new THREE.SphereGeometry(1600, 24, 16), this.skyMaterial);
    this.sky.frustumCulled = false;
    this.sky.renderOrder = -1;
    this.scene.add(this.sky);

    // --- Terreno ---
    this.terrainRenderer = new TerrainRenderer(this.terrain, this.palette, {
      fogDensity: config.graphics.fogDensity,
      bloodTextureSize: config.graphics.bloodTextureSize,
      quality: config.graphics.quality,
    });
    this.scene.add(this.terrainRenderer.root);

    // --- Decorado: rocas y arboleda que dan escala al campo ---
    this.props = new Props(this.terrain, {
      density: config.graphics.quality === 'low' ? 0 : (config.graphics.propDensity ?? 1),
      fogColor: this.palette.fog,
      fogDensity: config.graphics.fogDensity,
    });
    this.props.setLighting(
      this.palette.sky, this.palette.ground, this.palette.sun,
      this.palette.lightDir, this.palette.fog, config.graphics.fogDensity,
    );
    this.scene.add(this.props.root);

    // --- Unidades ---
    const teamColors = {
      red: { color: new THREE.Color(config.teams.red.color), dark: new THREE.Color(config.teams.red.colorDark) },
      blue: { color: new THREE.Color(config.teams.blue.color), dark: new THREE.Color(config.teams.blue.colorDark) },
    };
    this.unitsRenderer = new UnitsRenderer({
      // Margen sobre el tope por equipo: élites y campeones viven fuera de la cuota.
      capacityPerGroup: config.battle.renderCapPerTeam + 512,
      // Solo la calidad más baja recorta brazos y equipo: sin ellos el soldado se
      // lee como un bloque y se pierde la animación de golpe.
      detail: config.graphics.quality === 'low' ? 'simple' : config.graphics.quality === 'medium' ? 'low' : 'high',
      // Una silueta propia por arquetipo; el orden debe coincidir con el que
      // recibe el worker, porque el snapshot agrupa por índice de arquetipo.
      archetypes: buildArchetypes(config).map((a) => ({ key: a.key, mesh: a.mesh, stackable: a.stackable })),
      teamColors,
      shadows: config.graphics.shadows,
      fogColor: this.palette.fog,
      fogDensity: config.graphics.fogDensity,
      lodDistance: config.graphics.lodDistance ?? 70,
      textureDistance: config.graphics.textureDistance ?? 45,
    });
    this.unitsRenderer.setLighting(this.palette.sky, this.palette.ground, this.palette.sun, this.palette.lightDir);
    this.scene.add(this.unitsRenderer.root);

    // --- Efectos ---
    this.effects = new Effects({
      particleLimit: config.graphics.particleLimit,
      corpseLimit: config.graphics.corpseLimit,
      gore: config.graphics.gore,
      teamColors: { red: teamColors.red.color, blue: teamColors.blue.color },
    });
    this.scene.add(this.effects.root);

    // --- Post-procesado ---
    this.post = new PostProcessing({
      enabled: config.graphics.postProcessing !== false && config.graphics.quality !== 'low',
      bloomThreshold: config.graphics.bloomThreshold,
      bloomIntensity: config.graphics.bloomIntensity,
      vignette: config.graphics.vignette,
      saturation: config.graphics.saturation,
      contrast: config.graphics.contrast,
      exposure: config.graphics.exposure,
      sharpen: config.graphics.sharpen,
    });

    // --- Estación y clima ---
    // La caja de precipitación es pequeña a propósito: sigue a la cámara, así que
    // solo tiene que cubrir lo que se ve. Cuanto más chica, más densa se ve la
    // lluvia con las mismas partículas.
    const precipScale = { low: 0, medium: 0.45, high: 1, ultra: 1.6 }[config.graphics.quality] ?? 1;
    this.weather = new Weather({
      particleCount: Math.round((config.graphics.precipitationParticles ?? 9000) * precipScale),
      boxSize: 78,
      boxHeight: 46,
    });
    this.scene.add(this.weather.root);
    this.setSeason(config.world?.season ?? 'summer');
    this.setWeather(config.world?.weather ?? 'clear');

    this.director = new CameraDirector(this.camera, this.terrain, config);
    this.nametags = new Nametags(nametagContainer, config.chat.showNametags ? config.chat.maxNametags : 0);

    this.resize();
  }

  // ------------------------------------------------------------------ ciclo

  applySnapshot(snapshot: Snapshot): void {
    const units = new Float32Array(snapshot.units);
    // El reparto por LOD necesita la posición de la cámara de este frame.
    this.unitsRenderer.update(units, snapshot.groups, this.camera.position);

    const projectiles = new Float32Array(snapshot.projectiles);
    this.effects.updateProjectiles(projectiles, snapshot.projectileCount, PROJECTILE_STRIDE);

    for (const death of snapshot.deaths) {
      this.terrainRenderer.paintBlood(death.x, death.z, death.soldiers);
      this.effects.spawnBlood(death.x, death.y, death.z, death.soldiers);
      this.effects.spawnCorpse(death.x, death.y, death.z, death.rot, death.team, death.scale);
    }

    for (const impact of snapshot.impacts) {
      if (impact.kind === 0) continue; // los golpes cuerpo a cuerpo ya generan sangre
      this.effects.spawnExplosion(impact.x, impact.y, impact.z, impact.power, impact.kind);
      if (impact.kind === 2) this.director.shake(0.25);
    }

    for (const meteor of snapshot.meteors) {
      this.effects.spawnMeteor(meteor.x, meteor.y, meteor.z, meteor.delay, meteor.radius);
    }

    this.director.setHotspot(snapshot.hotspot.x, snapshot.hotspot.z, snapshot.hotspot.intensity);
  }

  updateNametags(champions: Snapshot['champions'], lookup: Map<number, ChampionInfo>): void {
    const size = this.renderer.getSize(new THREE.Vector2());
    const closest = this.nametags.update(
      champions,
      lookup,
      this.camera,
      size.x,
      size.y,
      this.config.chat.nametagMaxDistance,
    );
    this.director.setChampionFocus(closest);
  }

  render(dt: number): void {
    this.elapsed += dt;
    this.director.update(dt);
    this.weather.update(dt, this.elapsed, this.camera.position);
    // El viento es global: mueve capas, alas, copas y precipitación a la vez.
    this.unitsRenderer.setTime(this.elapsed, this.weather.wind);
    this.props.setTime(this.elapsed, this.weather.wind);
    if (this.weather.lightning > 0) {
      this.post.addFlash(this.weather.lightning * 0.5, new THREE.Color('#dce8ff'));
    }
    this.terrainRenderer.update(dt, this.elapsed);
    this.effects.update(dt);
    // El cielo viaja con la cámara para que nunca se alcance su borde.
    this.sky.position.copy(this.camera.position);
    this.skyMaterial.uniforms.uTime.value = this.elapsed;
    this.post.update();
    this.post.render(this.renderer, this.scene, this.camera);
    this.trackPerformance(dt);
  }

  /**
   * Si los FPS caen por debajo del objetivo de forma sostenida, se baja la
   * resolución interna. Mejor un directo un poco menos nítido que uno a tirones.
   */
  private trackPerformance(dt: number): void {
    if (dt <= 0) return;
    this.fpsSamples.push(1 / dt);
    if (this.fpsSamples.length > 90) this.fpsSamples.shift();
    this.fps = this.fpsSamples.reduce((a, b) => a + b, 0) / this.fpsSamples.length;

    this.adaptTimer += dt;
    if (this.adaptTimer < 3 || this.fpsSamples.length < 60) return;
    this.adaptTimer = 0;

    const target = this.config.graphics.targetFps;
    if (this.fps < target * 0.72 && this.currentPixelRatio > 0.65) {
      this.currentPixelRatio = Math.max(0.65, this.currentPixelRatio - 0.2);
      this.applyPixelRatio();
    } else if (this.fps > target * 0.97 && this.currentPixelRatio < this.basePixelRatio) {
      this.currentPixelRatio = Math.min(this.basePixelRatio, this.currentPixelRatio + 0.1);
      this.applyPixelRatio();
    }
  }

  // ----------------------------------------------------------------- ajustes

  /** Los render targets del post deben seguir al pixel ratio del renderer. */
  private applyPixelRatio(): void {
    this.renderer.setPixelRatio(this.currentPixelRatio);
    const size = this.renderer.getSize(new THREE.Vector2());
    this.post.setSize(size.x, size.y, this.currentPixelRatio);
  }

  resize(): void {
    const width = this.canvas.clientWidth || window.innerWidth;
    const height = this.canvas.clientHeight || window.innerHeight;
    this.renderer.setSize(width, height, false);
    this.post.setSize(width, height, this.currentPixelRatio);
    this.camera.aspect = width / Math.max(1, height);
    this.camera.updateProjectionMatrix();
  }

  /** Empieza una ronda nueva: limpia sangre, cadáveres y partículas. */
  resetRound(): void {
    this.terrainRenderer.clearBlood();
    this.effects.reset();
    this.nametags.clear();
    this.director.reset();
  }

  setTeamColor(team: TeamId, color: string, dark: string): void {
    const main = new THREE.Color(color);
    this.unitsRenderer.setTeamColors(team, main, new THREE.Color(dark));
    this.effects.setTeamColor(team === 'red' ? 0 : 1, main);
  }

  setTimeOfDay(value: 'day' | 'sunset' | 'night'): void {
    this.palette = PALETTES[value] ?? PALETTES.day;
    this.terrainRenderer.setPalette(this.palette);
    this.unitsRenderer.setFog(this.palette.fog, this.config.graphics.fogDensity);
    this.unitsRenderer.setLighting(this.palette.sky, this.palette.ground, this.palette.sun, this.palette.lightDir);
    this.skyMaterial.uniforms.uSky.value.copy(this.palette.sky);
    this.skyMaterial.uniforms.uHorizon.value.copy(this.palette.horizon);
    this.skyMaterial.uniforms.uSun.value.copy(this.palette.sun);
    this.skyMaterial.uniforms.uSunDir.value.copy(this.palette.lightDir).normalize();
    // La franja horaria es la base; estación y clima se aplican encima.
    this.applyAtmosphere();
  }

  setQuality(quality: GameConfig['graphics']['quality']): void {
    this.config.graphics.quality = quality;
    const ratioCap = { low: 0.85, medium: 1.1, high: 1.5, ultra: 2 }[quality];
    this.basePixelRatio = Math.min(window.devicePixelRatio || 1, ratioCap, this.config.graphics.pixelRatioCap);
    this.currentPixelRatio = this.basePixelRatio;
    this.applyPixelRatio();
    this.unitsRenderer.setShadowsEnabled(quality !== 'low' && this.config.graphics.shadows);
    // Con menos calidad, el corte de LOD se acerca: menos figuras detalladas.
    this.unitsRenderer.setLodDistance((this.config.graphics.lodDistance ?? 70) * { low: 0, medium: 0.55, high: 1, ultra: 1.5 }[quality]);
    this.effects.setGore(quality !== 'low' && this.config.graphics.gore);
    this.post.setOptions({ enabled: this.config.graphics.postProcessing !== false && quality !== 'low' });
  }

  updateConfig(config: GameConfig): void {
    this.config = config;
    this.director.updateConfig(config);
    this.nametags.setMaxTags(config.chat.showNametags ? config.chat.maxNametags : 0);
    this.terrainRenderer.setFogDensity(config.graphics.fogDensity);
    this.unitsRenderer.setFog(this.palette.fog, config.graphics.fogDensity);
    this.setTeamColor('red', config.teams.red.color, config.teams.red.colorDark);
    this.setTeamColor('blue', config.teams.blue.color, config.teams.blue.colorDark);
  }

  get terrainRef(): Terrain {
    return this.terrain;
  }

  /** Punto del campo donde conviene encuadrar una ultimate del equipo indicado. */
  dramaticPointFor(team: TeamId): { x: number; z: number } {
    const sign = team === 'red' ? 1 : -1;
    const focus = this.director.currentFocus;
    return { x: clamp(focus.x + sign * 18, -this.terrain.halfWidth * 0.8, this.terrain.halfWidth * 0.8), z: focus.z };
  }

  /** Destello de pantalla: lo llama el juego al lanzar una ultimate. */
  flash(amount: number, color?: string): void {
    this.post.addFlash(amount, color ? new THREE.Color(color) : undefined);
  }

  /** Cambia la estación: paleta del suelo, follaje, nieve y tono del agua. */
  setSeason(season: Season): void {
    const profile = SEASON_PROFILES[season] ?? SEASON_PROFILES.summer;
    this.weather.setSeason(season);
    this.terrainRenderer.setSeason(profile);
    this.props.setFoliage(profile.foliageA, profile.foliageB);
    this.applyAtmosphere();
  }

  /** Cambia el clima: precipitación, niebla, oscurecimiento y suelo mojado. */
  setWeather(weather: WeatherKind): void {
    this.weather.setWeather(weather);
    this.applyAtmosphere();
  }

  /**
   * Recalcula todo lo que depende a la vez de estación y clima. Se llama al
   * cambiar cualquiera de los dos porque se multiplican entre sí: la nieve de
   * invierno con tormenta no se ve igual que con cielo despejado.
   */
  private applyAtmosphere(): void {
    const season = this.weather.seasonProfile;
    const sky = this.weather.weatherProfile;

    const gloom = 1 - sky.gloom * 0.8;
    const sun = this.palette.sun.clone().multiply(season.sunTint).multiplyScalar(gloom);
    const skyColor = this.palette.sky.clone().multiply(season.skyTint).multiplyScalar(gloom);
    const fogColor = this.palette.fog.clone().multiply(season.skyTint).multiplyScalar(0.35 + gloom * 0.65);
    const fogDensity = this.config.graphics.fogDensity * sky.fogScale;

    // El agua es color base + especular, así que no se entera de que el sol se ha
    // apagado: bajo tormenta seguía siendo una plancha azul turquesa. Hay que
    // oscurecerla a mano. El tinte de estación NO va aquí: ya lo aplica el shader
    // con `uSeasonWater` desde setSeason, y aplicarlo dos veces lo dobla.
    const waterDim = 0.4 + gloom * 0.6;
    const water = this.palette.water.clone().multiplyScalar(waterDim);
    const waterDeep = this.palette.waterDeep.clone().multiplyScalar(waterDim);

    this.terrainRenderer.setPalette({
      ...this.palette,
      sun,
      sky: skyColor,
      fog: fogColor,
      water,
      waterDeep,
    });
    this.terrainRenderer.setSeason(season);
    this.terrainRenderer.setFogDensity(fogDensity);
    this.terrainRenderer.setWetness(sky.wetness);

    this.unitsRenderer.setLighting(skyColor, this.palette.ground, sun, this.palette.lightDir);
    this.unitsRenderer.setFog(fogColor, fogDensity);
    // Nieve en los hombros solo si de verdad está nevando o el manto es espeso.
    this.unitsRenderer.setWeather(sky.wetness, Math.max(sky.snowy * 0.7, season.groundSnow * 0.35));

    this.props.setFoliage(season.foliageA, season.foliageB);
    this.props.setLighting(skyColor, this.palette.ground, sun, this.palette.lightDir, fogColor, fogDensity);

    this.skyMaterial.uniforms.uSky.value.copy(skyColor);
    this.skyMaterial.uniforms.uHorizon.value.copy(this.palette.horizon).multiplyScalar(gloom);
    this.skyMaterial.uniforms.uSun.value.copy(sun);
    this.skyMaterial.uniforms.uCloudAmount.value = Math.min(1, (this.config.graphics.clouds ?? 0.85) + sky.cloudBoost);
    this.renderer.setClearColor(fogColor, 1);
  }

  /** Sortea un clima acorde a la estación actual y lo aplica. */
  rollWeather(): WeatherKind {
    const next = this.weather.rollWeather();
    this.setWeather(next);
    return next;
  }

  get atmosphere(): { season: Season; weather: WeatherKind } {
    return { season: this.weather.currentSeason, weather: this.weather.currentWeather };
  }

  dispose(): void {
    this.post.dispose();
    this.props.dispose();
    this.weather.dispose();
    this.unitsRenderer.dispose();
    this.terrainRenderer.dispose();
    this.effects.dispose();
    this.nametags.dispose();
    this.skyMaterial.dispose();
    this.sky.geometry.dispose();
    this.renderer.dispose();
  }
}
