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
import { SRGB_ENCODE } from './shader-chunks';
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
  precision mediump float;
${SRGB_ENCODE}
  uniform vec3 uSky;
  uniform vec3 uHorizon;
  uniform vec3 uSun;
  uniform vec3 uSunDir;
  varying vec3 vDirection;

  void main() {
    vec3 dir = normalize(vDirection);
    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 color = mix(uHorizon, uSky, pow(h, 0.75));
    // Halo del sol.
    float sun = pow(max(dot(dir, normalize(uSunDir)), 0.0), 220.0);
    float glow = pow(max(dot(dir, normalize(uSunDir)), 0.0), 6.0) * 0.28;
    color += uSun * (sun + glow);
    gl_FragColor = vec4(linearToSRGB(color), 1.0);
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

    // --- Unidades ---
    const teamColors = {
      red: { color: new THREE.Color(config.teams.red.color), dark: new THREE.Color(config.teams.red.colorDark) },
      blue: { color: new THREE.Color(config.teams.blue.color), dark: new THREE.Color(config.teams.blue.colorDark) },
    };
    this.unitsRenderer = new UnitsRenderer({
      // Margen sobre el tope por equipo: élites y campeones viven fuera de la cuota.
      capacityPerGroup: config.battle.renderCapPerTeam + 512,
      // Solo la calidad más baja recorta brazos y arma: sin ellos el soldado se
      // lee como un bloque y se pierde la animación de golpe.
      detail: config.graphics.quality === 'low' ? 'simple' : 'full',
      teamColors,
      shadows: config.graphics.shadows,
      fogColor: this.palette.fog,
      fogDensity: config.graphics.fogDensity,
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

    this.director = new CameraDirector(this.camera, this.terrain, config);
    this.nametags = new Nametags(nametagContainer, config.chat.showNametags ? config.chat.maxNametags : 0);

    this.resize();
  }

  // ------------------------------------------------------------------ ciclo

  applySnapshot(snapshot: Snapshot): void {
    const units = new Float32Array(snapshot.units);
    this.unitsRenderer.update(units, snapshot.groups);

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
    this.terrainRenderer.update(dt, this.elapsed);
    this.effects.update(dt);
    // El cielo viaja con la cámara para que nunca se alcance su borde.
    this.sky.position.copy(this.camera.position);
    this.renderer.render(this.scene, this.camera);
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
      this.renderer.setPixelRatio(this.currentPixelRatio);
    } else if (this.fps > target * 0.97 && this.currentPixelRatio < this.basePixelRatio) {
      this.currentPixelRatio = Math.min(this.basePixelRatio, this.currentPixelRatio + 0.1);
      this.renderer.setPixelRatio(this.currentPixelRatio);
    }
  }

  // ----------------------------------------------------------------- ajustes

  resize(): void {
    const width = this.canvas.clientWidth || window.innerWidth;
    const height = this.canvas.clientHeight || window.innerHeight;
    this.renderer.setSize(width, height, false);
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
    this.renderer.setClearColor(this.palette.fog, 1);
  }

  setQuality(quality: GameConfig['graphics']['quality']): void {
    this.config.graphics.quality = quality;
    const ratioCap = { low: 0.85, medium: 1.1, high: 1.5, ultra: 2 }[quality];
    this.basePixelRatio = Math.min(window.devicePixelRatio || 1, ratioCap, this.config.graphics.pixelRatioCap);
    this.currentPixelRatio = this.basePixelRatio;
    this.renderer.setPixelRatio(this.currentPixelRatio);
    this.unitsRenderer.setShadowsEnabled(quality !== 'low' && this.config.graphics.shadows);
    this.effects.setGore(quality !== 'low' && this.config.graphics.gore);
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

  dispose(): void {
    this.unitsRenderer.dispose();
    this.terrainRenderer.dispose();
    this.effects.dispose();
    this.nametags.dispose();
    this.skyMaterial.dispose();
    this.sky.geometry.dispose();
    this.renderer.dispose();
  }
}
