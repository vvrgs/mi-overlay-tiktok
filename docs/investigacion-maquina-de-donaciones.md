# La Máquina de Donaciones — Investigación y diseño de mecánicas para TikTok Live

> Investigación basada en fuentes reales: papers académicos revisados por pares, datos de mercado
> y mecánicas documentadas de TikTok, Twitch, YouTube, Douyin, Bigo/Huya y juegos free-to-play.
> Objetivo: diseñar un overlay para TikTok Live donde los regalos causen efectos visibles y las
> mecánicas maximicen emoción, curiosidad y donaciones — usando lo que la ciencia dice que funciona.
>
> Fecha de investigación: agosto 2026. Todas las citas fueron verificadas vía búsqueda web;
> ninguna referencia es inventada. Las fuentes de industria (no académicas) se marcan como tal.

---

## 1. Resumen ejecutivo

**La conclusión central de toda la evidencia:** la gente NO dona por el contenido. Dona por
**reconocimiento público, estatus visible, pertenencia a un grupo y agencia** (poder causar algo
en el live). Las plataformas más rentables del mundo construyen sus sistemas de regalos sobre
6 patrones que se repiten en todas:

1. **Estatus visible proporcional al gasto** — badges, colores, tamaños de animación, rankings.
2. **Reconocimiento inmediato y nominal** — tu nombre en pantalla, el streamer te lee en voz alta.
3. **Metas visibles con deadline** — barras de progreso, timers, multiplicadores por tiempo limitado.
4. **Competición tribal** — batallas, equipos, leaderboards, guerras de gifters.
5. **Recompensa variable** — ruletas, resultados impredecibles, "no sabes qué va a pasar".
6. **Agencia comprada** — el regalo es un botón que causa un efecto real e inmediato en el live.

**Los tres mecanismos con mejor evidencia científica** (tanto en papers de streaming como en
psicología experimental) son:

- **(a) Reconocimiento inmediato y nominal del donante** — es el predictor #1 de disposición a
  regalar en el estudio experimental más reciente sobre TikTok Live (CHB 2025), y coincide con
  el clásico de Ariely (2009): la gente es significativamente más generosa cuando el acto es público.
- **(b) Visibilidad del regalo ante la comunidad** — el gasto en lives es consumo conspicuo:
  se compra estatus ante los demás espectadores (Su et al. 2020; Li et al. 2021; Liu et al. 2025).
- **(c) Metas visibles con barra de progreso y deadline** — el goal-gradient effect: la gente
  dona más cuanto más cerca está la meta (Kivetz 2006; Cryder 2013 con datos de Kiva;
  Kuppuswamy con 2 años de datos de Kickstarter; Games Done Quick recauda >$1.5M por evento así).

**El dato de negocio más importante:** el ingreso está brutalmente concentrado. Entre 1.5% y 4%
de los espectadores envía al menos un regalo por sesión, y dentro de ese grupo una fracción
minúscula ("whales") genera la mayoría del dinero (en gaming móvil, <1% de usuarios genera >50%
del revenue; Amouranth reportó que 3–5 personas le donan >$90,000/año cada una). **El overlay debe
tener una escalera completa: micro-regalos de 1 coin para la masa Y experiencias de estatus
espectaculares para los 5–10 whales de tu comunidad.**

---

## 2. Los datos duros del mercado

| Dato | Cifra | Fuente |
|---|---|---|
| Gasto in-app en TikTok 2024 (mayoría: coins para regalos) | >$6,000M/año — primera app de la historia en lograrlo | Sensor Tower, Digital Market Index Q4 2024 |
| Gasto acumulado histórico en TikTok | >$15,000M | Sensor Tower |
| % de ingresos del streaming chino que viene de regalos virtuales | >85% (no publicidad, no suscripciones) | iiMedia Research / Wowza |
| % de viewers que envía al menos un regalo por sesión (TikTok Live) | 1.5%–4% (estimación de industria) | ttsvibes insights |
| Concentración del gasto | <1% de usuarios genera >50% del revenue IAP en móvil; patrón idéntico en streaming | Udonis; caso Amouranth |
| Creadores de TikTok Live en EE.UU. que ganan más que la mediana de un medio tiempo | >60,000 | Ipsos/TikTok 2025 (Tubefilter) |
| Ingresos de batallas PK vs live normal | 3–5x más por sesión | streamwrapped / ttcalculator (industria) |
| Rango de precios de regalos en Douyin | ¥0.1 a ¥6,666 — 4 órdenes de magnitud | Qiu & Klug 2021 (arXiv:2108.05960) |
| Por qué la gente regala (encuesta Ipsos/TikTok, n≈500) | 35% "mostrar aprecio", 34% "destacar/ser notado en la comunidad", 32% interactuar, 31% celebrar un hito | Tubefilter, dic. 2025 |

Fíjate en el segundo motivo de la encuesta de Ipsos: **"ser notado dentro de la comunidad"
empata prácticamente con "mostrar aprecio"**. La mitad de la motivación de regalar es social,
no altruista. Ese es el hueco que el overlay debe explotar.

---

## 3. La ciencia: 12 principios psicológicos con sus fuentes primarias

