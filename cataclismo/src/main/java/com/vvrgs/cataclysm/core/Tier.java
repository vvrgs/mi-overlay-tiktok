package com.vvrgs.cataclysm.core;

/**
 * Tier de concurrencia — la decision #1 de cada desastre.
 * Los regalos de TikTok llegan JUNTOS (100 rosas de golpe): la politica de
 * sesiones por tier es lo que evita 100 sesiones simultaneas.
 */
public enum Tier {
    /** Spameable: 100 comandos = 100 incrementos de UNA cola que drena a ritmo fijo. */
    S,
    /** Solapable: hasta N simultaneos por jugador, con offset espacial por indice. */
    M,
    /** Cinematico: 1-2 a la vez por jugador, set-piece con entidades propias. */
    C,
    /** Ultra: exclusion GLOBAL (uno por servidor), el gran final. */
    U
}
