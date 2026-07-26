# ⚔ Guerra de Naciones

Juego 3D interactivo para TikTok Live: dos naciones se enfrentan con ejércitos de
cientos de miles de soldados, y **la audiencia decide la batalla**. Los regalos
mandan tropas y unidades élite, los comentarios meten a cada viewer al campo con su
nombre encima, y los likes cargan una barra de furia que desata ultimates.

Corre como **Browser Source en OBS**. Los eventos los alimenta tu plataforma
(**prizvo.live**) por HTTP, WebSocket, postMessage o una API global.

```bash
npm install
npm start
# Overlay → http://localhost:8787/index.html
# Panel   → http://localhost:8787/control.html
# Probarlo sin estar en vivo → http://localhost:8787/index.html?sim=1
```

---

## Qué hace

| | |
| --- | --- |
| 🎁 **Regalos → tropas y élites** | Cada regalo suma soldados según su precio. Los caros invocan caballería, gigantes y dragones. Un regalo que no esté en tu catálogo funciona igual: se resuelve por tramo de monedas. |
| 💬 **Comentarios → campeones** | Escribir `rojo` o `azul` mete al viewer a la batalla como un campeón con su nombre flotando, vida propia y contador de bajas. Cuando cae, sale en el killfeed y entra al ranking de supervivencia. |
| ❤️ **Likes → furia → ultimates** | Los likes llenan la barra del equipo. Al llenarse cae una ultimate: lluvia de meteoros, refuerzos masivos, furia de guerra, escudo divino o nigromante. |
| 🏳️ **Países y serie de rondas** | Cada equipo representa un país con su bandera. Marcador al mejor de N, rotación automática de países, celebración del campeón e histórico por país. |
| 🩸 **Batalla que se ve** | El campo y el río se van tiñendo de rojo con las bajas reales. Cadáveres, sangre, flechas, explosiones con onda de choque, chispas, humo y meteoros con estela. |
| ✨ **Presentación HDR** | Render en espacio lineal con bloom real, tonemapping ACES, viñeta y grading. El fuego rebosa luz; el resto no. |
| 🎥 **Cámara automática** | Se dirige sola: encuadra donde más gente está muriendo, corta con planos variados y se sacude con las ultimates. Nadie tiene que pilotarla en un directo de 8 horas. |
| 🎛️ **Panel de control** | Pantalla aparte para iniciar/pausar, cambiar países, mandar tropas, lanzar ultimates, banear, probar regalos y ver rankings. |
| 🧪 **Simulador** | Genera chats, regalos, likes y follows falsos para desarrollar, grabar clips y probar el balance sin estar en vivo. |

---

## Cómo consigue mostrar 400.000 soldados

Renderizar 400.000 mallas animadas mataría cualquier PC de streaming. La solución
está en tres decisiones:

**1. El "stack".** Cada entidad simulada representa N soldados. Su vida *y su daño*
escalan con N, así que la batalla se resuelve al mismo ritmo que si fueran individuos,
pero solo se dibujan unos miles. El contador suma `stack × vida%`, y por eso baja de
forma continua en vez de a saltos. Cuando llegan más tropas de las que caben, los
refuerzos engordan a las unidades que ya pelean en lugar de crear más.

**2. La simulación vive en un Web Worker.** Combate, búsqueda de objetivos (rejilla
espacial con counting sort), proyectiles y física corren a paso fijo en otro hilo. Cada
tick manda un snapshot con los buffers como *transferables*, y el hilo principal los
devuelve para reciclarlos: cero basura por frame.

**3. Toda la animación ocurre en el vertex shader.** Los soldados se construyen por
código con atributos de miembro y pivote; caminar, golpear, caer y ondear la capa son
rotaciones calculadas en la GPU a partir de la fase y el estado de cada instancia.
Miles de guerreros animados caben en un puñado de draw calls y ni un ciclo de CPU.

Además el overlay vigila sus propios FPS y baja la resolución interna antes de que el
directo empiece a tironear.

---

## Cómo se ve

**Unidades con silueta propia.** No son cápsulas de colores: el soldado lleva casco,
espada y escudo; el arquero, capucha, arco y carcaj; el mago, túnica, sombrero y un
báculo con orbe encendido; la caballería, gualdrapa y lanza calada; el gigante,
hombreras y maza; el campeón del chat, penacho y capa que ondea al correr. A la
distancia de cámara de un directo, la silueta es lo único que distingue una unidad de
otra —el color ya lo ocupa el equipo—. Cada instancia además varía de estatura y tono,
para que el ejército no parezca una figura clonada mil veces.

