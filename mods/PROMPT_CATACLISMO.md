# PROMPT MAESTRO — Mod "CATACLISMO": desastres naturales y eventos cósmicos

> Copia TODO este documento y pégalo como encargo. Es autocontenido: no
> asume ninguna conversación previa. Cada regla existe porque ya nos costó
> un bug aprenderla — no negocies ninguna.

---

## 1. Misión y listón de calidad

Crea un mod para **Minecraft Forge 1.20.1 (47.2.0, mappings oficiales de
Mojang, Java 17, ForgeGradle 6 + Gradle 8.1.1)** llamado **Cataclismo**
(modid `cataclysm` o similar): desastres naturales y eventos cósmicos
**dirigidos a un jugador concreto**, disparados por comandos desde chat o
consola. Es un mod standalone (proyecto propio, no una extensión de otro).

El listón: **como si fuera un mod de 1 millón de dólares**. Detalle
milimétrico, nunca la ruta fácil, nada genérico, nada barato. Cada desastre
es una COREOGRAFÍA cronológica fase a fase (ticks exactos), no "una
explosión con partículas". **Cero partículas vanilla**: motor de FX propio
(partículas custom, shaders, screen shake, sonido sintetizado propio), con
puente opcional a Effekseer. Entrega la **versión final pulida de una vez**:
no un prototipo a iterar, sino el resultado de iterar tú internamente hasta
que esté perfecto.

## 2. Contexto del usuario (CRÍTICO — el mod se diseña PARA esto)

Soy streamer de TikTok. Hago lives donde **el objetivo de mis viewers es
MATARME**: sus regalos ejecutan comandos de consola contra mí. Adapta todo a
esto:

- **Tótems**: llevo ~150 slots de inventario con tótems de la undying.
  IMPORTANTE (bug real aprendido): los tótems **NO se apilan**
  (`maxStackSize=1`) y **solo salvan desde la mano** — cualquier sistema de
  "romper tótems en cadena" DEBE auto-recargar la offhand desde el
  inventario antes de cada golpe, o la cadena muere en el primer pop.
- **Armadura**: Netherite completo con Protection IV. El daño sostenido debe
  llevar `bypasses_armor` **y** `bypasses_enchantments` o no me hará cosquillas.
- **El espectáculo son los pops**: quiero cadenas RÁPIDAS de tótems
  reventando, 5–7 pops/segundo configurables ("bajando ticks"). La ventana
  de invulnerabilidad vanilla (10 ticks) se salta con damage type con
  `bypasses_cooldown` + poner `entity.invulnerableTime = 0` antes de cada
  golpe (cinturón y tirantes: en Mohist los plugins tocan esa ventana).
- **El mundo no importa**: lo reinicio cada live. La destrucción masiva de
  terreno es DESEABLE (cráteres reales, fisuras reales, mapa lunar).
- **Uso trucos NMS y muchos plugins**: el mod debe ser defensivo (nunca
  asumir estado limpio) y convivir con un servidor **Mohist 1.20.1**
  (híbrido Forge+Bukkit).
- **Otros plugins me TELETRANSPORTAN** (al End, al cielo, desplazamientos
  por explosiones): TODO desastre debe PERSEGUIRME siempre, incluso entre
  dimensiones (ver §7).
- **Visibilidad**: en mis lives hay spam de efectos de otros mods. Cada
  desastre debe anunciarse y leerse solo (ver §8).

## 3. Contrato de comandos (TikTok → consola)

- Un comando Brigadier por desastre: `/volcan <jugador>`,
  `/terremoto <jugador>`, `/tornado <jugador>`, etc. (nombres en español,
  sin mayúsculas ni tildes en el literal del comando).
- **Fallback de consola obligatorio**: si la source no es un jugador y no se
  pasa argumento, apuntar al **primer jugador online**. Los regalos de
  TikTok (Stream to Earn / TikFinity / ServerTap) ejecutan comandos de
  CONSOLA — este fallback es el contrato principal, no un extra.
