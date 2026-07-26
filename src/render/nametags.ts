/**
 * Etiquetas de los campeones (los viewers que entraron por el chat).
 *
 * Se dibujan como HTML por encima del canvas, no como sprites: el texto queda
 * nítido a cualquier resolución, se estiliza con CSS y no cuesta draw calls.
 * Solo se muestran los N campeones más cercanos a la cámara para no tapar la
 * batalla.
 */

import * as THREE from 'three';
import type { TeamId } from '../shared/config';

export interface ChampionView {
  championId: number;
  x: number;
  y: number;
  z: number;
  hp: number;
  kills: number;
}

export interface ChampionInfo {
  nickname: string;
  team: TeamId;
}

interface TagElement {
  root: HTMLDivElement;
  label: HTMLSpanElement;
  kills: HTMLSpanElement;
  bar: HTMLDivElement;
  inUse: boolean;
}

export class Nametags {
  private pool: TagElement[] = [];
  private projected = new THREE.Vector3();

  constructor(private container: HTMLElement, private maxTags: number) {}

  private acquire(index: number): TagElement {
    let tag = this.pool[index];
    if (tag) return tag;

    const root = document.createElement('div');
    root.className = 'nametag';
    const label = document.createElement('span');
    label.className = 'nametag__name';
    const kills = document.createElement('span');
    kills.className = 'nametag__kills';
    const barTrack = document.createElement('div');
    barTrack.className = 'nametag__bar';
    const bar = document.createElement('div');
    bar.className = 'nametag__fill';
    barTrack.appendChild(bar);
    root.append(label, kills, barTrack);
    this.container.appendChild(root);

    tag = { root, label, kills, bar, inUse: false };
    this.pool[index] = tag;
    return tag;
  }

  update(
    champions: ChampionView[],
    lookup: Map<number, ChampionInfo>,
    camera: THREE.PerspectiveCamera,
    width: number,
    height: number,
    maxDistance: number,
  ): THREE.Vector3 | null {
    if (this.maxTags <= 0 || champions.length === 0) {
      this.hideFrom(0);
      return null;
    }

    // Orden por cercanía a la cámara: se etiqueta lo que de verdad se ve.
    const scored = champions
      .map((champion) => ({
        champion,
        distance: camera.position.distanceTo(new THREE.Vector3(champion.x, champion.y, champion.z)),
      }))
      .filter((entry) => entry.distance <= maxDistance)
      .sort((a, b) => a.distance - b.distance)
      .slice(0, this.maxTags);

    let used = 0;
    let closest: THREE.Vector3 | null = null;

    for (const { champion, distance } of scored) {
      const info = lookup.get(champion.championId);
      if (!info) continue;

      this.projected.set(champion.x, champion.y + 2.6, champion.z);
      this.projected.project(camera);
      // Detrás de la cámara o fuera de pantalla: no se dibuja.
      if (this.projected.z > 1 || Math.abs(this.projected.x) > 1.1 || Math.abs(this.projected.y) > 1.1) continue;

      const tag = this.acquire(used);
      const screenX = (this.projected.x * 0.5 + 0.5) * width;
      const screenY = (-this.projected.y * 0.5 + 0.5) * height;

      tag.root.style.transform = `translate(-50%, -100%) translate(${screenX.toFixed(1)}px, ${screenY.toFixed(1)}px)`;
      tag.root.style.opacity = String(Math.max(0.25, 1 - distance / maxDistance));
      tag.root.dataset.team = info.team;
      if (tag.label.textContent !== info.nickname) tag.label.textContent = info.nickname;
      const killsText = champion.kills > 0 ? `⚔ ${champion.kills.toLocaleString('es-MX')}` : '';
      if (tag.kills.textContent !== killsText) tag.kills.textContent = killsText;
      tag.bar.style.width = `${Math.max(0, Math.min(1, champion.hp)) * 100}%`;
      if (!tag.inUse) {
        tag.root.style.display = '';
        tag.inUse = true;
      }

      if (!closest) closest = new THREE.Vector3(champion.x, champion.y, champion.z);
      used++;
    }

    this.hideFrom(used);
    return closest;
  }

  private hideFrom(index: number): void {
    for (let i = index; i < this.pool.length; i++) {
      const tag = this.pool[i];
      if (tag?.inUse) {
        tag.root.style.display = 'none';
        tag.inUse = false;
      }
    }
  }

  setMaxTags(value: number): void {
    this.maxTags = Math.max(0, value);
  }

  clear(): void {
    this.hideFrom(0);
  }

  dispose(): void {
    for (const tag of this.pool) tag.root.remove();
    this.pool.length = 0;
  }
}
