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
| 🌦️ **Estaciones y clima** | Primavera, verano, otoño e invierno cambian la paleta del campo, el follaje y el agua; encima llueve, nieva, cae niebla o revienta una tormenta con relámpagos y viento. Rota solo cada ronda, o lo fijas desde el panel. |
| 🩸 **Batalla que se ve** | El campo y el río se van tiñendo de rojo con las bajas reales. Cadáveres, sangre, flechas, explosiones con onda de choque, chispas, humo y meteoros con estela. |
| ✨ **Presentación HDR** | Render en espacio lineal con bloom real, tonemapping ACES, viñeta y grading. El fuego rebosa luz; el resto no. |
| 🖋️ **HUD de esports** | Tipografía propia autoalojada (Bebas Neue para números y titulares, Archivo para el resto), barra de fuerzas rojo-contra-azul en vez de texto flotante, cinta de victoria inclinada con la bandera, cuenta atrás con anillo de choque y todo animado con muelles por transform/opacity. |
| 🎥 **Cámara automática, pero tuya** | Se dirige sola —encuadra donde más gente muere, corta con planos variados, se sacude con las ultimates— y aun así distancia, altura, campo de visión, ritmo de corte, órbita y sacudida se mueven en vivo con deslizadores, más cuatro encuadres listos: épica, al ras, táctica y por defecto. |
| 🎛️ **Panel de control** | Pantalla aparte, en seis pestañas, pensada para usarse con el pulgar desde el móvil mientras hablas. Partida, cámara, aspecto, mundo, pruebas y rankings, y todo se aplica al instante sin recargar. |
| 🧪 **Simulador** | Genera chats, regalos, likes y follows falsos para desarrollar, grabar clips y probar el balance sin estar en vivo. |
| 🎛️ **Modo prueba** | Con `?test=1` (o siempre en la demo web) aparece un cajón táctil dentro del propio overlay: dispara cada regalo por tramo, campeones, likes, ultimates y tropas por unidad, y configura hora del día, estación, clima, cámara, calidad y dificultad. Todo entra por el mismo camino que los eventos reales. |

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

**Personajes, no bloques.** La primitiva de todo el cuerpo es un prisma de ocho lados
con sección elíptica que se estrecha a lo largo del miembro, con normales suaves. Es lo
que separa un brazo de un ladrillo: una caja tiene cuatro caras planas y aristas de 90°
que la luz delata al instante. Encima van proporciones heroicas (cuello, pelvis,
rótulas, botas, guanteletes, hombreras) y **oclusión ambiental horneada por vértice**,
que oscurece axilas y articulaciones y da sensación de volumen sin calcular sombras
propias.

Cada arquetipo tiene su equipo: el soldado, casco con nasal, espada que se afila y
escudo con umbo; el arquero, capucha, arco recurvo con cuerda y carcaj; el mago,
túnica acampanada, sombrero cónico y báculo con orbe encendido; la caballería, montura
completa con crin, gualdrapa y lanza calada; el gigante, hombreras y maza con pinchos;
el campeón del chat, penacho y capa que ondea al correr; el dragón, cuello, cresta
dorsal y alas con diedro y dedos alares. A la distancia de cámara de un directo la
silueta es lo único que distingue una unidad de otra —el color ya lo ocupa el equipo—.
Cada instancia varía además de estatura y tono, para que el ejército no parezca una
figura clonada mil veces.

**Menos color de equipo, no más.** Un soldado vestido de rojo intenso de la cabeza a
los pies no se lee como un soldado: se lee como un muñeco de plástico. Un ejército real
es en su mayoría cuero, acero y tela sucia. Así que las calzas van en un tono apagado y
el color del bando se concentra en la sobrevesta y el escudo —que además es donde el
espectador lo busca—. Es el cambio que más hizo por sacarlos del aspecto de juguete.

**Relieve sin mapas de normales.** El shader estima la pendiente del material en UV y
dobla la normal con ella, así que los pliegues, los anillos de la malla y las
abolladuras del acero recogen la luz de verdad en vez de estar pintados. El campo de
altura NO es el del color: la trama del tejido va a 110 ciclos por unidad, cae muy por
debajo del píxel y derivarla solo produce moiré, así que al relieve solo entran los
rasgos gruesos.