Estos son los principios que los equipos de diseño de Meta, YouTube, TikTok, casinos y juegos
free-to-play usan (documentadamente) para diseñar engagement y gasto. Cada uno tiene el estudio
original verificado.

### 3.1 Refuerzo intermitente (razón variable) — Skinner 1953
La recompensa impredecible genera conducta mucho más persistente que la predecible. Es el
principio de la tragamonedas, documentado etnográficamente en la industria del juego por
Natasha Schüll (*Addiction by Design*, Princeton, 2012) y codificado como receta de producto por
Nir Eyal (*Hooked*, 2014: trigger → acción → **recompensa variable** → inversión).
**Aplicación al overlay:** ruletas, cofres, resultados aleatorios al donar.

### 3.2 Goal-gradient effect — Hull 1932; Kivetz, Urminsky & Zheng, *JMR* 2006
El experimento de la tarjeta de café: una tarjeta de 12 sellos con 2 ya regalados se completa
MÁS RÁPIDO que una de 10 vacía (mismo esfuerzo real). La gente acelera al acercarse a la meta,
y el "progreso regalado" (endowed progress) funciona.
**Aplicación al overlay:** barras de meta que NUNCA empiecen en 0% — arranca el live con la barra
al 15–20% ("los primeros fans ya aportaron").

### 3.3 Aversión a la pérdida / efecto dotación — Kahneman & Tversky 1979; KKT 1990
Las pérdidas pesan ~2x más que las ganancias equivalentes. Poseer algo (aunque sea simbólico)
aumenta su valor percibido. Es la base de las rachas de Duolingo/Snapchat: la racha no vale nada,
pero perderla duele.
**Aplicación al overlay:** rachas de asistencia/apoyo del viewer, títulos que expiran, win streaks.

### 3.4 Social proof y reciprocidad — Cialdini 1984; Regan 1971
Mostrar la lista de quienes ya donaron aumenta la donación del siguiente; recibir un favor
no pedido (Regan: un refresco) dispara la compra recíproca.
**Aplicación al overlay:** feed visible de últimos donantes; el streamer "regala" shoutouts y
atención (reciprocidad) que la audiencia siente que debe devolver.

### 3.5 Dopamina y error de predicción de recompensa — Schultz, Dayan & Montague, *Science* 1997
La dopamina no codifica el placer sino la **anticipación** y la sorpresa. Tras el aprendizaje,
la neurona dispara ante la SEÑAL que predice la recompensa, no ante la recompensa misma.
**Aplicación al overlay:** separa la señal del resultado — redoble de tambores, animación de
"cargando" de 2–3 segundos antes de revelar qué salió en la ruleta o qué efecto se disparó.
La pausa ES el producto.

### 3.6 Escasez y urgencia — Worchel, Lee & Adewole, *JPSP* 1975
El experimento de las galletas: galletas idénticas se valoran más cuando quedan 2 que cuando
quedan 10, y aún más si la escasez la causó "la demanda de otros".
**Aplicación al overlay:** ventanas de multiplicador x2 de 60 segundos, efectos "solo disponibles
hoy", contadores regresivos.

### 3.7 Motivación de imagen — Ariely, Bracha & Meier, *American Economic Review* 2009
Experimento "Click for Charity": la gente dona significativamente más cuando el acto es **público**
que cuando es privado. Bonus: pagar a la gente por donar en público REDUCE el efecto (diluye la
señal reputacional).
**Aplicación al overlay:** todo regalo debe ser un evento público con nombre; jamás anónimo por defecto.

### 3.8 Fiebre de subasta / arousal competitivo — Ku, Malhotra & Murnighan, *OBHDP* 2005
Con datos de subastas reales: rivalidad + presión de tiempo + audiencia genera una activación
emocional que hace que la gente **supere los límites de gasto que ella misma se fijó**.
**Aplicación al overlay:** batallas con timer, duelos de gifters, "¿quién corona al streamer?".

### 3.9 Motivación basada en identidad — Oyserman, *JCP* 2009; Tajfel & Turner 1979
Cuando una identidad de grupo está activa (badge, color, nombre de equipo), la conducta que la
señala (gastar por el equipo) se vuelve atractiva por sí misma.
**Aplicación al overlay:** nombre propio para la comunidad, equipos con colores en las batallas,
badges de pertenencia.

### 3.10 Efecto near-miss — Clark et al., *Neuron* 2009
En fMRI: "casi ganar" en una tragamonedas es menos placentero que perder del todo, pero
**aumenta el deseo de seguir jugando** y activa los circuitos cerebrales de la victoria.
Solo funciona cuando el sujeto siente control sobre su jugada.
**Aplicación al overlay:** la ruleta que se detiene JUSTO al lado del premio gordo; "te faltaron
20 coins para el nivel 2". (⚠️ Ver sección 7: este principio bordea lo manipulativo — úsalo con criterio.)

### 3.11 Coste hundido — Arkes & Blumer, *OBHDP* 1985
Experimento de los abonos de teatro con precio aleatorizado: quienes pagaron precio completo
asistieron a más funciones que quienes (al azar) recibieron descuento. Lo ya invertido gobierna
la conducta futura.
**Aplicación al overlay:** niveles acumulativos de por vida que nunca se resetean (como los
Gifter Levels de TikTok), historial visible de apoyo total.

