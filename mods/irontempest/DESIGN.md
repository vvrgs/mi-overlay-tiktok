# IRON TEMPEST — Arsenal de Guerra (G0 · Ficha de diseño)

**mod_id:** `irontempest` · **MC:** 1.20.1 · **Loader:** Forge 47.2.0 · **Mappings:** official (Mojang)
**Dependencias:** ninguna obligatoria. Opcional: AAA Particles (Effekseer, vía reflexión), Oculus (compat verificada).
**Contrato TikTok:** comandos de consola ejecutables por ServerTap/StreamToEarn. Un comando = un regalo.

---

## Ataques (tier declarado ANTES de codear — regla #5 del skill)

### 1. `rocketrain` — Tier **S** (spameable, 1–30 monedas, ráfagas de 100)
- **Comando:** `irontempest rocketrain [jugador] [salvas]`
- **Política:** cola GLOBAL por jugador + batch-ticker. Cada comando = +6 cohetes a la cola.
  `MAX_PENDING = 300`. Drena 1 cohete cada 4 ticks por jugador (lluvia continua, jamás 1 sesión/comando).
- **Cronología por cohete:** T+0 aparece a 28–40 bloques sobre un anillo aleatorio alrededor del
  jugador, silbido descendente con pitch-drop → T+impacto: flash 2t → bola de fuego 8t →
  anillo de choque 12t → humo negro 40t + chispas balísticas → quemadura cosmética ≤5 bloques (config).
- **Cámara/lock:** NUNCA. **Destrucción:** cosmética ≤5 bloques, presupuesto global compartido.

### 2. `cruisemissile` — Tier **M** (solapable, 100–700 monedas, 1–5 juntos)
- **Comando:** `irontempest cruisemissile [jugador]`
- **Política:** multi-sesión por jugador (MAX 5) + cola FIFO; offset espacial por índice de sesión
  (cada misil nace rotado 72° alrededor del jugador para que 5 misiles simultáneos se lean como abanico).
- **Cronología:** T+0 klaxon + columna de humo de silo a 45 bloques → T+20 lanzamiento vertical
  (boost, estela de fuego + humo denso) → T+55 tip-over con giro balístico → fase crucero a 30 bloques
  de altura con motor pulsante → guiado terminal proporcional (nav constante, no teleport) →
  detonación de proximidad (3.5 bloques): flash → bola de fuego 3 capas → anillo → hongo de humo
  60t → escombros con física → cráter amortizado r=4 → daño `killIfNoTotem` en r=6 con falloff.
- **Cámara/lock:** no. **Destrucción:** cráter r=4 amortizado, presupuesto GLOBAL de bloques/tick.

### 3. `tankblitz` — Tier **C** (cinemático, 1000–3000 monedas, ~1)
- **Comando:** `irontempest tankblitz [jugador]`
- **Política:** patrón GodzillaManager: fases con timeouts duros, `MAX_PER_PLAYER = 1`, extras se
  encolan FIFO. `MAX_LIFETIME_TICKS = 1200` (60 s) de seguridad absoluta.
- **Cronología (fases T_*):** T_SPAWN(0–30): tanque cae de un drop-pod con retro-cohetes a 25
  bloques, polvo de aterrizaje + clank metálico → T_HUNT(30–130): motor loop, torreta gira hacia el
  jugador a 2.2°/tick (traverse realista con inercia), designador láser visible cuando adquiere →
  T_FIRE ×3 (cada 45t): flash de boca 3 capas + retroceso de cañón con resorte + casquillo eyectado +
  obús trazador balístico → impacto (flash/fuego/anillo/humo/cráter r=3) → T_FINALE: disparo de
  clímax con doble carga → T_LEAVE: humo de cortina + el tanque se auto-desguaza en escombros.
- **Cámara/lock:** shake fuerte direccional (paquete custom), sin secuestro duro de cámara
  (desviación deliberada del patrón del workspace: el lock duro no es portable sin su referencia;
  el shake + sonido 3D consigue la lectura cinemática sin riesgo de softlock).
- **Destrucción:** cráteres r=3 por obús, amortizada, guardas completas.

