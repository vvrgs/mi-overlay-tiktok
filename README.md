# 🐔 Cruza la Calle — Overlay de juego para TikTok Live

Recreación completa estilo **Crossy Road** en voxel 3D, pensada como overlay
para directos de TikTok, con **desastres activados por regalos** que resetean
la partida o empujan al pollo hacia atrás.

Todo corre en el navegador con un solo `index.html` — sin servidor, sin
dependencias externas (Three.js y la fuente pixel van incluidas en `vendor/`).

## 🎮 El juego

- Portada animada con logo pixel y pollo saltando; empieza sola o con la
  primera tecla / primer regalo.
- Pollo voxel con salto, squash & stretch, respiración en reposo y
  picoteo ocioso como el original.
- Carriles infinitos casilla a casilla con variación de color: **pasto**
  (árboles, rocas, flores, matas y monedas giratorias), **carreteras**
  (coches, taxis, patrullas con torreta parpadeante, autobuses y camiones
  que echan humo, con arcén y líneas discontinuas), **ríos** (troncos que
  se mecen, nenúfares, espuma y destellos de corriente) y **vías de tren**
  (señales que parpadean, barreras que bajan, campana y tren a toda
  velocidad).
- Nubes voxel a la deriva que proyectan sombra sobre el mapa.
- Cámara isométrica que avanza sola (más rápido cuanto más lejos llegas);
  si te quedas atrás, pierdes. Águila que te caza si te quedas quieto.
- Muertes con cámara lenta y zoom dramático: aplastado (plumas), ahogado
  (splash), fulminado, abducido…
- Marcador pixel, monedas coleccionables, récord persistente con
  fanfarria + confeti al batirlo, y reinicio automático (ideal para
  dejarlo corriendo en el live).
- Viñeta sutil, marco de peligro en los desastres y sacudida del HUD.
- Sonidos 100% sintetizados con WebAudio (sin archivos de audio).

## 💥 Desastres y acciones (regalos)

Cada desastre es una secuencia cinematográfica con fases de anticipación,
clímax y resolución, con clima, cámara y sonido propios:

| Acción | Qué hace |
|---|---|
| 💎 **RESET** | El cielo se oscurece y un diamante gigante cae persiguiéndote con una sombra que crece bajo tus pies; cámara lenta, revienta en esquirlas y mata la run |
| 🍓 **SAVE THE RUN** | Pilar de luz dorada + halo descendente; da un escudo (aguanta una muerte y te recoloca a salvo) |
| 🪽 **MOVE LEFT** / 💗 **MOVE RIGHT** | Mueven al pollo un paso |
| 🚛 **SUPER CAMIÓN** | El suelo se agrieta; entra un camión monstruo con faros encendidos, pala quitanieves, fuego en los escapes y chispas, deja el asfalto EN LLAMAS, manda los coches por los aires… y cuando crees que pasó, **da la vuelta y vuelve a por ti** |
| 🌋 **VOLCÁN** | Grietas incandescentes, erupción que lanza rocas, **lluvia de ceniza**, columna de humo, bombas de lava con charcos ardientes… y de remate una **MEGA BOMBA teledirigida** con marcador sobre tu casilla |
| 🫨 **TERREMOTO** | La cámara se ladea, un **carril entero se hunde**, fisuras cruzan el mapa, árboles desplomándose, hasta 4 rocas gigantes rebotando… y cuando parece que acabó: **RÉPLICA** |
| 🌪️ **TORNADO** | Noche con ráfagas de viento y relámpagos lejanos; el embudo se retuerce y va acompañado de un **tornado bebé** que hace el caos; al irse **llueven los escombros** que se llevó |
| 🛸 **OVNI** | Noche cerrada con **interferencia en el HUD**; la nave madre llega con **2 drones escolta**, te busca con su foco, **abduce un árbol de muestra**, y luego a ti — despegue en warp |
| ⚡ **TORMENTA** | Lluvia, relámpagos ambiente y 4 rayos de aviso… y de remate **TRES rayos seguidos que te persiguen** |
| ☠️ **APOCALIPSIS** | Si coinciden 2+ desastres a la vez, banner especial y sacudida extra |

Cada evento muestra un banner con el nombre del regalo y del espectador, y el
panel lateral (como el del video) lleva el conteo de cada acción.

## 🔌 Conexión con TikTok (TikFinity)

El overlay se conecta solo al **Events API de TikFinity**
(`ws://localhost:21213/`) y se reconecta automáticamente:

1. Abre TikFinity en la PC del directo (con el Events API activado).
2. Añade `index.html` como **fuente de navegador** en OBS / TikTok Live
   Studio (por ejemplo 1080×1920 para formato vertical).
3. Listo: los regalos disparan las acciones según el mapeo.

¿Otro servidor de eventos? Añade `?ws=ws://host:puerto/` a la URL.

### Cambiar qué regalo dispara qué

Edita `giftMap` en [`js/config.js`](js/config.js) (nombre del regalo en
minúsculas → acción):

```js
giftMap: {
  'rose':      'moveLeft',
  'cap':       'superTruck',
  'money gun': 'volcano',
  // ...
},
```

También hay soporte opcional para comandos de chat (`chatCommands`) y para
disparar una acción cada N likes (`likes`).

## ⌨️ Teclas (para jugar y probar sin TikTok)

| Tecla | Acción |
|---|---|
| Flechas / WASD | Mover al pollo |
| `T` | Super camión |
| `V` | Volcán |
| `E` | Terremoto |
| `N` | Tornado |
| `U` | OVNI |
| `L` | Rayo |
| `1` / `2` | Reset / Escudo |
| `3` / `4` | Move left / Move right |
| `M` | Silenciar |
| `Enter` | Reiniciar ya (tras morir) |

También puedes simular regalos desde la consola del navegador:
`TikTok.simulateGift('Rose', 'nombre_fan')`.

## 📁 Estructura

```
index.html        página del overlay
css/style.css     HUD (marcador, panel, banners, game over)
js/config.js      ⚙️ toda la configuración editable
js/audio.js       sonidos sintetizados (WebAudio)
js/models.js      modelos voxel (pollo, coches, tren, volcán, ovni…)
js/world.js       generación de carriles, tráfico, troncos y trenes
js/player.js      movimiento, colisiones, troncos, escudo
js/effects.js     partículas, marcas y sacudida de cámara
js/disasters.js   los desastres y acciones de regalos
js/ui.js          HUD y banners
js/tiktok.js      WebSocket con TikFinity
js/main.js        escena, cámara, luces y bucle del juego
vendor/           Three.js + fuente pixel (sin internet necesario)
```
