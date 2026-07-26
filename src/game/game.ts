/**
 * Orquestador del juego.
 *
 * Une todo: escucha el bus de eventos, manda órdenes al worker de simulación,
 * lleva la serie de rondas, la furia y los campeones, y alimenta la HUD.
 * Es el único sitio donde vive la lógica de "qué pasa cuando alguien regala".
 */

import type { EventBus } from '../events/bus';
import type { EventSimulator } from '../events/simulator';
import type { TransportManager } from '../events/transports';
import type { ChatEvent, ControlCommand, GiftEvent, LikeEvent, LiveEvent, LiveUser } from '../events/types';
import type { GameRenderer } from '../render/renderer';
import type { GameConfig, TeamId, UltimateDef } from '../shared/config';
import { clamp, createRng, normalizeText, type Rng } from '../shared/math';
import type { MainToWorker, Snapshot } from '../sim/protocol';
import type { Hud } from '../ui/hud';
import { buildArchetypes } from './archetypes';
import { SEASON_PROFILES, SEASONS, WEATHER_PROFILES, type Season, type WeatherKind } from '../render/weather';
import { GiftResolver } from './gifts';
import type { RankingStore } from './ranking';

export type Phase = 'lobby' | 'countdown' | 'battle' | 'roundEnd' | 'seriesEnd';

interface ChampionRecord {
  uniqueId: string;
  nickname: string;
  team: TeamId;
  joinedAt: number;
}

export interface GameDeps {
  config: GameConfig;
  bus: EventBus;
  renderer: GameRenderer;
  hud: Hud;
  ranking: RankingStore;
  transports: TransportManager;
  simulator: EventSimulator;
}

export class Game {
  private config: GameConfig;
  private bus: EventBus;
  private renderer: GameRenderer;
  private hud: Hud;
  private ranking: RankingStore;
  private transports: TransportManager;
  private simulator: EventSimulator;

  private worker: Worker;
  private gifts: GiftResolver;
  private rng: Rng;

  phase: Phase = 'lobby';
  round = 1;
  paused = false;
  private wins: Record<TeamId, number> = { red: 0, blue: 0 };
  private countries: Record<TeamId, string>;
  private rage: Record<TeamId, number> = { red: 0, blue: 0 };
  private lastCast: Record<TeamId, number> = { red: -999, blue: -999 };
  private soldiers: Record<TeamId, number> = { red: 0, blue: 0 };
  private entities: Record<TeamId, number> = { red: 0, blue: 0 };

  private champions = new Map<number, ChampionRecord>();
  private championsByUser = new Map<string, number>();
  private championCount: Record<TeamId, number> = { red: 0, blue: 0 };
  private nextChampionId = 1;
  private userTeam = new Map<string, TeamId>();
  private lastJoinAt = new Map<string, number>();

  private phaseTimer = 0;
  private roundElapsed = 0;
  private clock = 0;
  private lastCountdownShown = -1;
  private suddenDeath = false;
  private statusTimer = 0;
  private donorTimer = 0;
  private roundSeed = 1;
  private seasonIndex = 0;

  constructor(deps: GameDeps) {
    this.config = deps.config;
    this.bus = deps.bus;
    this.renderer = deps.renderer;
    this.hud = deps.hud;
    this.ranking = deps.ranking;
    this.transports = deps.transports;
    this.simulator = deps.simulator;

    this.gifts = new GiftResolver(this.config);
    this.rng = createRng(this.config.simulator.seed + 17);
    this.countries = { red: this.config.teams.red.country, blue: this.config.teams.blue.country };
    this.roundSeed = this.config.simulator.seed;
    this.seasonIndex = Math.max(0, SEASONS.indexOf(this.config.world?.season ?? 'summer'));

    this.worker = new Worker(new URL('../sim/worker.ts', import.meta.url), { type: 'module' });
    this.worker.onmessage = (ev: MessageEvent) => this.onWorkerMessage(ev);
    this.send({ t: 'init', settings: this.buildSettings() });

    this.bindEvents();
    this.hud.setCountries(this.countries.red, this.countries.blue);
    this.hud.setWins(0, 0, this.config.series.target);
    this.hud.setRound(this.round);
  }

