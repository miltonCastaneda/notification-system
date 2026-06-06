package com.notification.procesador.repository;

import com.notification.procesador.domain.EstadoNotificacion;
import com.notification.procesador.domain.Notificacion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio MongoDB para el Micro 2.
 *
 * A diferencia del Micro 1 que solo escribe,
 * este repositorio lee y actualiza estados.
 */
@Repository
public interface NotificacionRepository extends MongoRepository<Notificacion, String> {

    // Busca notificaciones por estado — usado en warm-up
    List<Notificacion> findByEstado(EstadoNotificacion estado);

    // Busca por clave de idempotencia — verificación de duplicados
    Optional<Notificacion> findByClaveIdempotencia(String claveIdempotencia);

    // Recupera PROCESANDO huérfanos — fallaron antes de completar
    List<Notificacion> findByEstadoAndFechaActualizacionBefore(
            EstadoNotificacion estado,
            LocalDateTime fechaLimite
    );
}
