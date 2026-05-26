package com.notification.idempotencia.domain;

/**
 * Estados del ciclo de vida de una notificación.
 *
 * PENDIENTE   → registrada, aún no procesada por Micro 2
 * PROCESANDO  → Micro 2 la tomó (lock optimista)
 * ENVIADO     → confirmado exitosamente
 * FALLIDO     → agotó reintentos
 */
public enum EstadoNotificacion {
    PENDIENTE,
    PROCESANDO,
    ENVIADO,
    FALLIDO
}
