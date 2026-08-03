# ⚔️ IRON TEMPEST — Arsenal de Guerra (Forge 1.20.1)

Mod de guerra cinemático dirigido al jugador: **tanque de asalto con IA de
torreta, misiles de crucero guiados, lluvia de cohetes MLRS, nave orbital con
haz de energía y modo ARMAGEDÓN**. Motor de FX propio (cero partículas
vanilla), shaders GLSL propios, post-proceso de onda de choque, 18 sonidos
sintetizados, animación procedural y puente Effekseer opcional.

Diseñado para **regalos de TikTok Live**: cada ataque es un comando de consola
que StreamToEarn/TikFinity ejecuta vía ServerTap.

---

## Comandos (contrato TikTok)

Todos requieren permiso 2 (consola del server ya lo tiene). Sin `[jugador]`,
atacan al **primer jugador online** (para regalos: pon el nick fijo).

| Comando | Tier | Monedas sugeridas | Qué hace |
|---|---|---|---|
| `irontempest rocketrain [jugador] [salvas]` | S | 1–30 | +6 cohetes a la cola; drena 1 cada 4 ticks. Spamea 100 veces sin miedo: es UNA cola |
| `irontempest cruisemissile [jugador]` | M | 100–700 | Silo con klaxon → boost vertical → crucero → homing terminal → detonación multi-fase. Hasta 5 a la vez en abanico |
| `irontempest tankblitz [jugador]` | C | 1000–3000 | Drop-pod → torreta que te caza (2.2°/tick) → designador láser → 5 obuses con predicción de tiro → cortina de humo y desguace |
| `irontempest orbitalstrike [jugador]` | C | 1000–3000 | Warp-in → carga 4 s → haz orbital que TE PERSIGUE vaporizando el suelo → pulso final → warp-out |
| `irontempest armageddon [jugador]` | U | 4880+ | TODO el arsenal orquestado en oleadas + sirena y título global. Exclusión global: uno a la vez |
| `irontempest stopall` | — | — | Corta todas las sesiones activas |

Prueba rápida en consola del server (Mohist):
```
irontempest tankblitz TuNick
```
Smoke test: busca en el log `[irontempest] session start id=1 tier=C attack=tankblitz`
y su `session end` correspondiente. Sin stacktraces = verde.

## Build (en tu máquina Windows)

Requisitos: JDK 17 (el `gradlew` descarga Gradle 8.1.1 y Forge solo).

```powershell
cd mods\irontempest
.\gradlew build
# → build\libs\irontempest-1.0.0.jar
```

> OneDrive gotcha: si el build falla por archivos bloqueados, copia el
> proyecto fuera de OneDrive o pausa la sincronización (referencia:
> build-gotchas del skill).

Deploy: copia el jar a **cliente** (`Instances\ss\mods`) y **server**
(`minecraftServer\mods`). Es el mismo jar para ambos. Verifica hash tras
copiar (jar parcial = NoClassDefFoundError).

## Config (`config/irontempest-common.toml`, se genera al primer arranque)

- `damage.damageMultiplier` — 0 = solo espectáculo, sin daño.
- `damage.lethalStrikes` — impacto directo mata salvo tótem (el tótem SIEMPRE se respeta).
- `damage.sustainedDamage` + `dotMultiplier` — daño SOSTENIDO: cada impacto deja el
  cráter ardiendo 3–8 s (pisa y te quemas) y los alcanzados siguen recibiendo daño
  en pulsos cada 0.5 s aunque corran (cohete 3 s, obús 3 s, misil 4 s, roce del haz 4 s,
  sobrecarga 5 s), con fuego real encima.
- `damage.knockbackStrength` — FÍSICA de onda expansiva: las explosiones empujan de
  verdad (impulso con falloff y sesgo vertical, sincronizado al cliente).
- `terrain.terrainDestruction` / `globalBlockBudgetPerTick` — cráteres on/off y presupuesto global amortizado.
- `concurrency.*` — cupos por tier (5 misiles/jugador, cola de 300 cohetes…).
- `clientFx.postShader` — distorsión de pantalla (se autodesactiva con Oculus/Iris).

## Effekseer (opcional, sin tocar código)

Si el cliente tiene **AAA Particles** instalado, el mod dispara además un
`.efkefc` por evento. Crea el effek con el skill effekseer y suéltalo en:

```
assets/irontempest/effeks/<carpeta>/<nombre>/<nombre>.efkefc  (+ Texture/ al lado)
```

y apunta la clave del evento en `assets/irontempest/effeks/manifest.json`
(claves: `explosion_large`, `muzzle_flash`, `warp_in`, `beam_sweep`, …).
Ruta SIN extensión e INCLUYENDO el nombre: `explosion_large/explosion_large`.
Sin AAA Particles el mod funciona igual con su motor de FX nativo.

## Arquitectura (para tocar código)

```
server/session/   SessionManager (3 hooks cleanup + colas por tier) + 5 sesiones
server/util/      TerrainSculptor (destrucción amortizada con guardas) + DamageUtil (killIfNoTotem)
entity/           Tanque (torreta synced), misil (nav proporcional), cohete, nave, obús
net/              SimpleChannel: FxPacket → FxDirector (cliente)
client/fx/        FxDirector (cronologías), ScreenShake (trauma²), FlashOverlay, PostFxManager
client/render/    Renderers procedurales + RenderTypes + shader del haz (GLSL core)
client/particle/  11 partículas propias (física, rebotes, anillos, trazadores)
client/model/geom/ Geometría GENERADA por tools/gen_models.py (no editar a mano)
tools/            Generadores: modelos+texturas, sprites de partículas, sonidos
```

Reglas del proyecto: destrucción SIEMPRE amortizada (presupuesto global),
3 hooks de cleanup en el manager, `MAX_LIFETIME_TICKS` en toda sesión,
log-markers `session start/end`, cero `catch {}` vacíos.

## Créditos de assets

Todo generado proceduralmente en este repo (texturas, sprites, OGG por
síntesis): sin assets de terceros, sin problemas de copyright para stream.