**Render HDR.** La escena se dibuja en coma flotante y en espacio lineal, sin recortar
a blanco. Eso permite que explosiones, meteoros, orbes y el sol emitan por encima de
1.0, que es de donde sale un bloom creíble: luz que rebosa solo de lo que de verdad
brilla, no un desenfoque genérico. Al final se aplica tonemapping ACES —que conserva
el color en las altas luces en vez de quemarlas—, viñeta, saturación y contraste.

**Explosiones por capas.** Bola de fuego, onda de choque expandiéndose por el suelo,
chispas con trayectoria balística y humo que asciende y se disipa. Cada capa entra en
un momento distinto: eso es lo que la hace leerse como una explosión y no como un
fogonazo plano. Los meteoros caen con estela de fuego y sacuden la cámara.

**Escenario.** Cielo con nubes procedurales en dos capas y disco solar, río que
serpentea con espuma en la orilla y corriente, bosque y rocas en las laderas para dar
escala, y suelo mezclado en tres escalas de ruido con barro en la ribera.

---

## Estructura

```
public/config/game.config.json   Toda la configuración (se lee en caliente, no se compila)
index.html / control.html        Overlay y panel de control
src/
  events/     Contrato de eventos, normalizador configurable, transportes, simulador
  sim/        Worker de simulación: mundo SoA, rejilla espacial, protocolo
  render/     Escena three.js: terreno, unidades instanciadas, efectos, cámara, nametags
  game/       Rondas, países, furia, campeones, resolución de regalos, rankings
  ui/         HUD del overlay
  shared/     Config, matemáticas deterministas, terreno compartido sim↔render
server/       Servidor estático + central de eventos WebSocket + persistencia
docs/         EVENTOS.md (integración) · OBS.md (montaje en directo)
```

---

## Configuración

Todo el balance vive en **`public/config/game.config.json`**. Se lee en tiempo de
ejecución, así que puedes editarlo dentro de `dist/config/` y solo recargar el overlay
— no hace falta recompilar ni tocar código.

Ahí controlas: países y banderas, colores de equipo, tropas iniciales, catálogo de
regalos, estadísticas de cada unidad, ultimates y sus pesos, palabras clave del chat,
comportamiento de la cámara, calidad gráfica, textos de la HUD y el mapeo de campos de
tus eventos.

Cualquier valor se puede sobreescribir desde la URL para probar:

```
index.html?battle.difficulty=1.5&graphics.timeOfDay=sunset&camera.mode=orbit
```

### Ajustes que querrás tocar primero

| Clave | Para qué |
| --- | --- |
| `series.target` | Rondas para ganar la serie (el `Win X / 10` del marcador) |
| `battle.startingTroops` | Con cuántos soldados arranca cada bando |
| `gifts.coinsToTroops` | Tropas por moneda: el mando principal de la economía |
| `battle.renderCapPerTeam` | Figuras dibujadas por equipo. Baja esto si te faltan FPS |
| `graphics.bloomIntensity` | Cuánta luz rebosa del fuego. `graphics.bloomThreshold` sube el listón de qué brilla |
| `graphics.timeOfDay` | `day`, `sunset` o `night`: cambia toda la paleta de golpe |
| `graphics.clouds` / `propDensity` | Nubes y densidad de bosque |
| `hud.safeAreaTop` | Hueco arriba para tu cámara |
| `battle.autoBalance` | Da ventaja al bando que va perdiendo para que la ronda no se muera |

---

## Integración con prizvo.live

Ver **[docs/EVENTOS.md](docs/EVENTOS.md)** — es lo único que necesitas leer para
enganchar tu plataforma. Resumen:

```bash
curl -X POST http://localhost:8787/api/event -H 'content-type: application/json' \
  -d '{"type":"gift","uniqueId":"sofi","giftName":"Rosa","diamondCount":1,"repeatCount":30,"repeatEnd":true}'
```

Si tus campos se llaman distinto, se ajustan en `events.mapping` dentro de la config.
No hay que tocar código.

---

## Montaje en OBS

Ver **[docs/OBS.md](docs/OBS.md)**. Fuente de navegador a `http://localhost:8787/index.html`,
1080 × 1920, 60 FPS.

---

## Comandos

| Comando | Qué hace |
| --- | --- |
| `npm start` | Compila y levanta el servidor (lo normal para transmitir) |
| `npm run dev` | Servidor de desarrollo con recarga en caliente |
| `npm run server` | Solo el servidor, si ya compilaste |
| `npm run build` | Compila a `dist/` |
| `npm run typecheck` | Verifica tipos |

Variables de entorno del servidor: `PORT` (8787), `HOST` (0.0.0.0) y `API_KEY`
(si la defines, `/api/event` exige la cabecera `x-api-key`).

---

## Persistencia

Los rankings se guardan en `data/leaderboards.json` con escritura atómica: top de
donadores, guerreros que más aguantaron, victorias por país e historial de rondas.
Si el servidor no está levantado, el overlay usa `localStorage` y sigue funcionando.
