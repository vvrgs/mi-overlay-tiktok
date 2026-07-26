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

Está repartido en seis pestañas, y **todo lo que se toca se aplica al instante**
en el overlay, sin recargar nada:

| Pestaña | Qué hay |
| --- | --- |
| ⚔ **Partida** | Iniciar, pausar, repetir o terminar ronda; dificultad; países; enviar tropas; lanzar ultimates y llenar furia |
| 🎥 **Cámara** | Modo, distancia, altura, campo de visión, duración del plano, órbita, sacudida, perseguir la acción, y cuatro encuadres listos |
| ✨ **Aspecto** | Calidad, exposición, contraste, saturación, bloom, viñeta, distancia de modelos y de texturas, niebla |
| 🌦 **Mundo** | Estación y clima manuales |
| 🧪 **Pruebas** | Simulador, regalo/chat/like de prueba, moderación |
| 🏆 **Rankings** | Generales, guerreros, países y últimas rondas |

Los deslizadores de cámara y aspecto son **relativos**: 1,00× es lo que dice la
config y a partir de ahí acercas, alejas, subes o bajas sin tener que saber en qué
unidades trabaja el motor. El panel se pone al día solo con lo que reporta el
overlay, así que puedes abrirlo a mitad del directo o en el móvil y verá los
valores reales, no los de arranque.

Los cuatro encuadres de la pestaña de cámara mueven varios deslizadores a la vez:

| Encuadre | Para qué |
| --- | --- |
| 🏔 Épica | Plano general alto y pausado. Para el choque inicial y los finales |
| 💥 Al ras | Pegada a la tropa, cortes rápidos y sacudida fuerte. Para el cuerpo a cuerpo |
| 🗺 Táctica | Casi cenital, planos largos, poca órbita. Se ve el mapa entero |
| ↺ Por defecto | Vuelve a 1,00× en todo |

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
4. `graphics.textureDistance` → `20` (las texturas de tela, malla y acero solo se
   calculan de cerca; a 20 apenas se nota y se ahorra mucho fragmento)
5. `graphics.lodDistance` → `40` (menos figuras con el modelo detallado)
6. `graphics.precipitationParticles` → `4000` (solo cuesta cuando llueve o nieva; en
   cielo despejado no se dibuja ni una partícula)
7. `graphics.propDensity` → `0` y `graphics.clouds` → `0`
8. `graphics.shadows` → `false`
9. `graphics.particleLimit` → `2000`
10. `graphics.bloodTextureSize` → `256`

Poner `graphics.quality` en `"low"` hace varios de estos pasos de golpe: apaga el
post-procesado, el decorado, las sombras, la sangre y la precipitación, y usa siluetas
simplificadas.

Del clima, lo que cuesta es la **tormenta**: es el único que junta precipitación al
máximo, niebla espesa y destellos de relámpago. Si el directo solo se atasca en
tormenta, baja `graphics.precipitationParticles` antes de tocar nada más. Y si prefieres
no jugártelo, deja `world.randomWeatherEachRound` en `false` y fija el clima que
aguante tu equipo desde el panel.

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