- `permission level 2`, `/<modid> stopall` que corta TODO y limpia TODO.
- Cola de ataques con límite y feedback: si está llena, mensaje
  `queue_full` traducible — jamás fallar en silencio.
- Los comandos spameables (tier S) NO crean una sesión por comando: 100
  comandos = 100 incrementos de UNA cola por jugador que drena a ritmo fijo.

## 4. Catálogo de desastres (tiers + coreografía completa)

Sistema de **tiers** con cupos de concurrencia configurables:
- **S (spameable)**: barato, ilimitado razonable, cola que drena.
- **M (solapable)**: hasta N simultáneos por jugador (offset espacial por
  índice para que 5 a la vez se lean como abanico, no amasijo).
- **C (cinemático)**: 1–2 a la vez, set-piece con entidades propias.
- **U (ultra)**: exclusión GLOBAL (uno por servidor), el gran final.

Para CADA desastre define: cronología en ticks fase a fase, física, FX
propios, sonido, daño (directo + sostenido), terreno, presupuesto de pops de
tótem, bossbar/título, y comportamiento ante re-anclaje (§7). Catálogo
mínimo (añade los tuyos si mejoran el show):

1. **`/meteoros` (S)** — lluvia de meteoritos pequeños: retícula telegraph
   en el suelo ~1 s antes de cada impacto, bólidos con estela de plasma y
   spin, cráteres r=2, zona ardiente 3 s. Cola tipo lluvia (1 cada 4 ticks).
2. **`/rayo` (S)** — rayo dirigido REAL: 8 ticks de ionización visible
   (motas ascendentes + actionbar), descarga con rama fractal propia (no el
   LightningBolt vanilla), trueno con retardo por distancia, 2 pops.
3. **`/tormenta` (M)** — tormenta eléctrica sobre mí: célula de nubes negras
   bajas (FX), rayos encadenados cada 30–50 ticks que ME buscan (predicción
   de movimiento), viento lateral físico con ráfagas, 60 s.
4. **`/fuego` (M)** — tormenta de fuego: frente de llamas que avanza hacia
   mí, brasas ascendentes a contraluz, ignición del terreno en parches,
   DoT napalm que ignora armadura, columnas de humo que oscurecen el cielo.
5. **`/tornado` (C)** — embudo procedural (entidad con modelo/render
   propio, malla cónica animada girando): nace lejos, PERSIGUE al jugador
   a 0.4 b/t, succiona físicamemte entidades e ítems en radio 12 (impulso
   tangencial+ascendente real), arranca bloques de superficie (amortizado)
   que orbitan visualmente el embudo, me lanza al cielo si me atrapa,
   escombros al morir. 45 s.
6. **`/huracan` (C)** — huracán con OJO: 30 s de bandas de lluvia+viento
   físico creciente (empuje lateral sostenido con ráfagas), paso del ojo
   (15 s de calma sobrecogedora, cielo amarillo, silencio total — el
   contraste ES el efecto), segunda pared peor que la primera. Los
   proyectiles/ítems derivan con el viento.
7. **`/terremoto` (C)** — terremoto que PARTE EL SUELO: 5 s de temblor
   creciente (screen shake trauma², bloques de polvo, sonido subterráneo),
   luego una FISURA REAL se abre bajo mis pies: zanja de 3–5 de ancho,
   20–30 de profundidad, 40+ de largo, excavada amortizada tick a tick con
   los bordes desmoronándose; me TRAGA (succión hacia la grieta), réplicas
   decrecientes. La fisura queda en el mapa.
8. **`/volcan` (C/U)** — un VOLCÁN NACE cerca de mí: el suelo se abomba
   (bloques elevándose por fases), cono de basalto/magma creciendo 15 s,
   erupción: bombas volcánicas balísticas dirigidas A MÍ (telegraph donde
   caerán), fuentes de lava, ríos de lava reales pendiente abajo, flujo
   piroclástico final (muro gris que avanza radial y mata — salvo tótem),
   lluvia de ceniza que dura 60 s más. El cono QUEDA en el mapa.
