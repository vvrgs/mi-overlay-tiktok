/**
 * HUD del overlay.
 *
 * Solo toca el DOM que ya existe en index.html mediante atributos `data-hud`,
 * así que puedes reordenar o re-estilizar el HTML sin tocar este archivo.
 * Los contadores se interpolan en cada frame: ver el número bajar de forma
 * continua es lo que engancha a la gente a seguir mandando tropas.
 */

import type { GameConfig, TeamId } from '../shared/config';
import { damp, formatCount, formatTime } from '../shared/math';
import type { UltimateDef } from '../shared/config';

export interface FeedEntry {
  user?: string;
  text: string;
  amount?: string;
  team?: TeamId;
  big?: boolean;
}

export interface DonorView {
  nickname: string;
  coins: number;
}

type HudMap = Record<string, HTMLElement>;

const MAX_FEED_ITEMS = 9;

export class Hud {
  private el: HudMap = {};
  private displayed = { red: 0, blue: 0 };
  private targets = { red: 0, blue: 0 };
  private stageTimer: ReturnType<typeof setTimeout> | null = null;
  private feedItems: HTMLElement[] = [];

  constructor(private root: HTMLElement, private config: GameConfig) {
    for (const node of root.querySelectorAll<HTMLElement>('[data-hud]')) {
      this.el[node.dataset.hud!] = node;
    }
    this.applyConfig(config);
  }

  applyConfig(config: GameConfig): void {
    this.config = config;
    const style = this.root.style;
    style.setProperty('--red', config.teams.red.color);
    style.setProperty('--red-dark', config.teams.red.colorDark);
    style.setProperty('--blue', config.teams.blue.color);
    style.setProperty('--blue-dark', config.teams.blue.colorDark);
    style.setProperty('--hud-scale', String(config.hud.scale));
    style.setProperty('--safe-top', `${config.hud.safeAreaTop}px`);
    style.setProperty('--safe-bottom', `${config.hud.safeAreaBottom}px`);

    this.text('redName', config.teams.red.name);
    this.text('blueName', config.teams.blue.name);
    this.text('redCounterLabel', config.teams.red.shortName);
    this.text('blueCounterLabel', config.teams.blue.shortName);
    this.text('watermark', config.identity.watermark);
    this.setCta(config.identity.callToAction);

    this.toggle('scoreboard', config.hud.showScoreboard);
    this.toggle('counters', config.hud.showSoldierCounters);
    this.toggle('feed', config.hud.showKillfeed);
    this.toggle('donorsWrap', config.hud.showTopDonors);
    this.toggle('cta', config.hud.showCallToAction);
    this.toggle('timer', config.hud.showRoundTimer);
    this.toggle('redRageWrap', config.hud.showRageBars);
    this.toggle('blueRageWrap', config.hud.showRageBars);
  }

  // ------------------------------------------------------------- utilidades

  private text(key: string, value: string): void {
    const node = this.el[key];
    if (node && node.textContent !== value) node.textContent = value;
  }

  private toggle(key: string, visible: boolean): void {
    const node = this.el[key];
    if (node) node.hidden = !visible;
  }

  // -------------------------------------------------------------- marcador

  setCountries(red: string, blue: string): void {
    const countries = this.config.countries;
    const redInfo = countries[red] ?? { name: red, flag: '🏳️' };
    const blueInfo = countries[blue] ?? { name: blue, flag: '🏳️' };
    this.text('redFlag', redInfo.flag);
    this.text('blueFlag', blueInfo.flag);
    this.text('redCountry', redInfo.name);
    this.text('blueCountry', blueInfo.name);
  }

  setWins(red: number, blue: number, target: number): void {
    this.text('redScore', String(red));
    this.text('blueScore', String(blue));
    this.text('redWins', `WIN ${red} / ${target}`);
    this.text('blueWins', `WIN ${blue} / ${target}`);
  }

  setRound(round: number): void {
    this.text('roundLabel', `RONDA ${round}`);
  }

