package com.notification.idempotencia.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Entidad principal guardada en MongoDB.
 * Representa una notificación a enviar por canal específico.
 * La clave de idempotencia es transaccionId + canal.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notificaciones")
public class Notificacion {

    @Id
    private String id;

    // Clave de idempotencia: transaccionId + canal
    // Indexed unique garantiza que no haya duplicados en MongoDB
    @Indexed(unique = true)
    private String claveIdempotencia;

    private String transaccionId;
    private String usuarioId;
    private String canal;           // EMAIL, SMS, PUSH
    private String mensaje;
    private EstadoNotificacion estado;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private int intentos;
}