9. **`/tsunami` (C)** — muro de agua de 8–12 de alto y 40 de frente que
   avanza desde la distancia (se VE venir), espuma y bruma en la cresta,
   arrasa (impulso masivo + agua real temporal colocada/retirada
   amortizadamente), resaca que me arrastra de vuelta, escombros flotando.
10. **`/impacto` (U)** — IMPACTO PLANETARIO, el gran final cósmico: T+0
    aviso global (sirena+título a todo el server, el cielo se tiñe), T+3s
    un punto de luz CRECE en el cielo (se ve venir — entidad con render
    a escala, no una partícula), T+8s entrada atmosférica (estela que cruza
    el cielo, sonido rasgado), T+10s impacto a 30–60 bl de mí: flash
    blanco total, onda expansiva visible que avanza (anillo), cráter r=12+
    excavado amortizado, eyecta balística incendiaria, hongo de polvo,
    oscurecimiento del cielo 90 s, trituradora de tótems a tope en la zona.
11. **`/apocalipsis` (U)** — el armagedón natural: director de oleadas que
    ENCADENA los anteriores con timeline exacto (meteoros → rayos →
    tornado → terremoto → volcán → impacto final), bossbar con el timeline
    completo, sirena global. Reutiliza las sesiones existentes, no
    reimplementa nada.
12. **`/ejecucion_natural` (U)** — equivalente a una ejecución: el desastre
    me ANCLA (no puedo huir: succión física hacia el punto), trituradora
    de tótems a cadencia máxima con contador en pantalla hasta agotar
    presupuesto configurable (default 40 pops), clímax si sobrevivo.

## 5. Reglas duras de ingeniería (no negociables)

- **Sesiones**: cada desastre activo es una sesión con id, tier, target
  (UUID), edad, y `MAX_LIFETIME_TICKS` duro — JAMÁS ticks huérfanos. Log
  markers por sesión: `[<modid>] session start id=… tier=… attack=…
  target=…` y `session end id=… reason=…` (mi overlay los parsea).
- **3 hooks de limpieza obligatorios**: `ServerStoppingEvent` (cerrar todo y
  vaciar colecciones estáticas — el tick loop ya no volverá a purgar),
  `PlayerLoggedOutEvent` y `LivingDeathEvent` del target (sus sesiones,
  aflicciones, shreds y colas mueren con él).
- **Terreno AMORTIZADO**: toda excavación/colocación (cráteres, fisura,
  cono del volcán, agua del tsunami) pasa por un presupuesto GLOBAL de
  bloques/tick configurable (~2000) con cola; guards: nunca bedrock, nunca
  fluidos fuente fuera del efecto, nunca blockentities con inventario, cap
  de radio. Un tick jamás se congela por terreno.
- **Anti-CME**: toda colección estática que se itere en el tick se itera
  sobre SNAPSHOT (`new ArrayList<>(...)`) + `removeIf` al final — un hurt
  puede matar → `LivingDeathEvent` → limpieza reentrante DENTRO del mismo
  tick (bug real).
- **Excepciones**: el tick de una sesión va en try/catch; una sesión rota
  se termina con reason `exception`, jamás tumba el server tick.
- **killIfNoTotem**: el golpe "letal" es un hurt de 10000 con damage type
  propio — mata SALVO que el tótem salve (ver §6). Nada de `setHealth(0)`.

## 6. La regla SAGRADA del tótem

**JAMÁS uses `bypasses_invulnerability` ni ningún mecanismo que impida al
tótem salvar.** El show de mis lives es exactamente ver los tótems reventar
en cadena. La trituradora de tótems correcta:

- Damage types **data-driven** (JSON en `data/<modid>/damage_type/` + tags
  en `data/minecraft/tags/damage_type/`): `bypasses_armor`,
  `bypasses_enchantments`, `bypasses_cooldown`, `is_explosion`/`no_knockback`
  según el caso. NUNCA `bypasses_invulnerability`.
- Pulso de trituradora: (1) auto-recargar offhand con un tótem del
  inventario (no se apilan, solo salvan en mano), (2) `invulnerableTime=0`,
  (3) killIfNoTotem, (4) detectar el pop CONTANDO tótems antes/después
  (no por eventos, que los plugins cancelan), (5) log
  `totem pop target=… n=… left=…` + FX de pop + actionbar con el contador.
- Presupuesto de pops por desastre (fusionable con cap ~90 activo),
  cadencia configurable (default 4 ticks = 5/s, mínimo 2), `failStreak`:
  3 pulsos sin efecto (god-mode de plugin, creativo) → cortar.
- Config global: `streamerMode` (default TRUE), `totemShredder`,
  `shredIntervalTicks`, `shredAutoRefill`, `shredBudgetMultiplier`.

## 7. Re-anclaje anti-teleport (los desastres ME PERSIGUEN)

Mis plugins me teletransportan al End, al cielo, a donde sea. Regla:

- El target se resuelve **globalmente** cada tick vía
  `server.getPlayerList().getPlayer(uuid)` — nunca `level.getEntity` (se
  pierde al cambiar de dimensión).
- Detección de relocación: cambio de `serverLevel()` O salto >48 bl en un
  tick. El cambio de dimensión re-ancla SIEMPRE (sin cooldown); los saltos
  de distancia tienen cooldown de 40 ticks. Contador de re-anclajes con
  PERDÓN a los 300 ticks; al 5.º rebote rápido → `end("target_unstable")`
  (un plugin rebotándolo — cortar, no spamear entidades).
- Recolocar entidades set-piece por **discard + respawn** (con su FX de
  entrada — se lee intencional). **NUNCA `teleportTo` cross-dim**: recrea
  la entidad vía NBT y si tu `addAdditionalSaveData` está vacío pierdes el
  estado (sesión huérfana — bug real). Estado sincronizado: setéalo ANTES
  de `addFreshEntity` para que viaje en el primer packet.
- **Guard de vacío** al recolocar en el End: probar varios rumbos con
  heightmap; sin suelo → `end("no_ground")`, jamás spawn sobre el void.
- Tras un `end()` disparado DENTRO del re-anclaje, el tick debe CORTAR
  (no re-añadir a la bossbar ni tickear la sesión muerta — bug real).
- Las aflicciones de daño sostenido y la trituradora también resuelven por
  playerlist: siguen quemándome/triturándome en el End.
- Config `followAcrossDimensions` (default true).

## 8. Presentación y visibilidad (leerse entre el caos)

- **Título+subtítulo** al target al arrancar cada desastre (color propio
  por desastre vía códigos § en los lang). Anti-pisado: máx 1 título/3 s
  por jugador; si no toca, degradar a actionbar. Mapa de cooldowns con poda
  al desconectar.
- **Bossbar por sesión cinemática** con progreso REAL (crecimiento del
  volcán, avance del tsunami, cuenta atrás del impacto, tótems devorados
  en la ejecución — que retenga el máximo, no caiga a 0 al acabar el
  shred), color/overlay por desastre, `setDarkenScreen` en los U. Ciclo de
  vida: crear en ctor, `removeAllPlayers()` en el end (cubierto por los 3
  hooks). Re-enganchar al jugador cada tick (idempotente, sobrevive TPs).
- **Actionbars**: cuenta atrás de impacto, contador de tótems restantes por
  pop, avisos de zona ("¡SAL DE LA GRIETA!"), cola pendiente en tier S.
- **Glow outline rojo** en toda entidad de desastre (tornado, bólidos,
  bombas volcánicas) vía scoreboard team + `setGlowingTag`. Limpieza en
  override de **`setRemoved`** (NO `remove()`: la descarga de chunks no
  pasa por remove y deja UUIDs huérfanos en scoreboard.dat — bug real) +
  purga total del team en ServerStarted y ServerStopping.