  private buildSettings() {
    const b = this.config.battle;
    return {
      seed: this.roundSeed,
      fieldWidth: b.fieldWidth,
      fieldDepth: b.fieldDepth,
      riverWidth: b.riverWidth,
      riverSlowFactor: b.riverSlowFactor,
      renderCapPerTeam: b.renderCapPerTeam,
      deployPerSecond: b.deployPerSecond,
      collisionRadius: b.collisionRadius,
      separationStrength: b.separationStrength,
      cohesionStrength: b.cohesionStrength,
      targetRescanTicks: b.targetRescanTicks,
      tickRate: b.tickRate,
      difficulty: b.difficulty,
      damageGlobalMultiplier: b.damageGlobalMultiplier,
      autoBalance: b.autoBalance,
      archetypes: buildArchetypes(this.config),
    };
  }

  private send(message: MainToWorker, transfer?: Transferable[]): void {
    if (transfer) this.worker.postMessage(message, transfer);
    else this.worker.postMessage(message);
  }

  // ------------------------------------------------------------------ eventos

  private bindEvents(): void {
    this.bus.on('chat', (event) => this.onChat(event as ChatEvent));
    this.bus.on('gift', (event) => this.onGift(event as GiftEvent));
    this.bus.on('like', (event) => this.onLike(event as LikeEvent));
    this.bus.on('follow', (event) => this.onSimple(event, 'follow'));
    this.bus.on('share', (event) => this.onSimple(event, 'share'));
    this.bus.on('subscribe', (event) => this.onSimple(event, 'subscribe'));
    this.bus.on('join', (event) => this.onSimple(event, 'join'));
    this.bus.onCommand((command) => this.onCommand(command));
  }

  /** Decide en qué bando juega un usuario. */
  private teamFor(user: LiveUser, hint?: string, comment?: string): TeamId {
    const known = this.userTeam.get(user.uniqueId);
    if (known) return known;

    const candidates = [hint, comment].filter(Boolean) as string[];
    for (const raw of candidates) {
      const text = ` ${normalizeText(raw)} `;
      for (const team of ['red', 'blue'] as TeamId[]) {
        const teamConfig = this.config.teams[team];
        const country = this.config.countries[this.countries[team]];
        const keywords = [
          ...teamConfig.keywords,
          teamConfig.shortName,
          country?.name ?? '',
          country?.demonym ?? '',
        ].filter(Boolean);
        if (keywords.some((keyword) => text.includes(` ${normalizeText(keyword)} `))) {
          this.userTeam.set(user.uniqueId, team);
          return team;
        }
      }
    }

    // Sin palabra clave: entra al bando con menos gente, que además equilibra la partida.
    const fallback: TeamId = this.championCount.red <= this.championCount.blue ? 'red' : 'blue';
    this.userTeam.set(user.uniqueId, fallback);
    return fallback;
  }

  private onChat(event: ChatEvent): void {
    if (!this.config.chat.joinEnabled) return;

    const text = ` ${normalizeText(event.comment)} `;
    const explicit = (['red', 'blue'] as TeamId[]).find((team) =>
      this.config.teams[team].keywords.some((keyword) => text.includes(` ${normalizeText(keyword)} `)),
    );
    if (!explicit && !this.config.chat.autoAssignUnknown) return;

    // Cambiar de bando siempre es posible: mantiene viva la pelea del chat.
    if (explicit) this.userTeam.set(event.user.uniqueId, explicit);
    const team = this.teamFor(event.user, event.teamHint, event.comment);

    const last = this.lastJoinAt.get(event.user.uniqueId) ?? -Infinity;
    if (this.clock - last < this.config.chat.championCooldownSeconds) return;
    if (this.championsByUser.has(event.user.uniqueId)) return;
    if (this.championCount[team] >= this.config.chat.maxChampionsPerTeam) return;
    if (this.phase !== 'battle' && this.phase !== 'countdown') return;

    this.lastJoinAt.set(event.user.uniqueId, this.clock);
    const championId = this.nextChampionId++;
    this.champions.set(championId, {
      uniqueId: event.user.uniqueId,
      nickname: event.user.nickname,
      team,
      joinedAt: this.clock,
    });
    this.championsByUser.set(event.user.uniqueId, championId);
    this.championCount[team]++;
    this.send({ t: 'addChampion', team, championId });

    if (this.config.chat.announceJoins) {
      this.hud.pushFeed({ user: event.user.nickname, text: `entró con ${this.config.teams[team].shortName}`, team });
    }
  }

