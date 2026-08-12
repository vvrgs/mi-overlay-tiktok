# 🌋 CATACLISMO — desastres naturales y eventos cósmicos para tus lives

Mod **standalone** de Forge **1.20.1** (47.2.0, mojmap, Java 17) pensado para
lives de TikTok donde tus viewers intentan MATARTE: cada regalo ejecuta un
comando de consola y un desastre cinematográfico te cae encima, **te persigue
entre dimensiones**, revienta tus tótems en cadena (5–7 pops/s) **sin
saltarse jamás al tótem**, y destroza el mapa de forma amortizada (el tick
nunca se congela).

---

## 1. Build en Windows

Requisitos: **JDK 17** (Temurin/Adoptium recomendado). Gradle se descarga
solo con el wrapper.

```powershell
# ⚠ GOTCHA OneDrive: OneDrive bloquea archivos a mitad de build.
# Copia la carpeta cataclismo\ FUERA de OneDrive (p.ej. C:\dev\cataclismo)
# o pausa la sincronización antes de compilar.
cd C:\dev\mi-overlay-tiktok\cataclismo
.\gradlew build
```

El jar queda en `build\libs\cataclysm-1.0.0.jar`. (O directamente
`.\deploy.ps1`, que compila y despliega de una vez — ver sección 2.)

> La primera compilación descarga Forge y las mappings (necesita internet).
> Si tu red bloquea los maven de Forge, `verify\compile.sh` (Linux/WSL/Git
> Bash) verifica todo el código contra stubs con las firmas reales de 1.20.1
> sin red: `bash verify/compile.sh`.

## 2. Deploy (el MISMO jar en cliente y server)

Un solo comando hace build + copia a las dos carpetas + verificación de hash:

```powershell
.\deploy.ps1
```

El script borra versiones viejas del mod en destino (dos jars del mismo mod
crashean al arrancar), copia y compara los SHA256 — si alguno no cuadra, te
avisa en rojo y no da el deploy por bueno. Si ya tienes el jar compilado y
solo quieres recopiarlo: `.\deploy.ps1 -SoloCopiar`.

Rutas por defecto (cámbialas con `-Cliente` / `-Server` si mueves las instancias):

- Cliente: `C:\Users\Luis Angel\curseforge\minecraft\Instances\ss\mods`
- Server Mohist: `C:\Users\Luis Angel\curseforge\minecraft\Instances\ss\minecraftServer\mods`

Reinicia server y cliente al terminar.

## 3. Comandos (mapear a regalos de TikTok)

Todo cuelga de un único comando raíz: **`/disaster <desastre> [jugador]`**,
con `permission level 2`. **Fallback de consola**: si el comando llega por
consola sin argumento (Stream to Earn / TikFinity / ServerTap), apunta al
**primer jugador online** — ese es el contrato principal, así que en los
regalos basta con poner `disaster tornado`.

> Si algún plugin de Mohist ya registra un `/disaster`, usa el nombre
> completo `/cataclysm:disaster ...`, que siempre apunta a este mod.

| Comando | Tier | Monedas sugeridas | Qué pasa |
|---|---|---|---|
| `/disaster meteoros` | S (spameable) | 1–30 | +1 meteorito a TU cola de lluvia (telegraph 1 s → bólido → cráter r=2 + zona ardiente). 100 regalos = 100 en UNA cola que drena a ritmo fijo |
| `/disaster rayo` | S | 1–30 | Rayo dirigido REAL: 8 ticks de ionización visible → rama fractal propia → 2 pops de tótem |
| `/disaster tormenta` | M (hasta 5) | 100–700 | 60 s de célula sobre ti: rayos que PREDICEN tu movimiento + viento con ráfagas |
| `/disaster fuego` | M | 100–700 | Frente de llamas que te persigue + napalm que ignora armadura (y te sigue al End) |
| `/disaster tornado` | C (cinemático) | 1000–3000 | Embudo procedural que te CAZA a 0.4 b/t, succión física r=12, te lanza al cielo |
| `/disaster huracan` | C | 1000–3000 | 30 s primera pared → 15 s de OJO (calma sepia, silencio total) → segunda pared PEOR |
| `/disaster terremoto` | C | 1000–3000 | 5 s de temblor → FISURA REAL bajo tus pies (3–5 ancho, 20–30 profundo, 40+ largo) que te TRAGA. Queda en el mapa |
| `/disaster volcan` | C | 1000–3000 | Un volcán NACE: abombamiento → cono 15 s → erupción con bombas dirigidas A TI → flujo piroclástico (mata salvo tótem) → ceniza 60 s. El cono QUEDA |
| `/disaster tsunami` | C | 1000–3000 | Muro de agua de 11×45 que SE VE VENIR, arrasa, resaca que te arrastra de vuelta, y su agua se retira sola |
| `/disaster impacto` | U (uno por server) | 4880+ | IMPACTO PLANETARIO: sirena global → punto de luz que CRECE en el cielo → entrada atmosférica → flash blanco + anillo + cráter r=13 + eyecta + 90 s de polvo + trituradora a tope |
| `/disaster apocalipsis` | U | 4880+ | Director de oleadas: meteoros → rayos → tornado → terremoto → volcán → IMPACTO final, con agujero negro en el cielo y bossbar del timeline |
| `/disaster ejecucion_natural` | U | 4880+ | Te ANCLA (succión física, no puedes huir) y tritura tótems a cadencia máxima con contador en pantalla (default 40 pops). Clímax si sobrevives |
| `/disaster stopall` | — | — | Corta TODO y limpia TODO (sesiones, colas, FX, cielo, teams) |