### 3.12 Dolor de pagar y desacople — Prelec & Loewenstein, *Marketing Science* 1998
Cuanto más acoplados están pago y consumo, más duele gastar. Las monedas intermedias compradas
por adelantado (coins de TikTok, V-Bucks, Robux) anestesian el dolor: un regalo de 44,999 coins
"no se siente" como $562. Confirmado en streaming: el estudio de CHB 2025 sobre TikTok halló que
quienes ya tienen coins precargadas regalan más ("gifting elasticity").
**Aplicación al overlay:** habla siempre en coins/regalos, nunca en dinero; incentiva la recarga
temprana ("los que ya tienen rosas listas para la batalla de las 9pm...").

---

## 4. Qué dice la investigación académica específica sobre lives

Lo más relevante de ~30 estudios revisados (streaming en TikTok, Twitch, Douyin, Douyu):

### El reconocimiento es el predictor #1
- **"Diamonds are a TikTokers' best friend" (Computers in Human Behavior, 2025)** — estudio mixto
  en Reino Unido (12 entrevistas + experimentos 2×2): el **reconocimiento del streamer** es el
  predictor positivo significativo de la disposición a regalar.
- **Li, Lu, Ma & Wang (Information & Management, 2021)** — los espectadores que quieren ser
  **notados y reconocidos** por el streamer son los que gastan; el deseo de estatus en la
  plataforma ("identidad de clase") y de relación con el streamer impulsan el gifting.
- **Kim et al. (Univ. of Michigan, 2025; 8,068 streams, 956,328 usuarios de Twitch)** — el gifting
  es **contagioso**: quien recibe una sub regalada tiene más probabilidad de regalar después
  ("pay it forward")… pero **los regalos anónimos NO contagian**. La identidad visible del
  donante es la que propaga la conducta.

### La relación parasocial y la comunidad mueven el dinero
- **Wohn, Freeman & McLaughlin (CHI 2018, n=230)** — la relación parasocial correlaciona con el
  apoyo financiero al streamer.
- **Hilvert-Bruce et al. (CHB 2018, n=2,227 usuarios de Twitch)** — interacción social y sentido
  de comunidad son los únicos motivadores significativos de TODOS los tipos de engagement,
  incluido el económico. Bonus: los canales **pequeños** (<500 viewers) tienen audiencias MÁS
  motivadas socialmente — ventaja para creadores medianos.
- **Sjöblom & Hamari (CHB 2017, n=1,097)** — la motivación de **pertenencia** es el principal
  predictor del gasto en suscripciones.
- **Cao et al. (Frontiers in Psychology, 2022)** — la gente pasa de regalos baratos a caros cuando
  percibe intimidad con el streamer; y ojo: en salas percibidas como ENORMES el regalo "se nota
  menos" y la gente escala menos. En una sala mediana tu regalo brilla más — véndelo así.
- **Contrapunto honesto: Zhang (Frontiers in Psychology, 2022)** encontró en Douyu solo una
  correlación débil-media entre relación parasocial y gifting. La relación no es automática;
  necesita las mecánicas de visibilidad para convertirse en gasto.

### El estado emocional del streamer es una palanca medible
- **Lin, Yao & Chen (Journal of Marketing Research, 2021; datos minuto a minuto de 1,450 streams)**
  — "Happiness Begets Money": un streamer más feliz contagia a la audiencia y eso intensifica
  las propinas, cuantificado con panel VAR.
- **Internet Research (2023; deep learning sobre 1,809 horas de video y 358,002 regalos)** —
  las expresiones de felicidad y sorpresa del streamer aumentan el gifting (y curiosamente la
  tristeza también puede, vía "apoyo/consuelo").
  **Implicación de diseño:** el overlay debe hacer FÁCIL que el streamer reaccione en grande
  (efectos que lo sorprendan a él también → sorpresa genuina → más regalos).

### Las metas con progreso funcionan, con matices
- **Cryder, Loewenstein & Seltman (JESP, 2013; datos de campo de Kiva)** — la tasa de contribución
  **aumenta al acercarse la meta**; contribuir en el tramo final da mayor sensación de impacto.
- **Kuppuswamy & Bayus (2 años de datos de Kickstarter)** — patrón en U: el apoyo se dispara al
  principio y al final (deadline), sube al acercarse la meta y **CAE una vez alcanzada**.
  **Implicación:** cuando se complete una meta, revela la siguiente INMEDIATAMENTE (metas
  escalonadas), o el flujo muere.
- **Habib et al. (JMR, 2025; matiz importante)** — el goal-gradient clásico se sostiene cuando la
  causa se evalúa **aislada** (una sola barra en pantalla). Si muestras varias metas a la vez,
  la gente prefiere la más lejana. **Una sola barra de meta activa a la vez.**
- **Games Done Quick (CSCW 2019, etnografía + datos)** — las donaciones se agrupan alrededor de
  **incentivos con meta** ("si llegamos a $X, el runner juega en difícil") y **guerras de pujas**
  (los donantes compiten por decidir el nombre del personaje). GDQ recauda >$1.5M por evento con
  este diseño. Análisis independiente de AGDQ 2016 (30,528 donaciones): crecimiento exponencial
  en los últimos días — el deadline manda.