  setTimer(seconds: number, suddenDeath: boolean): void {
    this.text('timer', formatTime(seconds));
    this.el.timer?.classList.toggle('is-sudden', suddenDeath);
  }

  // ------------------------------------------------------------ contadores

  setSoldiers(red: number, blue: number): void {
    // Un salto grande (un regalo gordo) merece el destello del contador.
    if (Math.abs(red - this.targets.red) > Math.max(500, this.targets.red * 0.04)) this.pulse('red');
    if (Math.abs(blue - this.targets.blue) > Math.max(500, this.targets.blue * 0.04)) this.pulse('blue');
    this.targets.red = red;
    this.targets.blue = blue;
  }

  private pulse(team: TeamId): void {
    const node = this.el[team === 'red' ? 'redSoldiers' : 'blueSoldiers']?.parentElement;
    if (!node) return;
    node.classList.remove('is-hit');
    // Forzar reflow reinicia la animación aunque se dispare dos veces seguidas.
    void node.offsetWidth;
    node.classList.add('is-hit');
  }

  setRage(red: number, blue: number, max: number): void {
    for (const [team, value] of [
      ['red', red],
      ['blue', blue],
    ] as Array<[TeamId, number]>) {
      const ratio = Math.max(0, Math.min(1, value / (max || 1)));
      const fill = this.el[team === 'red' ? 'redRage' : 'blueRage'];
      if (fill) fill.style.width = `${(ratio * 100).toFixed(1)}%`;
      const wrap = this.el[team === 'red' ? 'redRageWrap' : 'blueRageWrap'];
      wrap?.classList.toggle('is-full', ratio >= 0.999);
    }
  }

  /** Interpola los contadores hacia su valor real. Se llama cada frame. */
  update(dt: number): void {
    for (const team of ['red', 'blue'] as TeamId[]) {
      const target = this.targets[team];
      const current = this.displayed[team];
      const next = Math.abs(target - current) < 1 ? target : damp(current, target, 9, dt);
      if (Math.round(next) !== Math.round(current)) {
        this.text(team === 'red' ? 'redSoldiers' : 'blueSoldiers', formatCount(next, this.config.hud.counterFormat));
      }
      this.displayed[team] = next;
    }
  }

  /** Coloca los contadores en su valor sin animación (inicio de ronda). */
  snapSoldiers(red: number, blue: number): void {
    this.targets = { red, blue };
    this.displayed = { red, blue };
    this.text('redSoldiers', formatCount(red, this.config.hud.counterFormat));
    this.text('blueSoldiers', formatCount(blue, this.config.hud.counterFormat));
  }

  // ------------------------------------------------------------------ feed

  pushFeed(entry: FeedEntry): void {
    const feed = this.el.feed;
    if (!feed || feed.hidden) return;

    const item = document.createElement('div');
    item.className = entry.big ? 'feed__item feed__item--big' : 'feed__item';
    if (entry.team) item.dataset.team = entry.team;

    if (entry.user) {
      const user = document.createElement('span');
      user.className = 'feed__user';
      user.textContent = entry.user;
      item.appendChild(user);
    }
    const text = document.createElement('span');
    text.className = 'feed__text';
    text.textContent = entry.text;
    item.appendChild(text);

    if (entry.amount) {
      const amount = document.createElement('span');
      amount.className = 'feed__amount';
      amount.textContent = entry.amount;
      item.appendChild(amount);
    }

    feed.appendChild(item);
    this.feedItems.push(item);
    while (this.feedItems.length > MAX_FEED_ITEMS) {
      this.feedItems.shift()?.remove();
    }
  }

  clearFeed(): void {
    for (const item of this.feedItems) item.remove();
    this.feedItems.length = 0;
  }

  // -------------------------------------------------------------- donadores

