# Conectar prizvo.live con el juego

El juego no se conecta a TikTok: tú ya tienes esa parte resuelta en **prizvo.live**.
Lo único que necesita es recibir los eventos. Este documento es todo lo que hace falta
para engancharlo.

---

## 1. Elige un canal de entrada

Hay cuatro, y funcionan **a la vez**. Usa el que mejor encaje con tu arquitectura.

### A. HTTP POST (lo más simple para un backend)

Tu servidor manda un POST y el servidor del juego lo reenvía al overlay:

```bash
curl -X POST http://localhost:8787/api/event \
  -H 'content-type: application/json' \
  -d '{"type":"gift","uniqueId":"creatura13","nickname":"creatura13","giftName":"Osito Mishka","diamondCount":10,"repeatCount":11,"repeatEnd":true}'
```

Acepta también un array para mandar lotes:

```bash
curl -X POST http://localhost:8787/api/events -H 'content-type: application/json' -d '[{...},{...}]'
```

Si defines la variable de entorno `API_KEY`, el endpoint exigirá la cabecera `x-api-key`.

### B. WebSocket (lo más eficiente para alto volumen)

```js
const ws = new WebSocket('ws://localhost:8787/events');
ws.onopen = () => ws.send(JSON.stringify({ type: 'hello', role: 'source' }));
ws.send(JSON.stringify({ type: 'gift', uniqueId: 'sofi', giftName: 'Rosa', diamondCount: 1, repeatCount: 30, repeatEnd: true }));
```

### C. iframe + postMessage (si embebes el overlay dentro de prizvo.live)

```js
const frame = document.querySelector('#overlay');
frame.contentWindow.postMessage({ type: 'chat', uniqueId: 'tapia04', comment: 'azul' }, '*');
```

### D. API global (si el overlay vive en la misma página)

```js
window.GuerraDeNaciones.emit({ type: 'like', uniqueId: 'nico', likeCount: 25 });
// Atajos:
window.GuerraDeNaciones.gift('sofi', 'Perfume', 20, 3);
window.GuerraDeNaciones.chat('tapia04', 'rojo');
window.GuerraDeNaciones.like('nico', 25);
```

---

## 2. Forma de los eventos

### Regalo

```json
{
  "type": "gift",
  "uniqueId": "creatura13",
  "nickname": "creatura13",
  "avatarUrl": "https://…",
  "giftId": "5655",
  "giftName": "Osito Mishka",
  "diamondCount": 10,
  "repeatCount": 11,
  "repeatEnd": true
}
```

`diamondCount` es el costo **unitario** en monedas; `repeatCount` es la cantidad del combo.

**Sobre `repeatEnd` — importante para no multiplicar tropas por error:**

| Lo que manda tu app | Qué hace el juego |
| --- | --- |
| `repeatEnd` ausente | Paga al instante. Úsalo si prizvo.live ya agrega los combos. |
| `repeatEnd: false` | Combo en curso: acumula sin pagar. |
| `repeatEnd: true` | Cierra el combo y paga solo lo que faltaba. |

Si mandas parciales y nunca llega el cierre, a los `gifts.comboWindowSeconds` segundos
se paga igual: **ningún regalo se pierde**.

### Comentario

```json
{ "type": "chat", "uniqueId": "tapia04", "nickname": "tapia04", "comment": "vamos azul" }
```

### Like

```json
{ "type": "like", "uniqueId": "nico", "nickname": "Nico", "likeCount": 15 }
```

### Follow / share / suscripción / entrada a la sala

```json
{ "type": "follow",    "uniqueId": "…", "nickname": "…" }
{ "type": "share",     "uniqueId": "…", "nickname": "…" }
{ "type": "subscribe", "uniqueId": "…", "nickname": "…" }
{ "type": "join",      "uniqueId": "…", "nickname": "…" }
```

### Forzar el bando (opcional)

Si prizvo.live ya sabe de qué equipo es el usuario, añade `"team": "red"` o `"blue"`
a cualquier evento y el juego respeta esa decisión.

---

## 3. Si tus campos se llaman distinto: no toques código

En `public/config/game.config.json`, dentro de `events.mapping`, cada campo canónico
apunta a una o varias rutas de **tu** payload, separadas por `|` (gana la primera que
exista) y con notación de puntos para anidar.

Ejemplo: si prizvo.live manda

```json
{ "evento": "regalo", "usuario": { "handle": "sofi", "display": "Sofi" }, "obsequio": { "titulo": "Rosa", "precio": 1, "veces": 30 } }
```

configuras:

```json
"events": {
  "mapping": {
    "type": "evento",
    "uniqueId": "usuario.handle",
    "nickname": "usuario.display",
    "giftName": "obsequio.titulo",
    "diamondCount": "obsequio.precio",
    "repeatCount": "obsequio.veces"
  },
  "typeAliases": {
    "gift": ["regalo"],
    "chat": ["comentario"],
    "like": ["megusta"]
  }
}
```

Y ya está. Sin recompilar: guarda el archivo y recarga el overlay.

> Si un evento no trae campo de tipo, el juego lo deduce por su forma (si hay nombre
> de regalo es un regalo, si hay comentario es un chat, etc.).

---

## 4. Qué provoca cada evento

| Evento | Efecto |
| --- | --- |
| Regalo | Tropas según `gifts` (por id → por nombre → por tramo de monedas), unidades élite, furia y entrada al ranking de donadores. Algunos regalos disparan una ultimate. |
| Comentario | Elige bando y genera un **campeón** con el nombre del viewer flotando sobre él. |
| Like | Carga la barra de furia y suma tropas. Al llenarse se lanza una ultimate. |
| Follow / share / suscripción | Recompensas configurables en `events.rewards`. |

**Regalo desconocido:** siempre da tropas. `gifts.tiers` reparte por precio, así que un
regalo nuevo de TikTok que no esté en tu catálogo funciona igual desde el primer segundo.

---

## 5. Leer el estado del juego desde tu app

El overlay publica su estado cada 500 ms por WebSocket, `BroadcastChannel` y
`postMessage` al contenedor padre:

```json
{
  "type": "status",
  "phase": "battle",
  "round": 3,
  "wins": { "red": 2, "blue": 1 },
  "soldiers": { "red": 40559, "blue": 29234 },
  "rage": { "red": 82, "blue": 41 },
  "countries": { "red": "MX", "blue": "CR" },
  "fps": 60
}
```

Y para controlarlo desde fuera, manda comandos por el mismo canal:

```json
{ "type": "control", "action": "start" }
{ "type": "control", "action": "setCountry", "team": "red", "country": "PE" }
{ "type": "control", "action": "castUltimate", "team": "blue", "ultimate": "meteoros" }
{ "type": "control", "action": "addTroops", "team": "red", "troops": 50000, "unit": "giant" }
```

La lista completa está en `src/events/types.ts` (`ControlCommand`).

---

## 6. Probarlo sin estar en vivo

```bash
npm start                      # compila y levanta el servidor
# Overlay con eventos falsos:
open http://localhost:8787/index.html?sim=1
# Panel de control (botón "Probar regalo"):
open http://localhost:8787/control.html
```
