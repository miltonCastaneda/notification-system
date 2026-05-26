package com.notification.procesador.service;

import com.notification.procesador.domain.Notificacion;
import com.notification.procesador.domain.PreferenciaUsuario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Servicio de envío por canal.
 *
 * Por ahora simula el envío con logs.
 * En producción aquí conectarías:
 * - EMAIL → AWS SES
 * - SMS   → AWS SNS
 * - PUSH  → AWS SNS + FCM/APNS
 */
@Slf4j
@Service
public class EnvioService {

    /**
     * Envía la notificación por el canal correspondiente.
     * Retorna true si el envío fue exitoso.
     */
    public boolean enviar(Notificacion notificacion, PreferenciaUsuario preferencia) {
        return switch (notificacion.getCanal()) {
            case "EMAIL" -> enviarEmail(notificacion, preferencia);
            case "SMS"   -> enviarSms(notificacion, preferencia);
            case "PUSH"  -> enviarPush(notificacion, preferencia);
            default -> {
                log.warn("Canal no soportado: {}", notificacion.getCanal());
                yield false;
            }
        };
    }

    private boolean enviarEmail(Notificacion notificacion, PreferenciaUsuario preferencia) {
        if (!preferencia.isEmailActivo() || preferencia.getEmail() == null) {
            log.info("Email no activo para usuarioId={}", notificacion.getUsuarioId());
            return false;
        }

        // TODO: integrar AWS SES
        // sesClient.sendEmail(preferencia.getEmail(), notificacion.getMensaje());
        log.info("EMAIL enviado → para={} mensaje='{}'",
                preferencia.getEmail(), notificacion.getMensaje());
        return true;
    }

    private boolean enviarSms(Notificacion notificacion, PreferenciaUsuario preferencia) {
        if (!preferencia.isSmsActivo() || preferencia.getTelefono() == null) {
            log.info("SMS no activo para usuarioId={}", notificacion.getUsuarioId());
            return false;
        }

        // TODO: integrar AWS SNS
        // snsClient.publish(preferencia.getTelefono(), notificacion.getMensaje());
        log.info("SMS enviado → para={} mensaje='{}'",
                preferencia.getTelefono(), notificacion.getMensaje());
        return true;
    }

    private boolean enviarPush(Notificacion notificacion, PreferenciaUsuario preferencia) {
        if (!preferencia.isPushActivo() || preferencia.getDeviceToken() == null) {
            log.info("Push no activo para usuarioId={}", notificacion.getUsuarioId());
            return false;
        }

        // TODO: integrar AWS SNS + FCM
        // snsClient.publishToEndpoint(preferencia.getDeviceToken(), notificacion.getMensaje());
        log.info("PUSH enviado → deviceToken={} mensaje='{}'",
                preferencia.getDeviceToken(), notificacion.getMensaje());
        return true;
    }
}