### Bonus: disparar FX sueltos con `/particle` (sin pasar por el mod)

Cada evento de FX tiene un ParticleType espejo: `/particle
cataclysm:fx_<evento> ~ ~ ~` dispara en el cliente la receta nativa completa
**+ su effek del manifest**. Ideal para regalos baratos con efecto visual puro:

`fx_telegraph, fx_meteor_impact, fx_ionization, fx_lightning_bolt,
fx_fire_burst, fx_ember_field, fx_smoke_column, fx_rain_cell, fx_wind_gust,
fx_quake_dust, fx_fissure_burst, fx_tornado_debris, fx_tsunami_spray,
fx_lava_fountain, fx_eruption_blast, fx_pyroclastic_front, fx_ash_fall,
fx_impact_flash, fx_shockwave_ring, fx_impact_mushroom, fx_ejecta,
fx_totem_pop, fx_execution_anchor, fx_apocalypse_omen`

## 4. La trituradora de tótems (la regla sagrada)

**Jamás** se usa `bypasses_invulnerability` ni `setHealth(0)`: el tótem
SIEMPRE puede salvar — el show son los pops. Cada pulso: (1) auto-recarga la
offhand desde el inventario (los tótems no se apilan y solo salvan en mano),
(2) `invulnerableTime=0` + damage type con `bypasses_cooldown` (cinturón y
tirantes para Mohist), (3) golpe de 10000 con damage type propio, (4) pop
detectado CONTANDO tótems antes/después (los plugins cancelan eventos),
(5) log + FX + actionbar. Con 3 pulsos sin efecto (god-mode de plugin) se
corta sola.

El daño perfora Netherite + Protection IV (`bypasses_armor` +
`bypasses_enchantments` en todos los damage types del mod).

## 5. Config (`config/cataclysm-common.toml` y `-client.toml`)

| Sección | Clave | Default | Qué toca |
|---|---|---|---|
| damage | `damageMultiplier` / `dotMultiplier` | 1.0 | daño directo / sostenido |
| damage | `knockbackStrength` / `windStrength` | 1.0 | ondas expansivas / viento sostenido |
| damage | `sustainedDamage` / `lethalStrikes` | true | aflicciones / golpes killIfNoTotem |
| terrain | `terrainDestruction` | true | cráteres/fisuras/cono/agua reales |
| terrain | `globalBlockBudgetPerTick` | 2000 | presupuesto GLOBAL de bloques/tick (nunca congela el tick) |
| terrain | `maxCraterRadius` / `maxFissureDepth` | 16 / 30 | caps duros |
| concurrency | `maxTierMPerPlayer` / `maxTierCPerPlayer` | 5 / 2 | cupos simultáneos |
| concurrency | `maxPendingPerPlayer` / `maxTierSQueue` | 10 / 300 | colas (llenas ⇒ mensaje `queue_full`, jamás silencio) |
| streamer | `streamerMode` | **true** | modo live |
| streamer | `totemShredder` / `shredAutoRefill` | true | trituradora / auto-recarga de offhand |
| streamer | `shredIntervalTicks` | 4 | cadencia (4 = 5 pops/s; mínimo 2) |
| streamer | `shredBudgetMultiplier` / `executionPopBudget` | 1.0 / 40 | presupuestos de pops |
| streamer | `followAcrossDimensions` | true | los desastres te persiguen al End |
| feedback | `broadcastMessages` | true | sirenas/títulos globales tier U |
| clientFx | `postShader` / `fxDensity` / `flashOverlay` / `screenShake` | true / 1.0 / true / true | FX de cliente (fxDensity 0.25–2.0) |

