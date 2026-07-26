# Montarlo en OBS y salir en directo

## 1. Levantar el juego

```bash
npm install
npm start        # compila y deja el servidor en http://localhost:8787
```

Déjalo corriendo mientras transmites.

## 2. Añadir el overlay en OBS

1. **Fuentes → + → Navegador**
2. URL: `http://localhost:8787/index.html`
3. Ancho **1080**, alto **1920** (vertical de TikTok). Si transmites en horizontal, `1920 × 1080`.
4. FPS personalizados: **60**
5. Marca **Apagar la fuente cuando no esté visible** → *desmarcado*
6. Marca **Actualizar el navegador cuando la escena se active** → *desmarcado*
   (si no, cada cambio de escena reinicia la partida en curso)

> **CSS personalizado de OBS: déjalo vacío.** OBS inyecta por defecto un CSS que pone
> el fondo transparente; el juego ya gestiona su propio fondo.

## 3. Dejar hueco a tu cámara

En el vídeo de referencia la cámara del streamer ocupa la franja superior. Para que la
HUD no quede debajo, ajusta en `game.config.json`:

```json
"hud": {
  "safeAreaTop": 420,      // píxeles reservados arriba para tu cámara
  "safeAreaBottom": 260,   // hueco para los comentarios de TikTok
  "scale": 1.15            // sube el tamaño de toda la interfaz
}
```

Recarga la fuente del navegador y listo.

## 4. Panel de control en otra ventana

Abre `http://localhost:8787/control.html` en tu navegador, en el segundo monitor.
Se conecta solo al overlay (por WebSocket y, si están en el mismo navegador, también
por `BroadcastChannel`, así que funciona incluso sin servidor).

Desde ahí controlas la partida, cambias países, mandas tropas, lanzas ultimates,
baneas usuarios y pruebas regalos sin depender de que alguien regale de verdad.

## 5. Atajos de teclado sobre el overlay

Solo funcionan si el overlay tiene el foco (en un navegador normal, no dentro de OBS):

| Tecla | Acción |
| --- | --- |
| `S` | Activar/desactivar el simulador de eventos |
| `P` | Pausar/reanudar |
| `N` | Empezar una ronda nueva |

## 6. Ajustar el rendimiento

El overlay ya baja la resolución interna solo si detecta caída de FPS. Si aun así te
va justo, en este orden:

1. `graphics.quality` → `"medium"`
2. `battle.renderCapPerTeam` → `3000` (los contadores siguen mostrando cientos de miles;
   solo se dibujan menos figuras)
3. `graphics.postProcessing` → `false` (quita bloom, viñeta y grading; ahorra varias
   pasadas a pantalla completa)
4. `graphics.lodDistance` → `40` (menos figuras con el modelo detallado)
5. `graphics.propDensity` → `0` y `graphics.clouds` → `0`
6. `graphics.shadows` → `false`
7. `graphics.particleLimit` → `2000`
8. `graphics.bloodTextureSize` → `256`

Poner `graphics.quality` en `"low"` hace varios de estos pasos de golpe: apaga el
post-procesado, el decorado, las sombras y la sangre, y usa siluetas simplificadas.

Todo se puede probar sin editar el archivo, pasándolo por la URL:

```
http://localhost:8787/index.html?graphics.quality=medium&battle.renderCapPerTeam=3000
```

## 7. Comprobaciones antes de salir en vivo

- [ ] `http://localhost:8787/api/health` responde `{"ok":true}`
- [ ] El punto de estado del overlay (arriba a la izquierda) está en verde `en vivo`
- [ ] Un regalo de prueba desde el panel mueve el contador de tropas
- [ ] Escribir `rojo` en el chat hace aparecer un campeón con nombre
- [ ] Los rankings se están guardando en `data/leaderboards.json`