  private onGift(event: GiftEvent): void {
    const reward = this.gifts.resolve(event);
    const team = this.teamFor(event.user, event.teamHint, event.giftName);

    this.send({
      t: 'addTroops',
      team,
      troops: reward.troops,
      unit: reward.unit,
      ...(reward.count > 0 ? { count: reward.count } : {}),
    });

    this.addRage(team, reward.rage);
    this.ranking.recordDonation(event.user.uniqueId, event.user.nickname, reward.coins);

    const big = reward.coins >= 500 || reward.count > 0;
    const unitLabel = this.config.units[reward.unit]?.label ?? '';
    const detail = reward.count > 0 ? `${reward.label} → ${reward.count}× ${unitLabel}` : reward.label;
    this.hud.pushFeed({
      user: event.user.nickname,
      text: detail,
      amount: `+${reward.troops.toLocaleString(this.config.hud.counterFormat)}`,
      team,
      big,
    });

    if (reward.ultimate) this.castUltimate(team, reward.ultimate, true);
  }

  private onLike(event: LikeEvent): void {
    if (!this.config.likes.enabled) return;
    const team = this.teamFor(event.user, event.teamHint);
    this.addRage(team, event.likeCount * this.config.likes.ragePerLike);
    const troops = Math.round(event.likeCount * this.config.likes.troopsPerLike);
    if (troops > 0) this.send({ t: 'addTroops', team, troops, unit: 'soldier' });
  }

  private onSimple(event: LiveEvent, kind: 'follow' | 'share' | 'join' | 'subscribe'): void {
    const reward = this.config.events.rewards?.[kind];
    if (!reward) return;
    const team = this.teamFor(event.user, event.teamHint);
    if (reward.troops) this.send({ t: 'addTroops', team, troops: reward.troops, unit: reward.unit ?? 'soldier' });
    if (reward.rage) this.addRage(team, reward.rage);
    if (kind === 'subscribe' || kind === 'share') {
      const labels = { subscribe: 'se suscribió', share: 'compartió el live', follow: 'te siguió', join: 'se unió' };
      this.hud.pushFeed({ user: event.user.nickname, text: labels[kind], team, big: kind === 'subscribe' });
    }
  }

