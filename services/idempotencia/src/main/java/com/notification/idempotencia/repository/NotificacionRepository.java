package com.notification.idempotencia.repository;

import com.notification.idempotencia.domain.EstadoNotificacion;
import com.notification.idempotencia.domain.Notificacion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio MongoDB para notificaciones.
 * Spring Data genera la implementación automáticamente.
 */
@Repository
public interface NotificacionRepository extends MongoRepository<Notificacion, String> {

    // Busca por clave de idempotencia (transaccionId + canal)
    // Usado para verificar si ya existe antes de procesar
    Optional<Notificacion> findByClaveIdempotencia(String claveIdempotencia);

    // Verifica existencia sin traer el objeto completo — más eficiente
    boolean existsByClaveIdempotencia(String claveIdempotencia);
}
