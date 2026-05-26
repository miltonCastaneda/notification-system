package com.notification.idempotencia.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa el evento de transacción que llega desde Kafka.
 * Publicado por los servicios de pagos, tarjetas, ahorros, etc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransaccionEvento {

    // ID único de la transacción — base de la clave de idempotencia
    private String transaccionId;

    // Usuario que realizó la transacción
    private String usuarioId;

    // Tipo: PAGO, TRANSFERENCIA, RETIRO, CONSIGNACION
    private String tipoTransaccion;

    // Monto de la transacción
    private Double monto;

    // Producto bancario: TARJETA, CUENTA_AHORROS, CREDITO
    private String producto;

    // Timestamp del evento en milisegundos
    private Long timestamp;
}