**Materiales, no colores planos.** Ninguna unidad usa una sola imagen: los materiales
se generan dentro del shader a partir de las coordenadas del propio modelo. El paño
tiene trama de tejido, la cota de malla su anillado, el peto acero cepillado con
reflejo anisótropo, el cuero su grano y veteado, la madera del arco y del asta sus
vetas, y el escudo su heráldica —franjas, cuartelado o chevrón según la unidad—. Para
que las piezas planas (escudos, capas, gualdrapas) no degeneraran en una rejilla hubo
que remapear sus UV de forma triplanar: en un prisma la coordenada vertical avanza con
la altura, pero en una losa es casi constante y el patrón se estiraba a rayas.

Las texturas solo se calculan dentro de `graphics.textureDistance`; más allá la unidad
usa color liso. A la distancia a la que se ve un ejército de 9.000 figuras, el detalle
no aporta nada y sí cuesta.

**Variedad por instancia.** Cada figura recibe su propio tono de piel, de cuero y de
metal, además de barro en las botas que va subiendo con la refriega. Dos soldados
contiguos nunca son el mismo píxel repetido, pero el color de equipo se mantiene
intacto y legible: es lo único que el espectador tiene que distinguir a la primera.

El arquero tiene su propia cadena de huesos, y por un motivo concreto: colgado del
antebrazo normal, un arco de metro y pico barría un arco enorme cada vez que el codo se
plegaba y acababa tumbado detrás de la espalda, como si se hubiera desprendido. Ahora el
brazo del arco va extendido y rígido, el otro tensa hacia la mejilla, y **el arco vive
en un hueso que solo traslada**: se queda vertical y sigue a la mano. Si rotara con el
brazo, visto de frente sería una raya invisible.

**Animación con esqueleto de dos huesos.** Cada vértice conoce su articulación y la de
su hueso padre, así que el shader dobla rodillas y codos rotando primero sobre una y
después sobre la otra. De ahí salen la flexión de rodilla al despegar el pie, el codo
que se extiende en el golpe, la contrarrotación del torso, la cabeza que se mantiene
mirando al frente y la inclinación del cuerpo al correr. Todo en GPU, cero coste de CPU.

Sobre ese esqueleto hay repertorio: tres estilos de ataque (tajo descendente, estocada
y golpe lateral) repartidos por instancia para que una línea de choque no golpee al
unísono, una postura de guardia con el escudo alzado cuando la unidad aguanta sin
avanzar, y tres formas distintas de caer —de rodillas, de espaldas y de bruces— con un
giro sobre el eje para que ningún cadáver quede clonado del de al lado. Las capas y
túnicas ondean con el viento del clima actual.

Para juzgar los modelos sin cazar el momento en que la cámara pasa cerca, hay un
previsualizador en **`models.html`**: muestra un ejemplar de cada arquetipo con los
mismos shaders, y se puede orbitar, cambiar de equipo y pausar la animación. Acepta
además el encuadre por URL —`models.html?unit=archer&yaw=0.6&pitch=0.1&dist=3&anim=0`—
para volver siempre exactamente a la misma vista al comparar cambios.

**Render HDR.** La escena se dibuja en coma flotante y en espacio lineal, sin recortar
a blanco. Eso permite que explosiones, meteoros, orbes y el sol emitan por encima de
1.0, que es de donde sale un bloom creíble: luz que rebosa solo de lo que de verdad
brilla, no un desenfoque genérico. Al final se aplica tonemapping ACES —que conserva
el color en las altas luces en vez de quemarlas—, viñeta, saturación y contraste.

**Explosiones por capas.** Bola de fuego, onda de choque expandiéndose por el suelo,
chispas con trayectoria balística y humo que asciende y se disipa. Cada capa entra en
un momento distinto: eso es lo que la hace leerse como una explosión y no como un
fogonazo plano. Los meteoros caen con estela de fuego y sacuden la cámara.

