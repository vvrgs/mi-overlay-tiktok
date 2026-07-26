/**
 * Previsualizador de unidades.
 *
 * Muestra un ejemplar de cada arquetipo, a tamaño grande y sobre fondo neutro,
 * usando exactamente el mismo renderer y los mismos shaders que la batalla. Es
 * la forma de juzgar un modelo o una animación sin tener que cazar el momento
 * en que la cámara cinematográfica pasa cerca de un soldado.
 *
 * Abrir en `models.html`.
 */

import * as THREE from 'three';
import { buildArchetypes } from '../game/archetypes';
import { loadConfig } from '../shared/config';
import { PostProcessing } from '../render/post';
import { UNIT_STRIDE, type UnitGroup } from '../sim/protocol';
import { UnitsRenderer } from '../render/units-renderer';

const SPACING = 2.1;

async function boot(): Promise<void> {
  const config = await loadConfig();
  const canvas = document.getElementById('scene') as HTMLCanvasElement;
  const labels = document.getElementById('labels') as HTMLElement;

  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(38, 1, 0.1, 200);

  const archetypes = buildArchetypes(config);
  const units = new UnitsRenderer({
    capacityPerGroup: 4,
    detail: 'high',
    archetypes: archetypes.map((a) => ({ key: a.key, mesh: a.mesh, stackable: a.stackable })),
    teamColors: {
      red: { color: new THREE.Color(config.teams.red.color), dark: new THREE.Color(config.teams.red.colorDark) },
      blue: { color: new THREE.Color(config.teams.blue.color), dark: new THREE.Color(config.teams.blue.colorDark) },
    },
    shadows: true,
    fogColor: new THREE.Color('#10141c'),
    // Sin niebla: aquí interesa el modelo, no la atmósfera.
    fogDensity: 0,
    lodDistance: 1000,
    textureDistance: 1000,
  });
  units.setLighting(
    new THREE.Color('#9dc0ea'),
    new THREE.Color('#3a3630'),
    new THREE.Color('#fff2d8'),
    new THREE.Vector3(0.5, 0.75, 0.42),
  );
  scene.add(units.root);

  // Suelo neutro para que las sombras de contacto tengan dónde caer.
  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(200, 200).rotateX(-Math.PI / 2),
    new THREE.MeshBasicMaterial({ color: new THREE.Color('#20262f') }),
  );
  scene.add(ground);

  const post = new PostProcessing({
    enabled: true,
    bloomThreshold: 1.15,
    bloomIntensity: 0.6,
    vignette: 0.4,
    saturation: 1.1,
    contrast: 1.04,
    exposure: 1.05,
    sharpen: 0.2,
  });

  // Una instancia por arquetipo, alineadas en fila.
  const source = new Float32Array(archetypes.length * UNIT_STRIDE);
  const groups: UnitGroup[] = archetypes.map((_, index) => ({ archetype: index, team: 0, start: index, count: 1 }));

  // El hueco entre modelos crece con su tamaño: si no, el dragón se come al resto.
  const offsets: number[] = [];
  let cursor = 0;
  archetypes.forEach((archetype, index) => {
    if (index > 0) cursor += SPACING * (archetypes[index - 1].scale + archetype.scale) * 0.5;
    offsets.push(cursor);
  });
  const totalWidth = cursor;

  archetypes.forEach((archetype, index) => {
    const o = index * UNIT_STRIDE;
    source[o] = offsets[index] - totalWidth / 2;
    source[o + 1] = 0;
    source[o + 2] = 0;
    source[o + 3] = Math.PI; // de cara a la cámara
    source[o + 4] = archetype.scale;
    source[o + 5] = index * 0.9;
    source[o + 6] = 0;
    source[o + 7] = 1;
  });

  archetypes.forEach((archetype, index) => {
    const tag = document.createElement('div');
    tag.className = 'tag';
    tag.textContent = config.units[archetype.key]?.label ?? archetype.key;
    tag.dataset.index = String(index);
    labels.appendChild(tag);
  });

  // --- Controles de órbita mínimos ---
  let yaw = 0;
  let pitch = 0.22;
  let distance = totalWidth * 0.8 + 8;
  let dragging = false;
  let lastX = 0;
  let lastY = 0;
  let team: 0 | 1 = 0;
  let animate = true;
  // Unidad enfocada: la cámara orbita a su alrededor y la distancia se ajusta a
  // su tamaño, para que un soldado y un dragón se encuadren igual de bien.
  let focusIndex = 0;

  canvas.addEventListener('pointerdown', (ev) => {
    dragging = true;
    lastX = ev.clientX;
    lastY = ev.clientY;
    canvas.setPointerCapture(ev.pointerId);
  });
  canvas.addEventListener('pointerup', () => (dragging = false));
  canvas.addEventListener('pointermove', (ev) => {
    if (!dragging) return;
    yaw -= (ev.clientX - lastX) * 0.006;
    pitch = Math.max(-0.2, Math.min(1.2, pitch + (ev.clientY - lastY) * 0.004));
    lastX = ev.clientX;
    lastY = ev.clientY;
  });
  canvas.addEventListener('wheel', (ev) => {
    distance = Math.max(3, Math.min(60, distance + ev.deltaY * 0.02));
    ev.preventDefault();
  }, { passive: false });
  function focusOn(index: number): void {
    focusIndex = (index + archetypes.length) % archetypes.length;
    distance = 3.2 + archetypes[focusIndex].scale * 3.4;
  }

  window.addEventListener('keydown', (ev) => {
    if (ev.code === 'Space') {
      team = team === 0 ? 1 : 0;
      for (const group of groups) group.team = team;
      ev.preventDefault();
    }
    if (ev.key.toLowerCase() === 'a') animate = !animate;
    if (ev.key === 'ArrowRight') focusOn(focusIndex + 1);
    if (ev.key === 'ArrowLeft') focusOn(focusIndex - 1);
    // 0 = vista general de toda la fila.
    if (ev.key === '0') {
      focusIndex = -1;
      distance = totalWidth * 0.8 + 8;
    }
  });
  focusOn(0);

  function resize(): void {
    const width = canvas.clientWidth || window.innerWidth;
    const height = canvas.clientHeight || window.innerHeight;
    renderer.setSize(width, height, false);
    post.setSize(width, height, renderer.getPixelRatio());
    camera.aspect = width / Math.max(1, height);
    camera.updateProjectionMatrix();
  }
  window.addEventListener('resize', resize);
  resize();

  const projected = new THREE.Vector3();
  let last = performance.now();
  let phase = 0;

  function frame(now: number): void {
    const dt = Math.min(0.05, (now - last) / 1000);
    last = now;
    if (animate) phase += dt;

    // Ciclo de estados: marcha, golpe y de vuelta, para ver toda la animación.
    const cycle = (phase * 0.5) % 3;
    for (let i = 0; i < archetypes.length; i++) {
      const o = i * UNIT_STRIDE;
      source[o + 5] = phase * 2.4 + i * 0.9;
      source[o + 6] = cycle < 2 ? Math.min(0.999, 0.85) : 1 + Math.abs(Math.sin(phase * 6)) * 0.99;
    }

    const centerX = focusIndex >= 0 ? source[focusIndex * UNIT_STRIDE] : 0;
    const centerY = focusIndex >= 0 ? archetypes[focusIndex].scale * 0.95 : 1.0;
    camera.position.set(
      centerX + Math.sin(yaw) * distance * Math.cos(pitch),
      centerY + Math.sin(pitch) * distance,
      Math.cos(yaw) * distance * Math.cos(pitch),
    );
    camera.lookAt(centerX, centerY, 0);

    units.update(source, groups, camera.position);
    post.update();
    post.render(renderer, scene, camera);

    // Las etiquetas siguen a cada modelo en pantalla.
    const size = renderer.getSize(new THREE.Vector2());
    for (const node of labels.children) {
      const tag = node as HTMLElement;
      const index = Number(tag.dataset.index);
      projected.set(source[index * UNIT_STRIDE], 0, source[index * UNIT_STRIDE + 2]).project(camera);
      tag.style.left = `${(projected.x * 0.5 + 0.5) * size.x}px`;
      tag.style.top = `${(-projected.y * 0.5 + 0.5) * size.y + 8}px`;
      tag.style.opacity = projected.z > 1 ? '0' : '1';
    }

    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
}

void boot().catch((err) => {
  console.error('[models] Fallo al arrancar:', err);
  document.body.textContent = `No se pudo iniciar el previsualizador: ${String(err)}`;
});