---

## 5. Catálogo: las mecánicas que ya usan las grandes plataformas

Referencia rápida de las 30 mecánicas documentadas (con números reales):

### TikTok / Douyin
| Mecánica | Cómo funciona | Principio que explota |
|---|---|---|
| **PK Battles** | 2 creadores, pantalla dividida, timer 3–5 min, regalos = puntos, ventanas x2/x3, castigo para el perdedor. Generan 3–5x el ingreso de un live normal | Competición tribal + deadline + espectáculo |
| **Top gifters de la sala** | Los 3 mayores gifters fijos arriba del live con avatar; "Gifter of the Week" semanal | Estatus + reseteo semanal (temporada corta) |
| **Gifter Levels 1–50** | Gasto acumulado de por vida, nunca se resetea. Nivel 10 ≈ $5.5; nivel 30 ≈ $6,625; nivel 40 ≈ $91,000. Nivel 25+ = entrada VIP animada a la sala | Coste hundido + estatus portátil |
| **Combo de regalos** | Mantener pulsado encadena x10/x99/x100 con contador de racha en pantalla | Fricción cero + slot machine social |
| **Rankings por hora/día/semana** | Rankings de diamonds con reseteo horario/diario; ligas multisemanales con marcos dorados de premio | Deadline recurrente ("quedan 10 min de la hora") |
| **Fan Clubs (Douyin)** | Badge con nombre del grupo + nivel de fan 1–20; sube con regalos E interacción diaria (nivel 20 ≈ $20,000 y 86 días) | Identidad + retención + gasto a la vez |
| **Wealth Level (Douyin)** | Badge global de gasto acumulado hasta nivel 75 (≈$2.8M); los whales son tratados como celebridades en cualquier sala | Jerarquía de casta visible |
| **Stream-to-earn (TikFinity)** | Regalo → comando en Minecraft/efecto en overlay por tiers de coins. Una rosa spawnea un cerdo; regalo caro = lluvia de TNT | **Agencia comprada** — el regalo es un botón |

### Twitch
| Mecánica | Cómo funciona | Principio |
|---|---|---|
| **Hype Train** | Se activa con 2+ contribuyentes en 5 min; cada nivel tiene timer de ~5 min que se reinicia al subir; hasta nivel 100+ (nivel 100 ≈ $500,000). Récords: Neuro-sama nivel 126 con 126,273 subs | Meta colectiva + pánico de "no dejar morir el tren" |
| **Bits/Cheermotes por tiers** | Animación proporcional al gasto: 1 bit gema gris → 10,000 roja gigante | Se compra visibilidad; metas de gasto discretas |
| **Leaderboard de gift subs** | Top gifters semanal/mensual/histórico junto al chat | Guerras entre 2–3 whales financian el canal |
| **Channel Points + Predictions** | Moneda gratis por mirar; apuestas parimutuel sobre el resultado del stream | Ancla la atención al resultado; monetiza tiempo |
| **Alertas + TTS + goals** | Donación → animación con nombre + mensaje leído en voz alta; barras de meta. Streamers con goals + celebración reportan 3–5x más ingreso que "el link en la bio" | Reconocimiento audible: 5 segundos de protagonismo |
| **Subathon (caso Ludwig)** | Cada sub añade tiempo al timer del stream; 31 días, 282,191 subs, récord histórico. El timer llegó a 1 segundo antes de ser "salvado" | Los viewers compran la EXISTENCIA del stream |
| **Ruleta de castigos** | Donación/umbral → giro de ruleta con castigos y retos en vivo | Recompensa variable + agencia + contenido garantizado |

### YouTube
| Mecánica | Cómo funciona | Principio |
|---|---|---|
| **Super Chat** | $1–$500; color y tiempo de pin proporcionales: $5 verde ~2 min → $100+ rojo hasta 5 h fijado con hasta 350 caracteres | Subasta de atención con feedback perfecto |
| **Super Stickers** | Stickers animados de pago, misma lógica de tiers | Baja la barrera: no hay que redactar nada |
| **Membership badges** | Badges que evolucionan con meses acumulados (1/2/6/12/24+) | Perder el badge de "2 años" duele → retención |

### Bigo/Huya (los más agresivos del mundo)
| Mecánica | Cómo funciona | Principio |
|---|---|---|
| **Sistema Noble** | Títulos mensuales de pago: Baron (~$20–58/mes) → Duke (~$800/mes). Entrada a sala de 5–25 s, chat de color, emotes por rango. **Expiran a los 30 días** con avisos a 7/3/1 días | Estatus perecedero: perder el título es pérdida pública |
| **Guardian system** | Rol único de "Guardián" del streamer, visible para todos, con reciprocidad emocional | Exclusividad relacional vendida |