**Cámara dirigida, pero tuya.** Sigue encuadrando sola donde muere más gente y
cortando entre planos variados, que es lo que hace falta en un directo de ocho
horas. Lo que cambia es que ahora todos sus parámetros se mueven en vivo desde el
panel, y como **multiplicadores** sobre lo que dice la config: 1,00× es el
encuadre base y a partir de ahí acercas o alejas sin tener que saber que el rango
del mapa va de 55 a 130 unidades. Los ajustes se aplican al ENCUADRAR, no al
elegir el plano, así que mover un deslizador se ve al momento, no corta la toma en
curso y no altera la secuencia de planos.

**Escenario.** Cielo con nubes procedurales en dos capas y disco solar, río que
serpentea con espuma en la orilla y corriente, bosque y rocas en las laderas para dar
escala, y suelo mezclado en tres escalas de ruido con barro en la ribera.

**El HUD también es parte del juego.** Los números y titulares van en Bebas Neue y
el texto de interfaz en Archivo, ambas autoalojadas en `public/fonts/` (el overlay
arranca sin internet). El dato estrella —cuántos soldados quedan— es una **barra de
fuerzas** rojo contra azul con el punto de choque brillante, no dos renglones de
texto tapando la batalla. Los momentos tienen ceremonia: la cuenta atrás cae con
rebote y lanza un anillo dorado por segundo, la ultimate presenta su placa con icono
flotante, la victoria entra en una cinta inclinada con la bandera y el subtítulo
retardado, y la muerte súbita salta como alarma. Dos reglas de oro en todo ello:
solo se anima transform y opacity (nada que fuerce layout sobre el WebGL), y las
entradas de los anuncios críticos animan únicamente transform — si el equipo va a
tirones, el banner se ve igual en vez de quedarse atascado en un fundido invisible.

**Estaciones y clima.** Son dos sistemas que se multiplican entre sí. La **estación**
define el aspecto del terreno: el ocre de otoño no se consigue multiplicando el verde
—un multiplicador solo escala canales y nunca cambia el tono—, sino con un
desplazamiento de tono que conserva la luminancia, así que el campo cambia de color sin
perder el relieve ni el contraste. Además tiñe el follaje, el agua y la luz, y en
invierno cuaja nieve en lo llano —menos en las pendientes, y se derrite donde hay
sangre—.

El **clima** añade lo que se mueve: lluvia y nieve como partículas instanciadas en una
caja que sigue a la cámara y se recicla con `mod()` sobre el tiempo (no tiene sentido
simular precipitación en el mapa entero cuando solo se ve un trozo), niebla que se
espesa, cielo que se oscurece, suelo y ropa mojados con reflejo especular, viento que
arrastra la precipitación y agita capas y árboles, y relámpagos que iluminan la escena
entera en tormenta. Al terminar cada ronda la estación avanza y el clima se sortea
entre los que encajan con ella —en invierno sale nieve mucho más a menudo que
tormenta—; con `world.cycleSeasonEachRound` y `world.randomWeatherEachRound` en `false`
se queda fijo en lo que elijas desde el panel.

---

## Estructura

```
public/config/game.config.json   Toda la configuración (se lee en caliente, no se compila)
index.html / control.html        Overlay y panel de control
models.html                      Previsualizador de unidades (herramienta de desarrollo)
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
| `world.season` / `world.weather` | Estación y clima de arranque |
| `world.cycleSeasonEachRound` | La estación avanza sola al terminar cada ronda |
| `world.randomWeatherEachRound` | Sortea clima nuevo cada ronda entre los propios de la estación |
| `graphics.clouds` / `propDensity` | Nubes y densidad de bosque |
| `graphics.lodDistance` | A partir de qué distancia se usa la malla reducida |
| `graphics.textureDistance` | Hasta dónde se calculan las texturas de material de las unidades |
| `graphics.precipitationParticles` | Densidad de lluvia y nieve. Lo primero que bajar si la tormenta cuesta FPS |
| `camera.distanceRange` / `heightRange` | Encuadre base. Los deslizadores del panel multiplican sobre esto |
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