## 6. Effeks (Effekseer vía AAA Particles) — guía

El mod trae sus FX nativos COMPLETOS de serie; tus effeks los **elevan**
cuando están. El puente es 100 % reflexión: sin AAA Particles instalado no
pasa nada (un log y sigue).

1. Instala en el **cliente** (y opcionalmente server): mod
   `aaa_particles-1.20.1-1.4.x-forge` + **Architectury API** (los pide
   AAA Particles; de CurseForge/Modrinth).
2. Suelta tus `.efkefc` (con su carpeta `Texture/` al lado) en el jar o en
   un resource pack bajo:
   `assets/cataclysm/effeks/<carpeta>/<nombre>.efkefc`
3. Edita `assets/cataclysm/effeks/manifest.json`: clave = evento (minúsculas,
   la lista de la sección 3), valor = ruta SIN extensión
   (`"lightning_bolt": "rayos/mi_rayo"`), `""` = solo FX nativos.
4. `F3+T` (recarga de recursos) y listo — **sin tocar código**. La escala
   del effek va ligada a la intensidad del evento.

Dónde brillan: rayos con ribbons (`lightning_bolt`), ondas con rings
(`shockwave_ring`), el embudo (`tornado_debris`), el flash del impacto
(`impact_flash`), portales cósmicos (`apocalypse_omen`).

## 7. Para tu overlay (log markers parseables)

```
[cataclysm] session start id=7 tier=C attack=tornado target=vvrgs
[cataclysm] session reanchor id=7 attack=tornado dim=minecraft:the_end pos=(...)
[cataclysm] totem pop target=vvrgs n=12 left=87
[cataclysm] shred start/end target=... reason=...
[cataclysm] session end id=7 reason=finished
```

Razones de `session end`: `finished`, `drained`, `timeout`, `stopall`,
`target_death`, `target_logout`, `target_missing`, `target_unstable` (un
plugin lo rebota en bucle), `no_ground` (void del End), `exception`,
`server_stopping`, `executed`, `survived`.

## 8. Previews (sin abrir Minecraft)

En `previews/`: beauty shots y turntables GIF de las 5 entidades (tornado,
bólido, bomba volcánica, muro de tsunami, impactor) renderizados por
software desde la MISMA geometría y texturas del juego, más la secuencia de
la coreografía del `/disaster impacto`. Regenerables con `python3 tools/preview_render.py`.

## 9. Regenerar assets (Python 3.11 + Pillow + numpy + soundfile)

```bash
python3 tools/specs.py          # SPECS -> Geometry.java + atlas de entidades
python3 tools/gen_particles.py  # 39 sprites de partículas (64px, supersampleo x4)
python3 tools/gen_sounds.py     # 21 OGGs mono sintetizados (loops sin costura)
python3 tools/preview_render.py # previews
```

`tools/specs.py` es la **fuente única de verdad** de los modelos: cambia ahí
la geometría y regenera; `Geometry.java` está marcado como generado.

## 10. Arquitectura (resumen para tocar el código)

- `core/DisasterManager` — tick loop snapshot-safe, tiers S/M/C/U con cupos,
  colas, exclusión global de ultras y los **3 hooks de limpieza**
  (ServerStopping, PlayerLoggedOut, LivingDeath).
- `core/DisasterSession` — base: MAX_LIFETIME duro, target resuelto
  globalmente cada tick vía playerlist, re-anclaje anti-teleport (cambio de
  dimensión SIEMPRE; salto >48 bl con cooldown 40; 5 rebotes → corta;
  guard de vacío en el End).
- `core/TerrainBudget` — TODO el terreno pasa por un presupuesto global de
  bloques/tick con guards (nunca bedrock, nunca cofres, fluidos solo del
  efecto).
- `disasters/` — una sesión por desastre con su coreografía en ticks.
- `fx/` + `client/fx/` — el server describe QUÉ pasó (un packet por evento);
  el cliente lo expande en recetas cronológicas (partículas custom, shake
  trauma², flash, post-shaders con auto-off si Iris/Oculus, cielo con
  impactor creciente y agujero negro).
- `compat/AAABridge` — puente Effekseer por reflexión, auto-desactivable.