  private onCommand(command: ControlCommand): void {
    switch (command.action) {
      case 'start':
        if (this.phase === 'lobby' || this.phase === 'seriesEnd') this.startSeries();
        break;
      case 'pause':
        this.setPaused(true);
        break;
      case 'resume':
        this.setPaused(false);
        break;
      case 'reset':
        this.startRound();
        break;
      case 'skipRound':
        if (this.phase === 'battle') this.endRound(this.soldiers.red >= this.soldiers.blue ? 'red' : 'blue');
        break;
      case 'resetSeries':
        this.startSeries();
        break;
      case 'reloadConfig':
        window.location.reload();
        break;
      case 'setCountry':
        this.countries[command.team] = command.country;
        this.config.teams[command.team].country = command.country;
        this.hud.setCountries(this.countries.red, this.countries.blue);
        break;
      case 'setDifficulty':
        this.config.battle.difficulty = command.value;
        this.send({ t: 'setDifficulty', value: command.value });
        break;
      case 'addTroops':
        this.send({ t: 'addTroops', team: command.team, troops: command.troops, unit: command.unit ?? 'soldier' });
        this.hud.pushFeed({ text: `Panel: +${command.troops.toLocaleString('es-MX')} a ${this.config.teams[command.team].shortName}`, team: command.team });
        break;
      case 'castUltimate':
        this.castUltimate(command.team, command.ultimate, true);
        break;
      case 'setRage':
        this.rage[command.team] = clamp(command.value, 0, this.config.likes.rageMax);
        break;
      case 'ban':
        this.bus.ban(command.uniqueId);
        break;
      case 'unban':
        this.bus.unban(command.uniqueId);
        break;
      case 'setSimulator':
        this.simulator.toggle(command.enabled);
        break;
      case 'setCamera':
        this.renderer.director.setMode(command.mode as GameConfig['camera']['mode']);
        break;
      case 'tuneCamera': {
        const { type: _t, action: _a, ...patch } = command;
        this.renderer.director.tune(patch);
        break;
      }
      case 'setGraphics':
        this.renderer.setQuality(command.quality as GameConfig['graphics']['quality']);
        break;
      case 'tuneGraphics': {
        const { type: _t, action: _a, ...patch } = command;
        this.renderer.tuneGraphics(patch);
        break;
      }
      case 'setSeason': {
        const index = SEASONS.indexOf(command.season as Season);
        if (index >= 0) this.seasonIndex = index;
        this.renderer.setSeason(command.season as Season);
        this.announceAtmosphere();
        break;
      }
      case 'setWeather':
        this.renderer.setWeather(command.weather as WeatherKind);
        this.announceAtmosphere();
        break;
    }
  }

  // --------------------------------------------------------------- ultimates

  private addRage(team: TeamId, amount: number): void {
    if (amount <= 0) return;
    this.rage[team] = Math.min(this.config.likes.rageMax, this.rage[team] + amount);
    if (
      this.config.likes.autoCastAtFull &&
      this.rage[team] >= this.config.likes.rageMax &&
      this.clock - this.lastCast[team] >= this.config.likes.castCooldownSeconds &&
      this.phase === 'battle'
    ) {
      this.castUltimate(team, this.pickUltimate());
    }
  }

  /** Elige una ultimate del pool según su peso relativo. */
  private pickUltimate(): string {
    const pool = this.config.ultimates.pool.filter((key) => this.config.ultimates.defs[key]);
    if (pool.length === 0) return '';
    const total = pool.reduce((sum, key) => sum + Math.max(0, this.config.ultimates.defs[key].weight), 0);
    if (total <= 0) return this.rng.pick(pool);
    let roll = this.rng() * total;
    for (const key of pool) {
      roll -= Math.max(0, this.config.ultimates.defs[key].weight);
      if (roll <= 0) return key;
    }
    return pool[pool.length - 1];
  }

  private castUltimate(team: TeamId, key: string, force = false): void {
    const def: UltimateDef | undefined = this.config.ultimates.defs[key];
    if (!def) return;
    if (!force && this.phase !== 'battle') return;

    this.rage[team] = 0;
    this.lastCast[team] = this.clock;

    this.send({
      t: 'ultimate',
      team,
      payload: {
        key,
        durationSeconds: def.durationSeconds,
        ...(def.meteors !== undefined ? { meteors: def.meteors } : {}),
        ...(def.damage !== undefined ? { damage: def.damage } : {}),
        ...(def.radius !== undefined ? { radius: def.radius } : {}),
        ...(def.troops !== undefined ? { troops: def.troops } : {}),
        ...(def.unit !== undefined ? { unit: def.unit } : {}),
        ...(def.damageMultiplier !== undefined ? { damageMultiplier: def.damageMultiplier } : {}),
        ...(def.speedMultiplier !== undefined ? { speedMultiplier: def.speedMultiplier } : {}),
        ...(def.damageTakenMultiplier !== undefined ? { damageTakenMultiplier: def.damageTakenMultiplier } : {}),
        ...(def.revivePercent !== undefined ? { revivePercent: def.revivePercent } : {}),
        ...(def.maxRevive !== undefined ? { maxRevive: def.maxRevive } : {}),
      },
    });

    this.hud.showUltimate(team, def);
    this.renderer.director.shake(def.shake ?? 0.5);
    // Destello de pantalla del color del equipo: marca el momento sin taparlo.
    this.renderer.flash(0.18 + (def.shake ?? 0.5) * 0.12, this.config.teams[team].colorLight);
    const point = this.renderer.dramaticPointFor(team);
    this.renderer.director.cutToDramatic(point.x, point.z);
  }

