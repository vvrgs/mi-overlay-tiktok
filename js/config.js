/* ============================================================
 * CONFIGURACIÓN DEL OVERLAY — edita aquí sin tocar el resto
 * ============================================================ */
const CONFIG = {
  // --- Tablero ---
  cols: 4,           // columnas jugables a cada lado del centro (-4..4 = 9 casillas)
  sideCols: 9,       // casillas decorativas (oscuras) a cada lado
  startSafeRows: 3,  // filas de pasto seguro al inicio
  spawnX: 16,        // distancia a la que aparecen/desaparecen vehículos

  // --- Cámara / dificultad ---
  cameraCreepBase: 0.40,   // velocidad base de avance automático (casillas/seg)
  cameraCreepMax: 1.35,
  behindDeathRows: 7,      // si te quedas tantas filas atrás de la cámara, mueres
  idleEagleSec: 10,        // segundos sin moverse para que venga el águila

  // --- Partida ---
  autoRestartSec: 4,       // segundos para reiniciar tras morir
  maxShields: 3,

  // --- Conexión TikTok (TikFinity Events API) ---
  ws: {
    url: 'ws://localhost:21213/',  // se puede sobreescribir con ?ws=...
    reconnectMs: 4000,
  },

  // --- Regalos → acciones ---
  // La clave es el nombre del regalo de TikTok en minúsculas.
  // Acciones disponibles:
  //   moveLeft, moveRight, reset, saveRun,
  //   superTruck, volcano, earthquake, tornado, ufo, lightning
  giftMap: {
    'rose':           'moveLeft',
    'finger heart':   'moveRight',
    'gg':             'reset',
    'ice cream cone': 'saveRun',
    'doughnut':       'saveRun',
    'cap':            'superTruck',
    'money gun':      'volcano',
    'swan':           'tornado',
    'corgi':          'earthquake',
    'galaxy':         'ufo',
    'perfume':        'lightning',
  },

  // --- Comandos de chat (opcional) ---
  chatCommands: {
    enabled: false,
    map: { '!izq': 'moveLeft', '!der': 'moveRight' },
  },

  // --- Likes (opcional): cada N likes dispara una acción ---
  likes: { enabled: false, per: 200, action: 'moveRight' },

  // --- Audio ---
  audio: { enabled: true, volume: 0.5 },
};

/* Entradas del panel lateral (icono, etiqueta y acción) */
const PANEL_ITEMS = [
  { action: 'reset',      icon: '💎', label: 'RESET' },
  { action: 'saveRun',    icon: '🍓', label: 'SAVE THE RUN' },
  { action: 'moveLeft',   icon: '🪽', label: 'MOVE LEFT' },
  { action: 'moveRight',  icon: '💗', label: 'MOVE RIGHT' },
  { action: 'superTruck', icon: '🚛', label: 'SUPER CAMIÓN' },
  { action: 'volcano',    icon: '🌋', label: 'VOLCÁN' },
  { action: 'earthquake', icon: '🫨', label: 'TERREMOTO' },
  { action: 'tornado',    icon: '🌪️', label: 'TORNADO' },
  { action: 'ufo',        icon: '🛸', label: 'OVNI' },
  { action: 'lightning',  icon: '⚡', label: 'RAYO' },
];
