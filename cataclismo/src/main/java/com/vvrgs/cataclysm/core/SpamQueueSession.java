package com.vvrgs.cataclysm.core;

/**
 * Sesion tier S: 100 comandos = 100 incrementos de UNA cola por jugador que
 * drena a ritmo fijo. Jamas una sesion por comando.
 */
public interface SpamQueueSession {

    /** Suma unidades a la cola. @return false si la cola esta llena (feedback queue_full). */
    boolean enqueueSpam(int amount);

    /** Unidades pendientes (para el actionbar de cola). */
    int pendingSpam();
}