  // ------------------------------------------------------------------ rondas

  startSeries(): void {
    this.wins = { red: 0, blue: 0 };
    this.round = 1;
    this.hud.setWins(0, 0, this.config.series.target);
    this.startRound();
  }

  startRound(): void {
    this.roundSeed = (this.roundSeed * 1103515245 + 12345) >>> 0;
    this.phase = 'countdown';
    this.phaseTimer = this.config.series.countdownSeconds;
    this.roundElapsed = 0;
    this.suddenDeath = false;
    this.lastCountdownShown = -1;
    this.rage = { red: 0, blue: 0 };
    this.champions.clear();
    this.championsByUser.clear();
    this.championCount = { red: 0, blue: 0 };

    this.bus.flushAll();
    this.send({ t: 'setRunning', value: false });
    this.send({ t: 'reset', seed: this.roundSeed, startingTroops: this.config.battle.startingTroops });
    this.send({ t: 'setSuddenDeath', multiplier: 1 });

    this.applyRoundAtmosphere();
    this.renderer.resetRound();
    this.hud.clearFeed();
    this.hud.setRound(this.round);
    this.hud.setCountries(this.countries.red, this.countries.blue);
    // Los contadores suben desde 0 durante la cuenta atrás: el ejército se forma.
    this.hud.rollSoldiers(this.config.battle.startingTroops, this.config.battle.startingTroops);
    this.hud.setCta(this.config.identity.callToAction);
  }

  /**
   * Estación y clima de la ronda. La estación avanza en orden —para que a lo
   * largo de un directo se vea el año entero— y el clima se sortea entre los
   * que tienen sentido en esa estación: no nieva en verano.
   */
  private applyRoundAtmosphere(): void {
    const world = this.config.world;
    if (!world) return;
    if (world.cycleSeasonEachRound && this.round > 1) {
      this.seasonIndex = (this.seasonIndex + 1) % SEASONS.length;
    }
    this.renderer.setSeason(SEASONS[this.seasonIndex]);
    if (world.randomWeatherEachRound) this.renderer.rollWeather();
    else this.renderer.setWeather(world.weather as WeatherKind);
    this.announceAtmosphere();
  }

  private announceAtmosphere(): void {
    const { season, weather } = this.renderer.atmosphere;
    const seasonLabel = SEASON_PROFILES[season]?.label ?? season;
    const weatherLabel = WEATHER_PROFILES[weather]?.label ?? weather;
    const icons: Record<string, string> = { clear: '☀️', rain: '🌧️', snow: '❄️', fog: '🌫️', storm: '⛈️' };
    this.hud.pushFeed({ text: `${icons[weather] ?? ''} ${seasonLabel} · ${weatherLabel}` });
  }

  private beginBattle(): void {
    this.phase = 'battle';
    this.roundElapsed = 0;
    this.send({ t: 'setRunning', value: true });
    this.hud.showCountdown(0);
  }

  private endRound(winner: TeamId | 'draw'): void {
    if (this.phase !== 'battle') return;
    this.phase = 'roundEnd';
    this.phaseTimer = this.config.series.roundEndSeconds;
    this.send({ t: 'setRunning', value: false });

    if (winner !== 'draw') this.wins[winner]++;
    this.hud.setWins(this.wins.red, this.wins.blue, this.config.series.target);

    const survivors = winner === 'draw' ? 0 : this.soldiers[winner];
    this.hud.showRoundResult(
      winner,
      winner === 'draw'
        ? 'Nadie dominó el campo'
        : `Sobrevivieron ${survivors.toLocaleString(this.config.hud.counterFormat)} soldados`,
    );

    void this.ranking.recordMatch({
      round: this.round,
      winner,
      redCountry: this.countries.red,
      blueCountry: this.countries.blue,
      redSoldiers: this.soldiers.red,
      blueSoldiers: this.soldiers.blue,
      durationSeconds: Math.round(this.roundElapsed),
      endedAt: Date.now(),
    });

    if (winner !== 'draw' && this.wins[winner] >= this.config.series.target) {
      this.phase = 'seriesEnd';
      this.phaseTimer = this.config.series.roundEndSeconds * 2;
      this.hud.showSeriesResult(winner, this.wins[winner], this.config.series.target);
    }
  }