- **Telegraphs**: retícula pulsante en el suelo 1–2 s antes de CADA impacto
  puntual (meteorito, bomba volcánica, rayo). Receta visual distinta de las
  explosiones (anillo dorado + motas convergentes).
- Config `fxDensity` (0.25–2.0) multiplicando los counts de partículas en
  los helpers del director de FX.

## 9. Motor de FX propio (cero vanilla)

- **Partículas custom** (10+): registrar con `new SimpleParticleType(true)`
  — el flag es `overrideLimiter`; con `false` se cullean a >32 bloques y
  tus efectos lejanos desaparecen (bug real). Sprites generados por script
  Python (64px, supersampleados ×4), con física propia por tipo (ceniza que
  flota, brasas con drag, polvo balístico, gotas, plasma).
- **Director de FX**: un packet servidor→cliente por evento (enum de tipos)
  que el cliente expande en RECETAS CRONOLÓGICAS (scheduler de acciones con
  delay en ticks): flash → bola de fuego → onda → escombros → humo, cada
  uno en su tick exacto. Cola con cap (~4000) y `clear()` en
  logout/unload.
- **Screen shake** con modelo trauma² (se suma, decae, sacude cámara vía
  `ViewportEvent.ComputeCameraAngles`) y **flash overlay** con falloff por
  distancia. **Post-shader** de distorsión (pipeline propio) con
  auto-desactivado si Iris/Oculus está presente.
- **Shader core** GLSL para haces/columnas de energía (registrado en
  `RegisterShadersEvent`), RenderTypes aditivos para trazadores, retículas
  y beacons.
- **Puente Effekseer opcional**: si el cliente tiene AAA Particles,
  disparar un `.efkefc` por evento vía reflexión, mapeado por
  `manifest.json` (clave = nombre del evento en minúsculas). Sin AAA
  Particles todo funciona igual con el motor nativo.
- **Sonido**: OGGs mono sintetizados por script Python (sin copyright),
  loops sin costura para embudo/viento/lava, variantes de pitch. OJO:
  radio audible = 16 × volumen — los avisos lejanos (sirena, klaxon,
  tren del tornado) necesitan volumen 3.0+; los globales van por
  `playNotifySound` a cada jugador, no por radio.

## 10. Física y animación premium

- **Daño sostenido**: zonas ardientes/tóxicas en el terreno (pulso cada 10
  ticks a quien las pise, 3–8 s) + aflicciones que PERSIGUEN a la entidad
  (pulso cada 10 ticks aunque corra), con multiplicadores de config y
  perforación total en streamer mode. Snapshot-iteration (§5).
- **Impulso físico real** en ondas expansivas y viento:
  `setDeltaMovement` con falloff y sesgo vertical + `hurtMarked = true`
  para sincronizar al cliente. El viento del huracán/tornado es fuerza
  SOSTENIDA por tick, no un empujón.
- **Entidades set-piece** (tornado, bólido, muro de tsunami…): modelos
  procedurales generados por script Python (fuente única de verdad: SPECS
  → Geometry.java + atlas pintado), interpolación de red estilo Boat
  (override `lerpTo` con 3 pasos — sin él el yaw cuantizado a ~1.4° salta),
  animación procedural en el renderer por estado sincronizado. Reglas de
  renderer aprendidas a golpes: los `ModelPart` se hornean UNA vez y se
  COMPARTEN entre entidades (capturar poses base en el ctor y restaurar
  toda posición mutada tras el render; rotaciones siempre en absoluto,
  jamás `+=`); el orden de rotación de ModelPart es **ZYX** (un yRot junto
  al xRot de elevación PRECESIONA en vez de girar sobre el eje propio —
  verifica cada composición); documenta tu convención de yaw/pitch y no
  la mezcles entre entidades y proyectiles.
