/**
 * Rankings y persistencia.
 *
 * Acumula en memoria durante el directo y sincroniza con el servidor en lotes
 * cada pocos segundos (mandar una petición por regalo sería absurdo). Si el
 * servidor no está levantado, todo cae a localStorage para que el overlay
 * funcione igual en solitario dentro de OBS.
 */

export interface DonorEntry {
  uniqueId: string;
  nickname: string;
  coins: number;
  gifts: number;
}

export interface WarriorEntry {
  uniqueId: string;
  nickname: string;
  bestSurvival: number;
  kills: number;
}

export interface CountryEntry {
  code: string;
  wins: number;
  losses: number;
}

export interface MatchRecord {
  round: number;
  winner: 'red' | 'blue' | 'draw';
  redCountry: string;
  blueCountry: string;
  redSoldiers: number;
  blueSoldiers: number;
  durationSeconds: number;
  endedAt: number;
}

export interface Leaderboards {
  donors: DonorEntry[];
  warriors: WarriorEntry[];
  countries: CountryEntry[];
  matches: MatchRecord[];
}

const LOCAL_KEY = 'guerra-de-naciones:leaderboards';
const SYNC_INTERVAL_MS = 8000;

function emptyBoards(): Leaderboards {
  return { donors: [], warriors: [], countries: [], matches: [] };
}

export class RankingStore {
  /** Acumulado del directo actual (se reinicia al recargar el overlay). */
  private sessionDonors = new Map<string, DonorEntry>();
  private sessionWarriors = new Map<string, WarriorEntry>();
  private pendingDonations = new Map<string, DonorEntry>();
  private pendingWarriors = new Map<string, WarriorEntry>();
  private allTime: Leaderboards = emptyBoards();
  private syncTimer: ReturnType<typeof setInterval> | null = null;
  private online = false;

  constructor(private apiBase: string, private enabled: boolean, private localFallback: boolean) {}

  async start(): Promise<void> {
    if (!this.enabled) return;
    await this.pull();
    this.syncTimer = setInterval(() => void this.flush(), SYNC_INTERVAL_MS);
  }

  stop(): void {
    if (this.syncTimer) clearInterval(this.syncTimer);
    this.syncTimer = null;
    void this.flush();
  }

  get isOnline(): boolean {
    return this.online;
  }

  // ------------------------------------------------------------- acumulación

  recordDonation(uniqueId: string, nickname: string, coins: number): void {
    if (coins <= 0) return;
    for (const map of [this.sessionDonors, this.pendingDonations]) {
      const entry = map.get(uniqueId) ?? { uniqueId, nickname, coins: 0, gifts: 0 };
      entry.nickname = nickname;
      entry.coins += coins;
      entry.gifts += 1;
      map.set(uniqueId, entry);
    }
  }

  recordWarrior(uniqueId: string, nickname: string, survivalSeconds: number, kills: number): void {
    for (const map of [this.sessionWarriors, this.pendingWarriors]) {
      const entry = map.get(uniqueId) ?? { uniqueId, nickname, bestSurvival: 0, kills: 0 };
      entry.nickname = nickname;
      entry.bestSurvival = Math.max(entry.bestSurvival, survivalSeconds);
      entry.kills += kills;
      map.set(uniqueId, entry);
    }
  }

  async recordMatch(record: MatchRecord): Promise<void> {
    this.allTime.matches.unshift(record);
    this.allTime.matches = this.allTime.matches.slice(0, 200);

    for (const [code, won] of [
      [record.redCountry, record.winner === 'red'],
      [record.blueCountry, record.winner === 'blue'],
    ] as Array<[string, boolean]>) {
      let entry = this.allTime.countries.find((c) => c.code === code);
      if (!entry) {
        entry = { code, wins: 0, losses: 0 };
        this.allTime.countries.push(entry);
      }
      if (record.winner === 'draw') continue;
      if (won) entry.wins++;
      else entry.losses++;
    }

    this.saveLocal();
    await this.post('match', record);
  }

  // ----------------------------------------------------------------- lecturas

  topDonors(limit: number): DonorEntry[] {
    return [...this.sessionDonors.values()].sort((a, b) => b.coins - a.coins).slice(0, limit);
  }

  topDonorsAllTime(limit: number): DonorEntry[] {
    return [...this.allTime.donors].sort((a, b) => b.coins - a.coins).slice(0, limit);
  }

  topWarriors(limit: number): WarriorEntry[] {
    return [...this.sessionWarriors.values()].sort((a, b) => b.bestSurvival - a.bestSurvival).slice(0, limit);
  }

  countryStandings(): CountryEntry[] {
    return [...this.allTime.countries].sort((a, b) => b.wins - a.wins);
  }

  recentMatches(limit: number): MatchRecord[] {
    return this.allTime.matches.slice(0, limit);
  }

  // ------------------------------------------------------------ sincronización

  private async pull(): Promise<void> {
    try {
      const res = await fetch(`${this.apiBase}/leaderboard`, { cache: 'no-store' });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = (await res.json()) as Partial<Leaderboards>;
      this.allTime = { ...emptyBoards(), ...data };
      this.online = true;
      return;
    } catch {
      this.online = false;
    }
    if (this.localFallback) this.loadLocal();
  }

  private async flush(): Promise<void> {
    if (this.pendingDonations.size === 0 && this.pendingWarriors.size === 0) return;
    const payload = {
      donations: [...this.pendingDonations.values()],
      warriors: [...this.pendingWarriors.values()],
    };
    this.pendingDonations.clear();
    this.pendingWarriors.clear();

    // Mezcla local para que el histórico se vea al instante aunque no haya servidor.
    for (const donation of payload.donations) {
      const entry = this.allTime.donors.find((d) => d.uniqueId === donation.uniqueId);
      if (entry) {
        entry.coins += donation.coins;
        entry.gifts += donation.gifts;
        entry.nickname = donation.nickname;
      } else {
        this.allTime.donors.push({ ...donation });
      }
    }
    for (const warrior of payload.warriors) {
      const entry = this.allTime.warriors.find((w) => w.uniqueId === warrior.uniqueId);
      if (entry) {
        entry.bestSurvival = Math.max(entry.bestSurvival, warrior.bestSurvival);
        entry.kills += warrior.kills;
        entry.nickname = warrior.nickname;
      } else {
        this.allTime.warriors.push({ ...warrior });
      }
    }
    this.saveLocal();
    await this.post('track', payload);
  }

  private async post(path: string, body: unknown): Promise<void> {
    if (!this.enabled) return;
    try {
      const res = await fetch(`${this.apiBase}/${path}`, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(body),
      });
      this.online = res.ok;
    } catch {
      this.online = false;
    }
  }

  private saveLocal(): void {
    if (!this.localFallback) return;
    try {
      localStorage.setItem(
        LOCAL_KEY,
        JSON.stringify({
          donors: this.allTime.donors.slice(0, 300),
          warriors: this.allTime.warriors.slice(0, 300),
          countries: this.allTime.countries,
          matches: this.allTime.matches.slice(0, 100),
        }),
      );
    } catch {
      /* almacenamiento lleno o bloqueado: se ignora */
    }
  }

  private loadLocal(): void {
    try {
      const raw = localStorage.getItem(LOCAL_KEY);
      if (!raw) return;
      this.allTime = { ...emptyBoards(), ...(JSON.parse(raw) as Partial<Leaderboards>) };
    } catch {
      /* datos corruptos: se empieza de cero */
    }
  }
}