### Free-to-play / gacha (la ingeniería más refinada)
| Mecánica | Cómo funciona | Principio |
|---|---|---|
| **Loot boxes** | Recompensa aleatoria de pago. Zendle & Cairns (PLOS ONE, n=7,422): el gasto escala con severidad de juego problemático ($11 → $38/mes); la ALEATORIEDAD es el mecanismo | Razón variable pura (⚠️ regulada en China/Bélgica/Países Bajos) |
| **Pity timer (Genshin)** | 0.6% base de 5★; "soft pity" desde el pull 74; garantizado en el 90. Peor caso acotado ≈ $150–200 | Acota el miedo a gastar + sunk cost medible ("estoy en pity 60") |
| **Battle pass** | 950 V-Bucks; completarlo devolvía 1,500 → jugar era "no perder dinero"; deadline de temporada | Sunk cost + earn-back + FOMO estructural |
| **Daily streaks** | Recompensa creciente por días consecutivos; romper la racha reinicia | Aversión a la pérdida como hábito |
| **First-purchase bonus** | Primera compra de cada paquete duplicada — la conversión crítica es EL PRIMER pago; quien paga una vez, repite | Romper la barrera del primer gasto |
| **VIP tiers** | Puntos permanentes por dólar gastado, escalera aspiracional | El "dolphin" siempre ve el siguiente tier cerca |

---

## 6. El diseño: la máquina de donaciones para tu overlay

Síntesis accionable. Cada mecánica indica el principio científico que explota y su prioridad.
La arquitectura es un **loop de 4 tiempos** que se repite durante todo el live:

```
  RECONOCER (cada regalo) → PROGRESAR (metas) → EVENTO (picos coordinados) → ESTATUS (memoria)
       ↑__________________________________________________________________________|
```

### CAPA 1 — Reconocimiento instantáneo (prioridad MÁXIMA, evidencia más fuerte)

> Explota: motivación de imagen (Ariely 2009), reconocimiento como predictor #1 (CHB 2025),
> contagio no-anónimo (Michigan 2025).

- **Alerta con nombre SIEMPRE**: cada regalo, por pequeño que sea, muestra nombre + avatar +
  animación. Nada de regalos "silenciosos".
- **Tamaño y duración proporcionales al valor** (modelo Bits/Super Chat): rosa = toast de 3 s en
  una esquina; regalo de 100+ coins = takeover de media pantalla con sonido; 1,000+ coins =
  pantalla completa + confeti + jingle propio. La proporcionalidad es la tabla de precios implícita.
- **Anticipación antes del reveal** (Schultz 1997): para regalos medianos/grandes, 2 segundos de
  redoble/glow ANTES de mostrar qué efecto se disparó. La pausa maximiza la dopamina de toda la sala.
- **Feed de últimos donantes** persistente en un lateral (social proof, Cialdini): "🌹 Luis ×12 ·
  🦁 Marta · 🌹 Karla ×3". Ver nombres ajenos dispara al siguiente.
- **Guion para el streamer**: leer el nombre EN VOZ ALTA en <5 segundos. Es la mecánica más barata
  y con más evidencia de todas. El overlay puede ayudarlo con un prompt gigante: "¡AGRADECE A LUIS!".

### CAPA 2 — Efectos causales: el regalo es un botón (el corazón de tu overlay)

> Explota: agencia comprada (modelo TikFinity/stream-to-earn), sorpresa del streamer
> ("Happiness Begets Money", JMR 2021).

- **Mapa regalo → efecto por tiers de coins**, público y visible (un "menú de poderes" en el overlay):
  - 1–9 coins: efecto pequeño (sonido, sticker, partículas).
  - 10–99: efecto mediano (filtro sobre la cámara del streamer 10 s, cambio de música, spawn en el juego).
  - 100–999: efecto grande (el streamer debe hacer un mini-reto, terremoto visual, "lluvia" temática).
  - 1,000+: efecto legendario (cinemática propia, el donante "toma el control" de algo 30 s).
- **Regla de oro**: el efecto debe afectar AL STREAMER, no solo a la pantalla. Su reacción genuina
  (sorpresa, risa, susto) es el producto que el donante compra — y la ciencia dice que esa emoción
  contagia y multiplica los regalos siguientes.
- **Un efecto "misterio"**: un regalo concreto dispara un efecto aleatorio de una tabla (razón
  variable, Skinner). Los viewers lo enviarán solo por ver qué sale.

### CAPA 3 — Ruleta de retos (recompensa variable + espectáculo)

> Explota: razón variable (Skinner/Schüll), agencia, contenido garantizado (modelo Streamlabs
> Spin Wheel + Games Done Quick).

- Un regalo umbral (p. ej. 99+ coins) gira una **ruleta en el overlay** con retos/castigos/premios:
  "canta 30 s", "modo formal 5 min", "el donante elige mi próximo juego", "shoutout dedicado",
  y 1–2 casillas raras y épicas ("live extendido +15 min", "reto legendario").
- La ruleta debe girar con **desaceleración dramática** (anticipación) y las casillas épicas deben
  ser visibles y raras (los near-misses ocurren naturalmente — no los amañes, ver sección 7).
- Variante GDQ (guerra de pujas): dos opciones en pantalla ("¿me pongo la peluca ROJA o AZUL?") y
  los regalos votan; la opción ganadora se ejecuta al agotar el timer. Convierte una decisión
  trivial en una batalla de donaciones.

### CAPA 4 — Metas escalonadas con barra única (goal-gradient aplicado bien)

