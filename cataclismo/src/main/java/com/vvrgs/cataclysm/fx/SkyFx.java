package com.vvrgs.cataclysm.fx;

/**
 * Estados de cielo renderizados via RenderLevelStageEvent (AFTER_SKY):
 * objetos celestes con depth-test off y fog-aware, legibles a cualquier
 * distancia. Se activan por packet global con duracion e intensidad.
 */
public enum SkyFx {
    /** Punto de luz que CRECE durante segundos: el impactor se ve venir. */
    IMPACT_APPROACH,
    /** Estela cruzando el cielo en la entrada atmosferica. */
    ATMOSPHERE_ENTRY,
    /** Cielo tenido de rojo sangre (aviso tier U). */
    RED_TINT,
    /** Oscurecimiento por polvo/ceniza (post-impacto, post-erupcion). */
    ASH_DARKEN,
    /** Cielo amarillo sobrecogedor del ojo del huracan. */
    EYE_SEPIA,
    /** Agujero negro con disco de acrecion (presagio del apocalipsis). */
    BLACK_HOLE,
    /** Corta un efecto concreto (duracion 0 = clear). */
    CLEAR;

    public static final SkyFx[] VALUES = values();

    public static SkyFx byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : CLEAR;
    }
}
