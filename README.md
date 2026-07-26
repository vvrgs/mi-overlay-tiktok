# 🐔 Cruza la Calle — Overlay de juego para TikTok Live

Recreación completa estilo **Crossy Road** en voxel 3D, pensada como overlay
para directos de TikTok, con **desastres activados por regalos** que resetean
la partida o empujan al pollo hacia atrás.

Todo corre en el navegador con un solo `index.html` — sin servidor, sin
dependencias externas (Three.js y la fuente pixel van incluidas en `vendor/`).

## 🎮 El juego

- Pollo voxel con salto, squash & stretch y rotación como el original.
- Carriles infinitos generados al azar: **pasto** (árboles y rocas que
  bloquean), **carreteras** (coches y camiones de colores), **ríos**
  (troncos flotantes para cruzar) y **vías de tren** (señales que parpadean,
  campana y tren a toda velocidad).
- Cámara isométrica que avanza sola: si te quedas atrás, pierdes.
- Águila que te caza si te quedas quieto demasiado tiempo.
- Muertes animadas: aplastado (plumas), ahogado (splash), fulminado,
  abducido…
- Marcador con fuente pixel, récord guardado en `localStorage`, reinicio
  automático tras cada muerte (ideal para dejarlo corriendo en el live).
- Sonidos 100% sintetizados con WebAudio (sin archivos de audio).

## 💥 Desastres y acciones (regalos)

| Acción | Qué hace |
|---|---|
| 💎 **RESET** | Mata la run al instante |
| 🍓 **SAVE THE RUN** | Da un escudo (aguanta una muerte y te recoloca a salvo) |
| 🪽 **MOVE LEFT** / 💗 **MOVE RIGHT** | Mueven al pollo un paso |
| 🚛 **SUPER CAMIÓN** | Un camión gigante barre la fila del pollo y aplasta todo |
| 🌋 **VOLCÁN** | Brota un volcán, la erupción te empuja 3 filas atrás y llueven bombas de lava |
| 🫨 **TERREMOTO** | Sacude la pantalla y te empuja 4 filas atrás |
| 🌪️ **TORNADO** | Cruza el mapa; si te atrapa, te arrastra muchas filas atrás |
| 🛸 **OVNI** | Te abduce con su rayo (adiós run) |
| ⚡ **RAYO** | Marca tu casilla y cae un rayo teledirigido |

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