  setDonors(donors: DonorView[]): void {
    const list = this.el.donors;
    if (!list) return;
    list.textContent = '';
    donors.forEach((donor, index) => {
      const item = document.createElement('li');
      item.className = 'donors__item';
      const rank = document.createElement('span');
      rank.className = 'donors__rank';
      rank.textContent = `${index + 1}.`;
      const name = document.createElement('span');
      name.className = 'donors__name';
      name.textContent = donor.nickname;
      const coins = document.createElement('span');
      coins.className = 'donors__coins';
      coins.textContent = formatCount(donor.coins, this.config.hud.counterFormat);
      item.append(rank, name, coins);
      list.appendChild(item);
    });
  }

  // ----------------------------------------------------------- capa central

  private stage(html: HTMLElement | null, holdMs: number): void {
    const stage = this.el.stage;
    if (!stage) return;
    if (this.stageTimer) clearTimeout(this.stageTimer);
    stage.textContent = '';
    if (!html) return;
    stage.appendChild(html);
    if (holdMs > 0) {
      this.stageTimer = setTimeout(() => {
        stage.textContent = '';
        this.stageTimer = null;
      }, holdMs);
    }
  }

  showCountdown(value: number): void {
    const node = document.createElement('div');
    node.className = 'stage__countdown';
    node.textContent = value > 0 ? String(value) : '¡YA!';
    this.stage(node, 1000);
  }

  showRoundResult(winner: TeamId | 'draw', subtitle: string): void {
    const wrap = document.createElement('div');
    wrap.className = 'stage';
    const banner = document.createElement('div');
    if (winner === 'draw') {
      banner.className = 'stage__banner stage__banner--gold';
      banner.textContent = 'EMPATE';
    } else {
      banner.className = `stage__banner stage__banner--${winner}`;
      const team = this.config.teams[winner];
      const country = this.config.countries[team.country];
      banner.textContent = `¡GANA ${country ? country.name.toUpperCase() : team.shortName}! ${country?.flag ?? ''}`;
    }
    const sub = document.createElement('div');
    sub.className = 'stage__sub';
    sub.textContent = subtitle;
    wrap.append(banner, sub);
    this.stage(wrap, 0);
  }

  showSeriesResult(winner: TeamId, wins: number, target: number): void {
    const wrap = document.createElement('div');
    wrap.className = 'stage';
    const banner = document.createElement('div');
    banner.className = 'stage__banner stage__banner--gold';
    const team = this.config.teams[winner];
    const country = this.config.countries[team.country];
    banner.textContent = `🏆 ${country ? country.name.toUpperCase() : team.shortName} CAMPEÓN`;
    const sub = document.createElement('div');
    sub.className = 'stage__sub';
    sub.textContent = `Serie ganada ${wins} / ${target}`;
    wrap.append(banner, sub);
    this.stage(wrap, 0);
  }

  showUltimate(team: TeamId, def: UltimateDef): void {
    const wrap = document.createElement('div');
    wrap.className = 'stage__ultimate';
    const icon = document.createElement('div');
    icon.className = 'stage__ultimate-icon';
    icon.textContent = def.icon;
    const name = document.createElement('div');
    name.className = 'stage__ultimate-name';
    name.textContent = def.label;
    name.style.color = this.config.teams[team].color;
    const desc = document.createElement('div');
    desc.className = 'stage__ultimate-desc';
    desc.textContent = def.description;
    wrap.append(icon, name, desc);
    this.stage(wrap, 2600);
  }

  clearStage(): void {
    this.stage(null, 0);
  }

  // ---------------------------------------------------------------- estado

  setCta(text: string, visible = true): void {
    const node = this.el.cta;
    if (!node) return;
    const span = node.querySelector('.cta__text');
    if (span && span.textContent !== text) span.textContent = text;
    node.hidden = !visible || !this.config.hud.showCallToAction;
  }

  setConnection(connected: boolean, simulator: boolean): void {
    const dot = this.el.statusDot;
    if (dot) {
      dot.classList.toggle('is-online', connected && !simulator);
      dot.classList.toggle('is-sim', simulator);
    }
    this.text('statusText', simulator ? 'simulador' : connected ? 'en vivo' : 'sin conexión');
  }
}
