package com.vvrgs.irontempest.net;

/**
 * Eventos de FX compuestos. El servidor decide CUÁNDO; el cliente (FxDirector)
 * compone la cronología exacta de partículas/shake/flash/post-shader/sonido local.
 */
public enum FxType {
    /** Impacto de cohete MLRS. dir = normal aprox del impacto. scale ~ 1. */
    EXPLOSION_SMALL,
    /** Impacto grande (obús / misil en suelo). scale 1.0—2.5. */
    EXPLOSION_LARGE,
    /** Detonación de proximidad en el aire (misil crucero). */
    AIRBURST,
    /** Fogonazo de cañón. dir = dirección del disparo. */
    MUZZLE_FLASH,
    /** Aterrizaje del drop-pod del tanque: polvo + chispas + clank. */
    TANK_LANDING,
    /** Destello de salto de entrada de la nave. */
    WARP_IN,
    /** Destello de salto de salida. */
    WARP_OUT,
    /** Ráfaga periódica de carga del cañón orbital (motas convergentes). dir = hacia el emisor. */
    CHARGE_BURST,
    /** Punto de barrido del haz en el suelo (chispas/brasas/humo). dir = dirección del haz. */
    BEAM_SWEEP,
    /** Pulso final AoE del haz orbital. */
    OVERLOAD_PULSE,
    /** Apertura del armagedón: flash rojo + columnas de humo. */
    ARMAGEDDON_OPENING,
    /** Quemadura cosmética pequeña (tier S). */
    SCORCH,
    /** Venteo del silo antes del lanzamiento del misil (vapor + polvo). */
    SILO_VENT,
    /** Lluvia de brasas del epílogo. scale = radio del área. */
    DEBRIS_RAIN,
    /** Solo trauma de cámara (sin partículas). scale = trauma 0..1. */
    SHAKE_ONLY;

    public static final FxType[] VALUES = values();
}