### 4. `orbitalstrike` — Tier **C** (cinemático, 1000–3000 monedas)
- **Comando:** `irontempest orbitalstrike [jugador]`
- **Política:** igual que tankblitz (fases, MAX_PER_PLAYER=1, timeout duro, cola de extras).
- **Cronología:** T_WARP(0–25): destello de salto + onda de distorsión, la nave insignia aparece a
  38 bloques sobre el jugador con motores pulsantes → T_CHARGE(25–105): motas de energía convergen
  al cañón ventral, pitch ascendente 4 s, anillos de carga concéntricos → T_BEAM(105–225): haz
  orbital continuo (shader GLSL propio, UV scroll + núcleo blanco) que barre hacia la posición del
  jugador a 0.35 bloques/tick, vaporización amortizada bajo el haz, fuego residual → T_OVERLOAD:
  pulso final AoE → T_JUMP(–260): la nave se pliega en un destello y deja anillo de vacío.
- **Destrucción:** trinchera del haz (r=2 continuo) + pulso final r=5, amortizada.

### 5. `armageddon` — Tier **U** (ultra, 4880+ monedas, único)
- **Comando:** `irontempest armageddon [jugador]`
- **Política:** exclusión global `ULTRA_ACTIVE` (un armageddon a la vez en TODO el server, extras
  rechazados con mensaje), broadcast con título a todos los jugadores, `MAX_LIFETIME_TICKS = 2400`.
- **Cronología:** sirena global + título → oleada 1: rocketrain ×40 comprimido → oleada 2: 3 misiles
  crucero escalonados 30t → oleada 3: tanque blitz → clímax: orbital strike sobre el objetivo →
  epílogo: lluvia de escombros ardientes 10 s + humo global. Todo orquestado por un director de
  fases que reutiliza los managers (no duplica lógica).
- **Destrucción:** máxima permitida por config, presupuesto global, amortizada SIEMPRE.

---

## Reglas duras aplicadas (del skill minecraft-mods)

- 3 hooks de cleanup en TODO manager: `ServerStopping`, `PlayerLoggedOut`, `LivingDeath` (del objetivo).
- `MAX_LIFETIME_TICKS` en toda sesión. Cero ticks huérfanos.
- Prohibido `catch {}` vacío — mínimo log con contexto.
- Log-markers: `[irontempest] session start id=<n> tier=<X> attack=<name> target=<player>` /
  `[irontempest] session end id=<n> reason=<...>`.
- Destrucción SIEMPRE amortizada (`advance(n)` por tick), guardas: lista de bloques protegidos,
  `getDestroySpeed < 0` (bedrock), no drenar fluidos, límite Y, presupuesto global de bloques/tick.
- Daño letal: `killIfNoTotem` con DamageTypes custom data-driven (mensajes de muerte propios,
  tags `bypasses_armor`; el totem SIEMPRE se respeta).
- Comandos Brigadier: `RegisterCommandsEvent`, `hasPermission(2)`, `EntityArgument.player()` +
  variante consola sin argumento → primer jugador online.

## FX — política "cero partículas vanilla"

Motor propio: 11 `ParticleType` custom con sprite sheets propios (generados, 8–16 frames),
física por partícula (drag, gravedad, turbulencia, rebote en suelo para escombros), render types
aditivos full-bright, anillo de choque orientado al plano del suelo, trazadores estirados por
velocidad. Shader core GLSL propio para el haz orbital; post-shader de distorsión radial para
ondas de choque (auto-desactivado si Oculus/Iris está presente — compat shader-pack).

**Puente Effekseer:** `EffekBridge` detecta AAA Particles por reflexión (`aaa_particles`). Si está,
cada evento FX mayor dispara además su `.efkefc` desde `assets/irontempest/effeks/<ataque>/<nombre>/`
(estructura lista; los `.efkefc` se crean con el skill effekseer en la máquina del usuario y se
sueltan ahí sin tocar código — el registro lee un manifest `effeks/manifest.json`).

## Sonido (todo sintetizado, sin copyright)

`explosion_far/near`, `shell_whistle` (pitch-drop), `missile_launch`, `missile_loop`, `tank_engine`
(loop), `cannon_fire`, `tank_landing`, `mlrs_launch`, `laser_charge`, `laser_beam` (loop),
`warp_in/out`, `klaxon`, `debris_clank`, `ultra_siren`. Subtítulos en lang (es_es, es_mx, en_us).

## Verificación en este entorno (adaptación del pipeline)

- G2/G3 → stub-compile javac (0 errores) + revisión adversarial multi-agente de mappings.
- G1 → json.tool sobre TODO asset JSON; PNGs/OGGs generados y verificados por tamaño/carga.
- G4–G6 (deploy real, smoke ServerTap, visual in-game) → en la máquina del usuario (README).
