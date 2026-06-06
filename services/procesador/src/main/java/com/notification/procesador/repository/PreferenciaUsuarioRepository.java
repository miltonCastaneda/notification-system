package com.notification.procesador.repository;

import com.notification.procesador.domain.PreferenciaUsuario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio de preferencias de usuario.
 * Solo lectura desde el Micro 2.
 */
@Repository
public interface PreferenciaUsuarioRepository extends MongoRepository<PreferenciaUsuario, String> {

    Optional<PreferenciaUsuario> findByUsuarioId(String usuarioId);
}