  /** Rota los países al terminar la ronda: así la serie no es siempre el mismo cartel. */
  private rotateCountries(): void {
    if (!this.config.series.rotateCountriesOnRoundEnd) return;
    for (const team of ['red', 'blue'] as TeamId[]) {
      const rotation = this.config.countryRotation[team] ?? [];
      if (rotation.length === 0) continue;
      const index = rotation.indexOf(this.countries[team]);
      const next = rotation[(index + 1) % rotation.length];
      this.countries[team] = next;
      this.config.teams[team].country = next;
    }
  }

  setPaused(value: boolean): void {
    this.paused = value;
    this.send({ t: 'setPaused', value });
  }

  // -------------------------------------------------------- bucle principal

  /**
   * @param dt      Delta de render, acotado para que las animaciones no den saltos.
   * @param realDt  Delta de reloj real. Los temporizadores de ronda usan este: si
   *                el PC del streamer sufre un bajón de FPS, la partida no puede
   *                pasar a cámara lenta mientras la simulación (que corre a paso
   *                fijo en el worker) sigue a velocidad normal.
   */
  update(dt: number, realDt: number = dt): void {
    this.clock += realDt;
    this.hud.update(dt);

    switch (this.phase) {
      case 'countdown': {
        this.phaseTimer -= realDt;
        const remaining = Math.ceil(this.phaseTimer);
        if (remaining !== this.lastCountdownShown && remaining > 0) {
          this.lastCountdownShown = remaining;
          this.hud.showCountdown(remaining);
        }
        if (this.phaseTimer <= 0) this.beginBattle();
        break;
      }
      case 'battle': {
        if (!this.paused) this.roundElapsed += realDt;
        this.hud.setTimer(this.roundElapsed, this.suddenDeath);

        if (!this.suddenDeath && this.roundElapsed >= this.config.series.suddenDeathAtSeconds) {
          this.suddenDeath = true;
          this.send({ t: 'setSuddenDeath', multiplier: this.config.series.suddenDeathDamageMultiplier });
          this.hud.showSuddenDeath();
          this.hud.pushFeed({ text: '⚡ MUERTE SÚBITA: el daño se dispara', big: true });
        }

        // Fin por aniquilación (con margen para no cortar por un redondeo).
        if (this.soldiers.red <= 0 && this.soldiers.blue <= 0) this.endRound('draw');
        else if (this.soldiers.red <= 0) this.endRound('blue');
        else if (this.soldiers.blue <= 0) this.endRound('red');
        else if (this.roundElapsed >= this.config.series.maxRoundSeconds) {
          this.endRound(this.soldiers.red === this.soldiers.blue ? 'draw' : this.soldiers.red > this.soldiers.blue ? 'red' : 'blue');
        }
        break;
      }
      case 'roundEnd': {
        this.phaseTimer -= realDt;
        if (this.phaseTimer <= 0) {
          this.round++;
          this.rotateCountries();
          this.startRound();
        }
        break;
      }
      case 'seriesEnd': {
        this.phaseTimer -= realDt;
        if (this.phaseTimer <= 0) {
          if (this.config.series.resetSeriesOnComplete) {
            this.round = 1;
            this.rotateCountries();
            this.startSeries();
          } else {
            this.phase = 'lobby';
          }
        }
        break;
      }
      case 'lobby':
        break;
    }

    this.hud.setRage(this.rage.red, this.rage.blue, this.config.likes.rageMax);

    this.donorTimer += realDt;
    if (this.donorTimer >= 1.5) {
      this.donorTimer = 0;
      this.hud.setDonors(this.ranking.topDonors(this.config.persistence.topDonorsShown));
      this.hud.setConnection(this.transports.connected, this.simulator.running);
    }

    this.statusTimer += realDt;
    if (this.statusTimer >= 0.5) {
      this.statusTimer = 0;
      this.publishStatus();
    }
  }

