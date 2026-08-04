package com.vvrgs.cataclysm.fx;

/**
 * Eventos de FX: un packet servidor->cliente por evento; el cliente lo
 * expande en una RECETA CRONOLOGICA (acciones con delay en ticks).
 *
 * El nombre en minusculas de cada constante es tambien la clave del
 * effeks/manifest.json (hot-swap de effeks de Effekseer sin tocar codigo).
 */
public enum FxEvent {
    /** Reticula pulsante de telegraph: anillo dorado + motas convergentes.
     *  data = duracion del telegraph en ticks (debe cubrir hasta el impacto). */
    TELEGRAPH,
    /** Impacto de meteorito pequeno: flash, bola de fuego, escombros, humo. */
    METEOR_IMPACT,
    /** Ionizacion pre-rayo: motas electricas ascendentes 8 ticks. */
    IONIZATION,
    /** Descarga de rayo dirigido: rama fractal propia + flash. data=alturaOrigen. */
    LIGHTNING_BOLT,
    /** Burst de frente de fuego: llamas + brasas a contraluz. */
    FIRE_BURST,
    /** Campo de brasas ascendentes sostenido (1 burst por llamada). */
    EMBER_FIELD,
    /** Columna de humo denso que oscurece. */
    SMOKE_COLUMN,
    /** Celula de lluvia de la tormenta / bandas del huracan. data=radio. */
    RAIN_CELL,
    /** Rafaga de viento visible (motas de polvo horizontales). data=yaw en grados. */
    WIND_GUST,
    /** Polvo y bloques sueltos del temblor. */
    QUAKE_DUST,
    /** Apertura de fisura: erupcion de polvo balistico a lo largo. data=yaw. */
    FISSURE_BURST,
    /** Escombros orbitando arrancados por el tornado. data=blockStateId. */
    TORNADO_DEBRIS,
    /** Espuma/bruma en la cresta del muro de agua. */
    TSUNAMI_SPRAY,
    /** Fuente de lava del volcan. */
    LAVA_FOUNTAIN,
    /** Boom de erupcion: flash + hongo + bombas. */
    ERUPTION_BLAST,
    /** Muro gris del flujo piroclastico avanzando. data=yaw. */
    PYROCLASTIC_FRONT,
    /** Lluvia de ceniza ambiental. data=radio. */
    ASH_FALL,
    /** Flash blanco total del impacto planetario (falloff por distancia). */
    IMPACT_FLASH,
    /** Onda expansiva visible que avanza como anillo. data=radioMax. */
    SHOCKWAVE_RING,
    /** Hongo de polvo del impacto. */
    IMPACT_MUSHROOM,
    /** Eyecta balistica incendiaria. */
    EJECTA,
    /** Pop de totem: burst dorado-verde propio + shake corto. */
    TOTEM_POP,
    /** Ancla de la ejecucion: columna estroboscopica en el punto. */
    EXECUTION_ANCHOR,
    /** Presagio del apocalipsis (portal cosmico sobre el mundo). */
    APOCALYPSE_OMEN;

    public static final FxEvent[] VALUES = values();

    /** Clave del manifest de effeks (hot-swap). */
    public String manifestKey() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static FxEvent byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : TELEGRAPH;
    }
}
