/**
 * Persistencia de rankings.
 *
 * Un archivo JSON con escritura atómica (se escribe a un temporal y se renombra)
 * y guardado con retardo. Suficiente y sobrado para los volúmenes de un directo,
 * y sin dependencias nativas que compilar en la máquina del streamer.
 */

import { mkdir, readFile, rename, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';

const SAVE_DEBOUNCE_MS = 1500;
const MAX_DONORS = 2000;
const MAX_WARRIORS = 2000;
const MAX_MATCHES = 500;

function emptyState() {
  return { donors: [], warriors: [], countries: [], matches: [] };
}

export class Store {
  #file;
  #state = emptyState();
  #saveTimer = null;
  #saving = false;
  #dirty = false;

  constructor(dataDir) {
    this.#file = join(dataDir, 'leaderboards.json');
  }

  async load() {
    try {
      const raw = await readFile(this.#file, 'utf8');
      this.#state = { ...emptyState(), ...JSON.parse(raw) };
    } catch (err) {
      if (err.code !== 'ENOENT') console.warn('[store] No se pudo leer el histórico:', err.message);
      this.#state = emptyState();
    }
    return this.#state;
  }

  get state() {
    return this.#state;
  }

  /** Suma monedas y regalos a cada donante del lote. */
  trackDonations(donations = []) {
    for (const donation of donations) {
      if (!donation?.uniqueId) continue;
      let entry = this.#state.donors.find((d) => d.uniqueId === donation.uniqueId);
      if (!entry) {
        entry = { uniqueId: donation.uniqueId, nickname: donation.nickname ?? donation.uniqueId, coins: 0, gifts: 0 };
        this.#state.donors.push(entry);
      }
      entry.nickname = donation.nickname ?? entry.nickname;
      entry.coins += Number(donation.coins) || 0;
      entry.gifts += Number(donation.gifts) || 0;
    }
    this.#state.donors.sort((a, b) => b.coins - a.coins);
    this.#state.donors.length = Math.min(this.#state.donors.length, MAX_DONORS);
    this.#touch();
  }

  /** Guarda la mejor marca de supervivencia y acumula bajas. */
  trackWarriors(warriors = []) {
    for (const warrior of warriors) {
      if (!warrior?.uniqueId) continue;
      let entry = this.#state.warriors.find((w) => w.uniqueId === warrior.uniqueId);
      if (!entry) {
        entry = { uniqueId: warrior.uniqueId, nickname: warrior.nickname ?? warrior.uniqueId, bestSurvival: 0, kills: 0 };
        this.#state.warriors.push(entry);
      }
      entry.nickname = warrior.nickname ?? entry.nickname;
      entry.bestSurvival = Math.max(entry.bestSurvival, Number(warrior.bestSurvival) || 0);
      entry.kills += Number(warrior.kills) || 0;
    }
    this.#state.warriors.sort((a, b) => b.bestSurvival - a.bestSurvival);
    this.#state.warriors.length = Math.min(this.#state.warriors.length, MAX_WARRIORS);
    this.#touch();
  }

  trackMatch(match) {
    if (!match) return;
    this.#state.matches.unshift(match);
    this.#state.matches.length = Math.min(this.#state.matches.length, MAX_MATCHES);

    for (const [code, won] of [
      [match.redCountry, match.winner === 'red'],
      [match.blueCountry, match.winner === 'blue'],
    ]) {
      if (!code) continue;
      let entry = this.#state.countries.find((c) => c.code === code);
      if (!entry) {
        entry = { code, wins: 0, losses: 0 };
        this.#state.countries.push(entry);
      }
      if (match.winner === 'draw') continue;
      if (won) entry.wins++;
      else entry.losses++;
    }
    this.#touch();
  }

  #touch() {
    this.#dirty = true;
    if (this.#saveTimer) return;
    this.#saveTimer = setTimeout(() => {
      this.#saveTimer = null;
      void this.save();
    }, SAVE_DEBOUNCE_MS);
  }

  /** Escritura atómica: temporal + rename, así un corte no deja el archivo a medias. */
  async save() {
    if (this.#saving || !this.#dirty) return;
    this.#saving = true;
    this.#dirty = false;
    try {
      await mkdir(dirname(this.#file), { recursive: true });
      const temp = `${this.#file}.tmp`;
      await writeFile(temp, JSON.stringify(this.#state, null, 2), 'utf8');
      await rename(temp, this.#file);
    } catch (err) {
      console.error('[store] Error al guardar:', err.message);
      this.#dirty = true;
    } finally {
      this.#saving = false;
    }
  }

  async flush() {
    if (this.#saveTimer) {
      clearTimeout(this.#saveTimer);
      this.#saveTimer = null;
    }
    await this.save();
  }
}