  // ------------------------------------------------------------------ worker

  private onWorkerMessage(ev: MessageEvent): void {
    const data = ev.data as Snapshot | { t: 'ready' };
    if (data.t === 'ready') {
      if (this.config.series.autoStart) this.startSeries();
      return;
    }

    const snapshot = data;
    this.soldiers.red = snapshot.stats.red.soldiers;
    this.soldiers.blue = snapshot.stats.blue.soldiers;
    this.entities.red = snapshot.stats.red.entities;
    this.entities.blue = snapshot.stats.blue.entities;
    this.hud.setSoldiers(this.soldiers.red, this.soldiers.blue);

    this.renderer.applySnapshot(snapshot);
    this.renderer.updateNametags(snapshot.champions, this.championLookup());

    for (const death of snapshot.championDeaths) {
      this.onChampionDeath(death);
    }

    // Los buffers vuelven al worker para reutilizarlos en el siguiente tick.
    this.send({ t: 'recycle', units: snapshot.units, projectiles: snapshot.projectiles }, [snapshot.units, snapshot.projectiles]);
  }

  private championLookup(): Map<number, { nickname: string; team: TeamId }> {
    const lookup = new Map<number, { nickname: string; team: TeamId }>();
    for (const [id, record] of this.champions) lookup.set(id, { nickname: record.nickname, team: record.team });
    return lookup;
  }

  private onChampionDeath(death: { championId: number; killerChampionId: number; kills: number; lifetime: number }): void {
    const record = this.champions.get(death.championId);
    if (!record) return;
    this.champions.delete(death.championId);
    this.championsByUser.delete(record.uniqueId);
    this.championCount[record.team] = Math.max(0, this.championCount[record.team] - 1);

    if (this.config.persistence.trackSurvivalRanking) {
      this.ranking.recordWarrior(record.uniqueId, record.nickname, death.lifetime, death.kills);
    }

    const killer = this.champions.get(death.killerChampionId);
    const killsText = death.kills > 0 ? ` (${death.kills.toLocaleString(this.config.hud.counterFormat)} bajas)` : '';
    this.hud.pushFeed({
      user: record.nickname,
      text: killer ? `cayó ante ${killer.nickname}${killsText}` : `cayó tras ${Math.round(death.lifetime)}s${killsText}`,
      team: record.team,
    });
  }

  private publishStatus(): void {
    this.transports.publishStatus({
      type: 'status',
      phase: this.phase,
      round: this.round,
      seriesTarget: this.config.series.target,
      wins: { ...this.wins },
      soldiers: { ...this.soldiers },
      rage: { ...this.rage },
      countries: { ...this.countries },
      season: this.renderer.atmosphere.season,
      weather: this.renderer.atmosphere.weather,
      entities: { ...this.entities },
      champions: { ...this.championCount },
      camera: { mode: this.config.camera.mode, ...this.renderer.director.settings },
      graphics: {
        quality: this.config.graphics.quality,
        bloomIntensity: this.config.graphics.bloomIntensity,
        saturation: this.config.graphics.saturation,
        contrast: this.config.graphics.contrast,
        exposure: this.config.graphics.exposure,
        vignette: this.config.graphics.vignette,
        lodDistance: this.config.graphics.lodDistance,
        textureDistance: this.config.graphics.textureDistance,
        fogDensity: this.config.graphics.fogDensity,
      },
      difficulty: this.config.battle.difficulty,
      fps: Math.round(this.renderer.fps),
      roundElapsed: Math.round(this.roundElapsed),
      simulator: this.simulator.running,
      paused: this.paused,
      connected: this.transports.connected,
    });
  }

  dispose(): void {
    this.worker.terminate();
  }
}