> Explota: Kivetz 2006 (endowed progress), Cryder 2013, Kuppuswamy (Kickstarter), Habib 2025
> (una sola meta a la vez).

- **UNA sola barra de meta activa** en pantalla, con recompensa concreta y visual:
  "🎯 5,000 coins → hago el reto del hielo EN VIVO".
- **Nunca arranques en 0%**: pre-carga 15–20% ("progreso heredado del inicio del live").
- **Al completarse: celebración ENORME + revelar la siguiente meta en <30 segundos** (Kickstarter
  demuestra que el flujo muere tras alcanzar la meta si no hay siguiente).
- **El tramo final es oro**: cuando la barra pase el 80%, el overlay entra en "modo clímax"
  (barra pulsante, sonido de tensión). Es el tramo donde la evidencia dice que la gente acelera.
- **Meta de cierre con deadline**: "últimos 10 minutos del live: nos faltan 800 coins para X" —
  el patrón en U de Kickstarter y el crecimiento exponencial final de AGDQ.

### CAPA 5 — El Tren del Hype propio (evento colectivo con timer)

> Explota: meta colectiva + pánico de timer (Twitch Hype Train), arousal competitivo (Ku 2005).

- Cuando 2+ personas distintas regalan en una ventana de 3 minutos, se activa el **"TREN"**:
  overlay especial, nivel 1, y un timer de 90–120 s que se **reinicia con cada nuevo regalo**.
- Cada nivel requiere más coins que el anterior y sube la espectacularidad (nivel 3 = efecto
  especial permanente el resto del live; nivel 5 = "efecto legendario" + badge para todos los
  participantes).
- El pánico de "¡no dejen morir el tren, faltan 12 segundos!" es la mecánica que produjo los
  récords de seis cifras en Twitch. A escala TikTok funciona igual: es una micro-emergencia
  colectiva cada 30–60 minutos.

### CAPA 6 — Batallas y equipos (para picos máximos, 1–2 veces por semana)

> Explota: identidad de grupo (Oyserman/Tajfel), arousal competitivo (Ku 2005), el formato de
> mayor ingreso documentado en TikTok (3–5x).

- Usa las **PK Battles nativas de TikTok** y poténcialas con overlay: marcador gigante propio,
  "MVP del equipo" en vivo (quién ha aportado más a tu bando), y anuncio de las **ventanas x2**
  (los momentos donde los equipos coordinan sus ráfagas — la mecánica que concentra el gasto).
- **Ponle nombre a tu comunidad** y úsalo en cada batalla ("¡Team X, defiendan la corona!").
  El badge/color de equipo activa la identidad que convierte el gasto en lealtad.
- **Win streak visible**: racha de victorias del equipo en el overlay. Perderla duele (aversión
  a la pérdida) — cada batalla defiende la racha, no solo el orgullo.

### CAPA 7 — Estatus persistente: la memoria de la máquina

> Explota: coste hundido (Arkes & Blumer), estatus (Su 2020, Li 2021), consumo conspicuo,
> modelo Gifter Levels/Wealth Level/badges de antigüedad.

- **Ranking de la sesión** (top 3 gifters de HOY, siempre visible, modelo TikTok nativo) +
  **ranking semanal** con coronación el domingo ("Gifter de la Semana" = shoutout dedicado,
  su nombre en el overlay toda la semana siguiente, elige un reto).
- **Niveles acumulativos de por vida** de tu comunidad (independientes de los de TikTok):
  bronce → plata → oro → diamante → leyenda. Nunca se resetean (sunk cost). Cada nivel da un
  privilegio VISIBLE: color de la alerta, entrada anunciada al live ("¡llegó Luis, fan DIAMANTE!"),
  acceso a la casilla épica de la ruleta.
- **Entrada VIP anunciada**: cuando un top-fan entra a la sala, el overlay lo anuncia (modelo
  entrada Noble de Bigo / nivel 25+ de TikTok). Es estatus gratis de mantener y adictivo de tener.
- **Trato de whale**: identifica a tus 5–10 mayores gifters y diseña PARA ellos experiencias
  únicas (rol de "Guardián" del canal, único, visible, renovable mensualmente — modelo Douyin).
  Recuerda: son una fracción del 1.5–4% que dona, pero son la mayoría del ingreso.

### Cómo se ve una sesión completa (el loop en acción)

1. **Apertura (min 0–10):** meta del día visible al 15%, feed de donantes activo, primeros
   agradecimientos nominales para sembrar el social proof.
2. **Cuerpo (min 10–60):** efectos causales fluyendo; primera ruleta; el TREN se activa
   orgánicamente 1–2 veces; entradas VIP anunciadas.
