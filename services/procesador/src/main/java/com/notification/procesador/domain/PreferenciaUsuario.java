package com.notification.procesador.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Preferencias de notificación del usuario.
 * Define qué canales tiene activos cada usuario.
 *
 * En producción este documento lo gestiona el microservicio
 * de configuración de perfil. Aquí lo usamos de lectura.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "preferencias_usuario")
public class PreferenciaUsuario {

    @Id
    private String id;

    @Indexed(unique = true)
    private String usuarioId;

    // Canales activos por usuario
    private boolean emailActivo;
    private String email;

    private boolean smsActivo;
    private String telefono;

    private boolean pushActivo;
    private String deviceToken;   // token del dispositivo móvil
}
