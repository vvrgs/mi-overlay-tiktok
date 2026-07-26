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
  private lastShare = -1;
  /** Durante la cuenta atrás los contadores suben desde 0: ver formarse al ejército. */
  private rolling = false;
  private lastWins = { red: -1, blue: -1 };
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
    // El número que anota celebra con un pop; el reflow forzado reinicia la
    // animación si anotara dos veces seguidas.
    for (const [team, value] of [['red', red], ['blue', blue]] as Array<[TeamId, number]>) {
      const node = this.el[team === 'red' ? 'redScore' : 'blueScore'];
      if (node && this.lastWins[team] >= 0 && value > this.lastWins[team]) {
        node.classList.remove('is-scored');
        void node.offsetWidth;
        node.classList.add('is-scored');
      }
      this.lastWins[team] = value;
    }
    this.text('redScore', String(red));
    this.text('blueScore', String(blue));
    // Sin espacios alrededor de la barra: "WIN 1 / 10" se partía en dos líneas
    // cuando el nombre del país era corto y la caja estrechaba.
    this.text('redWins', `VICTORIAS ${red}/${target}`);
    this.text('blueWins', `VICTORIAS ${blue}/${target}`);
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
    const node = this.el[team === 'red' ? 'redSoldiers' : 'blueSoldiers'];
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
      // scaleX en vez de width: la barra cambia con cada like y animar width
      // fuerza layout encima del WebGL; el transform va por el compositor.
      if (fill) fill.style.transform = `scaleX(${ratio.toFixed(3)})`;
      const wrap = this.el[team === 'red' ? 'redRageWrap' : 'blueRageWrap'];
      wrap?.classList.toggle('is-full', ratio >= 0.999);
    }
  }

  /** Interpola los contadores hacia su valor real. Se llama cada frame. */
  update(dt: number): void {
    for (const team of ['red', 'blue'] as TeamId[]) {
      const target = this.targets[team];
      const current = this.displayed[team];
      const gap = Math.abs(target - current);
      // Lambda adaptativa: el roll-up de arranque es pausado (que se vea crecer
      // al ejército), un regalo gordo alcanza rápido (el "subidón" debe verse
      // como tal) y el goteo del combate baja suave.
      const lambda = this.rolling ? 2.6 : gap > Math.max(2000, target * 0.15) ? 16 : 9;
      const next = gap < 1 ? target : damp(current, target, lambda, dt);
      if (Math.round(next) !== Math.round(current)) {
        this.text(team === 'red' ? 'redSoldiers' : 'blueSoldiers', formatCount(next, this.config.hud.counterFormat));
      }
      this.displayed[team] = next;
    }
    if (this.rolling && this.displayed.red >= this.targets.red - 1 && this.displayed.blue >= this.targets.blue - 1) {
      this.rolling = false;
    }

    // Reparto de la barra de fuerzas. Solo se toca el DOM cuando el porcentaje
    // se mueve de verdad: escribir estilos a 60 Hz sin necesidad es tirar CPU.
    const total = this.displayed.red + this.displayed.blue;
    const share = total > 0 ? (this.displayed.red / total) * 100 : 50;
    if (Math.abs(share - this.lastShare) > 0.15) {
      this.lastShare = share;
      const fill = this.el.redShare;
      if (fill) fill.style.width = `${share.toFixed(1)}%`;
      const spark = this.el.shareSpark;
      if (spark) spark.style.left = `${share.toFixed(1)}%`;
    }
  }

  /** Coloca los contadores en su valor sin animación (inicio de ronda). */
  snapSoldiers(red: number, blue: number): void {
    this.rolling = false;
    this.targets = { red, blue };
    this.displayed = { red, blue };
    this.text('redSoldiers', formatCount(red, this.config.hud.counterFormat));
    this.text('blueSoldiers', formatCount(blue, this.config.hud.counterFormat));
  }

  /** Cuenta de 0 al valor inicial durante la cuenta atrás: el ejército se forma. */
  rollSoldiers(red: number, blue: number): void {
    this.targets = { red, blue };
    this.displayed = { red: 0, blue: 0 };
    this.rolling = true;
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
      // El más viejo se despide con un fundido en vez de evaporarse.
      const oldest = this.feedItems.shift();
      if (oldest) {
        oldest.classList.add('feed__item--out');
        setTimeout(() => oldest.remove(), 220);
      }
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
    // Lo que hubiera se despide con una animación corta en vez de esfumarse.
    // Se saca del flujo (absolute) para que no empuje al contenido nuevo.
    for (const child of [...stage.children] as HTMLElement[]) {
      if (child.classList.contains('stage-leave')) {
        child.remove();
        continue;
      }
      child.classList.add('stage-leave');
      setTimeout(() => child.remove(), 240);
    }
    if (!html) return;
    stage.appendChild(html);
    if (holdMs > 0) {
      this.stageTimer = setTimeout(() => {
        this.stage(null, 0);
        this.stageTimer = null;
      }, holdMs);
    }
  }

  /** Crea un div con clase y texto: el HUD monta muchos nodos pequeños. */
  private div(className: string, textContent = ''): HTMLDivElement {
    const node = document.createElement('div');
    node.className = className;
    if (textContent) node.textContent = textContent;
    return node;
  }

  showCountdown(value: number): void {
    // El nodo se recrea en cada dígito a propósito: así todas las animaciones
    // CSS (pop del número, anillo que se expande) se reinician solas.
    const wrap = this.div(value > 0 ? 'countdown' : 'countdown countdown--go');
    wrap.appendChild(this.div('countdown__ring'));
    wrap.appendChild(this.div('countdown__num', value > 0 ? String(value) : '¡A LA CARGA!'));
    wrap.appendChild(this.div('countdown__label', value > 0 ? 'LA BATALLA EMPIEZA' : ''));
    this.stage(wrap, 1000);
  }

  /** Cinta de resultado: bandera + titular sobre una banda inclinada de color. */
  private resultBanner(kind: TeamId | 'gold', flag: string, title: string, subtitle: string): HTMLElement {
    const wrap = this.div('result');
    const ribbon = this.div(`result__ribbon result__ribbon--${kind}`);
    if (flag) ribbon.appendChild(this.div('result__flag', flag));
    ribbon.appendChild(this.div('result__title', title));
    wrap.appendChild(ribbon);
    if (subtitle) wrap.appendChild(this.div('result__sub', subtitle));
    return wrap;
  }

  showRoundResult(winner: TeamId | 'draw', subtitle: string): void {
    if (winner === 'draw') {
      this.stage(this.resultBanner('gold', '⚔️', 'EMPATE', subtitle), 5200);
      return;
    }
    const team = this.config.teams[winner];
    const country = this.config.countries[team.country];
    const name = country ? country.name.toUpperCase() : team.shortName;
    this.stage(this.resultBanner(winner, country?.flag ?? '', `¡GANA ${name}!`, subtitle), 5200);
  }

  showSeriesResult(winner: TeamId, wins: number, target: number): void {
    const team = this.config.teams[winner];
    const country = this.config.countries[team.country];
    const name = country ? country.name.toUpperCase() : team.shortName;
    const banner = this.resultBanner('gold', '🏆', `${name} CAMPEÓN`, `Serie ganada ${wins} / ${target}`);
    banner.classList.add('result--series');
    this.stage(banner, 0);
  }

  showUltimate(team: TeamId, def: UltimateDef): void {
    const wrap = this.div(`ultimate ultimate--${team}`);
    wrap.appendChild(this.div('ultimate__icon', def.icon));
    const plate = this.div('ultimate__plate');
    plate.appendChild(this.div('ultimate__name', def.label));
    plate.appendChild(this.div('ultimate__desc', def.description));
    wrap.appendChild(plate);
    this.stage(wrap, 2600);
  }

  /** Aviso de muerte súbita: banner de alarma breve en el centro. */
  showSuddenDeath(): void {
    const wrap = this.div('sudden');
    wrap.appendChild(this.div('sudden__title', '⚡ MUERTE SÚBITA'));
    wrap.appendChild(this.div('sudden__sub', 'El daño se dispara'));
    this.stage(wrap, 2300);
  }

  clearStage(): void {
    this.stage(null, 0);
  }

  // ---------------------------------------------------------------- estado

  setCta(text: string, visible = true): void {
    const node = this.el.cta;
    if (!node) return;
    const span = node.querySelector('.cta__text') as HTMLElement | null;
    if (span && span.dataset.raw !== text) {
      span.dataset.raw = text;
      // Las palabras de equipo se pintan de su color: es la instrucción número
      // uno del juego y así se lee sin leer.
      span.textContent = '';
      for (const part of text.split(/(ROJO|AZUL)/i)) {
        if (/^rojo$/i.test(part) || /^azul$/i.test(part)) {
          const word = document.createElement('b');
          word.className = /^rojo$/i.test(part) ? 'cta__team cta__team--red' : 'cta__team cta__team--blue';
          word.textContent = part;
          span.appendChild(word);
        } else if (part) {
          span.appendChild(document.createTextNode(part));
        }
      }
    }
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