3. **Pico programado (min 60):** batalla PK anunciada con antelación ("hoy a las 9, batalla
   contra @X — Team, preparen sus rosas") — la pre-carga de coins reduce fricción (CHB 2025).
4. **Clímax de cierre (últimos 10 min):** modo deadline en la meta, "última ruleta del día",
   coronación del top gifter de la sesión.
5. **Entre lives:** el ranking semanal y los niveles persisten — la razón para volver mañana.

### Priorización (impacto × evidencia × esfuerzo)

| # | Mecánica | Evidencia | Esfuerzo técnico | Prioridad |
|---|---|---|---|---|
| 1 | Alertas nominales proporcionales + feed de donantes | ★★★★★ | Bajo | **YA** |
| 2 | Mapa regalo→efecto por tiers (menú de poderes) | ★★★★☆ | Medio | **YA** |
| 3 | Barra de meta única escalonada (con endowed progress) | ★★★★★ | Bajo | **YA** |
| 4 | Ruleta de retos por umbral | ★★★★☆ | Medio | Semana 1–2 |
| 5 | Ranking de sesión + semanal | ★★★★☆ | Medio | Semana 1–2 |
| 6 | Tren del Hype propio | ★★★★☆ | Medio-alto | Semana 3–4 |
| 7 | Niveles de por vida + entradas VIP | ★★★★☆ | Alto (requiere BD) | Mes 2 |
| 8 | Overlay de batallas + equipos + win streak | ★★★★☆ | Alto | Mes 2 |
| 9 | Efecto misterio (razón variable) | ★★★☆☆ | Bajo | Cuando quieras |

---

## 7. Qué NO hacer (ética, riesgo y reglas de TikTok)

Esto no es opcional — es lo que separa una máquina sostenible de una cuenta baneada:

1. **No amañes los near-misses.** Simular que la ruleta "casi" cae en el premio con frecuencia
   superior a la real es exactamente la mecánica por la que las loot boxes fueron reguladas como
   juego de azar en Bélgica y Países Bajos (y por la que China obliga a publicar probabilidades).
   Si tu ruleta es aleatoria de verdad, los near-misses ocurren solos y son legítimos.
2. **Cuidado con los castigos.** Douyin sanciona (bloqueo de retiros, prohibición de PK) los
   castigos vulgares diseñados para estimular consumo. TikTok occidental hereda esa sensibilidad:
   retos sí, humillación o riesgo físico no.
3. **Menores:** TikTok exige 18+ para enviar regalos, pero el diseño no debe presionar a nadie
   ("¡dona o pierdes!"). Las mecánicas de este documento premian donar; nunca castigues no donar.
4. **Transparencia con los whales.** La evidencia (Zendle & Cairns) muestra que el gasto compulsivo
   escala con mecánicas de azar. Un whale que se quema abandona (y a veces denuncia). El trato VIP
   sano es reconocimiento y acceso, no presión de renovación agresiva.
5. **No prometas nada que TikTok prohíba** (sorteos con requisitos de regalo, contraprestaciones
   fuera de la plataforma, etc.). Revisa las políticas de LIVE vigentes antes de lanzar cada mecánica.

---

## 8. Referencias

### Psicología (fuentes primarias)
- Skinner, B.F. (1953). *Science and Human Behavior*. / Schüll, N.D. (2012). *Addiction by Design*. Princeton UP. / Eyal, N. (2014). *Hooked*.
- Kivetz, R., Urminsky, O. & Zheng, Y. (2006). The Goal-Gradient Hypothesis Resurrected. *Journal of Marketing Research*, 43(1), 39–58. https://journals.sagepub.com/doi/abs/10.1509/jmkr.43.1.39
- Kahneman, D. & Tversky, A. (1979). Prospect Theory. *Econometrica*, 47(2), 263–291. https://www.jstor.org/stable/1914185
- Cialdini, R. (1984). *Influence*. / Regan, D. (1971). *JESP*.
- Schultz, W., Dayan, P. & Montague, P.R. (1997). A Neural Substrate of Prediction and Reward. *Science*, 275(5306), 1593–1599. https://www.science.org/doi/10.1126/science.275.5306.1593
- Worchel, S., Lee, J. & Adewole, A. (1975). Effects of supply and demand on ratings of object value. *JPSP*, 32(5), 906–914.
- Ariely, D., Bracha, A. & Meier, S. (2009). Doing Good or Doing Well? Image Motivation and Monetary Incentives. *American Economic Review*, 99(1), 544–555. https://www.aeaweb.org/articles?id=10.1257/aer.99.1.544
- Ku, G., Malhotra, D. & Murnighan, J.K. (2005). Towards a competitive arousal model: auction fever. *OBHDP*, 96(2), 89–103. https://www.sciencedirect.com/science/article/abs/pii/S0749597804000925
- Oyserman, D. (2009). Identity-Based Motivation. *Journal of Consumer Psychology*, 19(3), 250–260.
- Clark, L. et al. (2009). Gambling Near-Misses Enhance Motivation to Gamble. *Neuron*, 61(3), 481–490. https://pubmed.ncbi.nlm.nih.gov/19217383/
- Arkes, H.R. & Blumer, C. (1985). The Psychology of Sunk Cost. *OBHDP*, 35(1), 124–140.
- Prelec, D. & Loewenstein, G. (1998). The Red and the Black. *Marketing Science*, 17(1), 4–28. https://www.jstor.org/stable/193222

### Streaming y donaciones (académicas)
- Zhang, Z. & Liu, F. (2024). *PLOS ONE* 19(1): e0296908. https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0296908
- "Diamonds are a TikTokers' best friend" (2025). *Computers in Human Behavior*. https://www.sciencedirect.com/science/article/abs/pii/S0747563225001116
- Wohn, D.Y., Freeman, G. & McLaughlin, C. (2018). CHI 2018. https://dl.acm.org/doi/10.1145/3173574.3174048
- Sjöblom, M. & Hamari, J. (2017). *CHB*. https://www.sciencedirect.com/science/article/abs/pii/S0747563216307208
- Hilvert-Bruce, Z. et al. (2018). *CHB*. https://www.sciencedirect.com/science/article/abs/pii/S0747563218300712
- Qiu, H.S. & Klug, D. (2021). arXiv:2108.05960. https://arxiv.org/abs/2108.05960
- Li, R., Lu, Y., Ma, J. & Wang, W. (2021). *Information & Management*, 58(6). https://www.sciencedirect.com/science/article/abs/pii/S037872062030344X
- Cao, J. et al. (2022). *Frontiers in Psychology*. https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2022.997651/full
- IPM (2023). Social influence on MMOG live streaming (988,829 obs. Douyu). https://www.sciencedirect.com/science/article/abs/pii/S0306457323001085
- Kim, J.E. et al. (2025). arXiv:2501.09235 (contagio del gifting en Twitch). https://arxiv.org/abs/2501.09235
- Lu, Z. et al. (2018). CHI 2018. https://dl.acm.org/doi/10.1145/3173574.3174040
- Liu, L. et al. (2025). *Journal of Retailing and Consumer Services*, 87. https://www.sciencedirect.com/science/article/abs/pii/S0969698925002152
- Zhang, W. (2022). *Frontiers in Psychology* (contrapunto PSR). https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2022.953484/full
- Lin, Y., Yao, D. & Chen, X. (2021). Happiness Begets Money. *JMR*, 58(3). https://journals.sagepub.com/doi/abs/10.1177/00222437211002477
- Internet Research (2023). Streamer emotions & gifting. https://www.emerald.com/insight/content/doi/10.1108/intr-05-2022-0350/full/html
- Su, Q., Zhou, F. & Wu, Y.J. (2020). *Sustainability*, 12(9), 3783. https://www.mdpi.com/2071-1050/12/9/3783
- Cryder, C., Loewenstein, G. & Seltman, H. (2013). Goal gradient in helping behavior. *JESP*. https://www.cmu.edu/dietrich/sds/docs/loewenstein/GoalGradBeh.pdf
- Kuppuswamy, V. & Bayus, B.L. (2013/2018). Kickstarter dynamics. https://papers.ssrn.com/sol3/papers.cfm?abstract_id=2234765
- Habib, R. et al. (2025). Closing-the-Gap Effect. *JMR*. https://doi.org/10.1177/00222437241270225
- Speedrunning for Charity (2019). *PACM HCI* (CSCW). https://dl.acm.org/doi/10.1145/3359150
- "'I Should Pay'" — PK ritual en Douyin. *Symbolic Interaction* (Wiley). https://onlinelibrary.wiley.com/doi/10.1002/symb.1217
- Zendle, D. & Cairns, P. Loot boxes y problem gambling. *PLOS ONE* (n=7,422). https://journals.plos.org/plosone/article?id=10.1371%2Fjournal.pone.0206767

### Industria y datos de mercado (no revisadas por pares)
- Sensor Tower Q4 2024: https://sensortower.com/blog/q4-2024-digital-market-index-report · https://sensortower.com/blog/tiktok-breaks-usd15-billion-barrier-in-lifetime-consumer-spend
- Ipsos/TikTok 2025 (Tubefilter): https://www.tubefilter.com/2025/12/02/tiktok-live-ipsos-virtual-gifting-data-study/
- Conversión de viewers a gifters: https://insights.ttsvibes.com/tiktok-live-gift-conversion-rate-by-viewer
- Gifter Levels TikTok: https://tik.tools/tiktok-gifter-levels · https://www.eulerstream.com/docs/tiktok-gifter-levels
- PK Battles: https://streamwrapped.com/blog/tiktok-live-battles-explained · https://ttcalculator.net/learn/tiktok-live-battles/
- Rankings TikTok: https://black-ads.agency/en/academy/tiktok-live-rankings
- TikFinity (stream-to-earn): https://tikfinity.zerody.one/tiktok/actionsandevents
- Hype Train: https://besttwitchextensions.com/articles/twitch-hype-train-complete-guide · récords: https://streamscharts.com/news/vedals-ai-vtuber-neuro-sama-shatters-twitch-hype-train-record-again
- Subathon de Ludwig: https://en.wikipedia.org/wiki/Ludwig%27s_subathon
- Super Chat: https://www.creatoressentials.com/glossary/super-chat/ · https://developers.google.com/youtube/v3/live/docs/superChatEvents
- Sistema Noble Bigo: https://news.bittopup.com/news/bigo-live-noble-costs-2026-baron-to-duke-recharge-guide
- Douyin fan/wealth levels: https://doudata.top/en/ · https://beithoven.com/inside-the-billion-dollar-douyin-live-streaming-gifting-economy/
- Pity Genshin: https://game8.co/games/Genshin-Impact/archives/305937
- Wowza (mercado chino): https://www.wowza.com/blog/live-streaming-in-china
- AGDQ 2016 (análisis de 30,528 donaciones): https://minimaxir.com/2016/01/agdq-2016/