- **Texturas HD**: el generador pinta a 1 texel/unidad y supersamplea el
  lienzo ×2–×4 SIN tocar el atlas lógico (los UVs de `LayerDefinition`
  son normalizados — el PNG puede ser múltiplo exacto; no subas texWidth).
  Painters de calidad: AO suave + AO de CONTACTO entre cajas de la misma
  parte, paneles biselados, óxido/desgaste multi-escala, decals. Seeds
  ESTABLES por hash de (parte|cubo|material|cara) — un contador global
  re-aleatoriza todo al añadir un cubo (bug real). Texturas emisivas
  `_glow.png` (RenderType.eyes fullbright) para lava, plasma, núcleos.
- Todo escrolleo visual periódico (p. ej. una cinta/flujo con sawtooth)
  exige textura EXACTAMENTE periódica en ese eje — pinta una tira del
  período y tesela, o habrá shimmer en cada wrap (bug real).

## 11. Metodología de verificación (así se trabaja)

- Si el entorno no puede resolver los maven de Forge/Mojang: **stub-compile**
  — carpeta `verify/stubs/` con stubs mínimos de la API REAL (mismas firmas
  de 1.20.1 mojmap; si dudas de una firma, NO la inventes ni dobles el stub
  para que compile: verifica) + `verify/compile.sh` con `javac --release 17`.
  0 errores tras CADA bloque de trabajo.
- Lint de todos los JSON de resources; **paridad de claves** entre los 3
  lang: `es_es` (principal), `es_mx` (variantes mexicanas), `en_us`.
- **Rondas de revisión adversarial**: tras cada versión, revisar con ojos
  de enemigo los escenarios que históricamente rompen — fugas de bossbar,
  re-anclaje en cada fase de cada sesión, thrash de TPs, teams huérfanos,
  CME reentrante, overflow de enteros en relojes (`Integer.MIN_VALUE` en
  restas — bug real), coordenadas de una dimensión aplicadas en otra tras
  un TP (bug real), stubs vs API real. Corregir TODO antes de entregar.
- **Previews renderizados por software** (rasterizador Python con z-buffer
  que consume los SPECS y texturas reales): beauty shots + turntable GIF +
  secuencias de la coreografía, para que yo lo vea desde el móvil sin
  Minecraft. El preview debe replicar la quiralidad del juego (ZP(180)
  voltea X e Y — niega también X en la proyección o los decals se leen
  espejados; bug real).

## 12. Config y entrega

Config TOML por secciones: `damage` (damageMultiplier, dotMultiplier,
knockbackStrength/windStrength, sustainedDamage, lethalStrikes), `terrain`
(terrainDestruction, globalBlockBudgetPerTick, caps de radio/profundidad),
`concurrency` (cupos por tier, cola máxima), `clientFx` (postShader,
fxDensity, flashOverlay), `streamer` (§6 + followAcrossDimensions),
`feedback` (broadcastMessages). Defaults pensados para MIS lives (streamer
mode ON).

Entrega: README en español con build en Windows (`.\gradlew build`, JDK 17;
gotcha: OneDrive bloquea archivos — copiar fuera o pausar sync), deploy del
MISMO jar a cliente (`Instances\ss\mods`) y server Mohist
(`minecraftServer\mods`), verificación de hash tras copiar, tabla de
comandos para mapear regalos de TikTok, sección de config comentada, y los
previews. Commit y push al terminar cada versión con mensajes descriptivos.

---

**Resumen en una frase**: desastres naturales y cósmicos cinematográficos,
dirigidos a mí, que me persiguen entre dimensiones, revientan mis tótems en
cadena a 5–7/s sin saltarse jamás al tótem, destrozan el mapa de forma
amortizada, se anuncian con títulos/bossbars/telegraphs legibles entre el
caos, con FX 100% propios de calidad AAA — y todo verificado, revisado
adversarialmente y entregado pulido de una vez.
