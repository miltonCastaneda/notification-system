package com.notification.procesador.service;

import com.notification.procesador.domain.EstadoNotificacion;
import com.notification.procesador.domain.Notificacion;
import com.notification.procesador.domain.PreferenciaUsuario;
import com.notification.procesador.repository.EnviadosRedisRepository;
import com.notification.procesador.repository.NotificacionRepository;
import com.notification.procesador.repository.PreferenciaUsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Servicio principal del Micro 2.
 *
 * Responsabilidades:
 * 1. Warm-up al arrancar: procesar PENDIENTES y PROCESANDO huérfanos
 * 2. Procesar notificaciones llegadas por Kafka
 * 3. Idempotencia: verificar cache antes de enviar
 * 4. Actualizar estado en MongoDB después del envío
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcesadorService {

    private final NotificacionRepository notificacionRepository;
    private final PreferenciaUsuarioRepository preferenciaRepository;
    private final EnviadosRedisRepository enviadosRedisRepository;
    private final EnvioService envioService;

    @Value("${envio.procesando-timeout-minutos:10}")
    private long procesandoTimeoutMinutos;

    // ─────────────────────────────────────────
    // WARM-UP AL ARRANCAR
    // ─────────────────────────────────────────

    /**
     * Se ejecuta automáticamente cuando el servicio arranca completamente.
     * Carga y procesa notificaciones pendientes antes de escuchar Kafka.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        log.info("Iniciando warm-up del Micro 2...");

        // Recuperar PENDIENTES
        List<Notificacion> pendientes = notificacionRepository
                .findByEstado(EstadoNotificacion.PENDIENTE);
        log.info("Warm-up: {} notificaciones PENDIENTES encontradas", pendientes.size());
        pendientes.forEach(this::procesarNotificacion);

        // Recuperar PROCESANDO huérfanos (fallaron hace más de X minutos)
        LocalDateTime limite = LocalDateTime.now().minusMinutes(procesandoTimeoutMinutos);
        List<Notificacion> huerfanos = notificacionRepository
                .findByEstadoAndFechaActualizacionBefore(EstadoNotificacion.PROCESANDO, limite);
        log.info("Warm-up: {} notificaciones PROCESANDO huérfanas encontradas", huerfanos.size());
        huerfanos.forEach(this::procesarNotificacion);

        log.info("Warm-up completado.");
    }

    // ─────────────────────────────────────────
    // PROCESAMIENTO PRINCIPAL
    // ─────────────────────────────────────────

    /**
     * Punto de entrada desde el consumer de Kafka.
     */
    public void procesarDesdeKafka(Notificacion notificacion) {
        log.info("Notificación recibida de Kafka. claveIdempotencia={}",
                notificacion.getClaveIdempotencia());
        procesarNotificacion(notificacion);
    }

    /**
     * Flujo completo de procesamiento con idempotencia.
     */
    private void procesarNotificacion(Notificacion notificacion) {
        String clave = notificacion.getClaveIdempotencia();

        // ── PASO 1: verificar en cache de enviados ──
        if (enviadosRedisRepository.yaEnviado(clave)) {
            log.info("Ya enviado (cache). clave={} → descartado", clave);
            return;
        }

        // ── PASO 2: verificar estado en MongoDB ──
        Optional<Notificacion> enBd = notificacionRepository
                .findByClaveIdempotencia(clave);

        if (enBd.isPresent() && enBd.get().getEstado() == EstadoNotificacion.ENVIADO) {
            log.info("Ya enviado (MongoDB). clave={} → descartado", clave);
            enviadosRedisRepository.registrarEnviado(clave);
            return;
        }

        // ── PASO 3: marcar como PROCESANDO (lock optimista) ──
        notificacion.setEstado(EstadoNotificacion.PROCESANDO);
        notificacion.setFechaActualizacion(LocalDateTime.now());
        notificacionRepository.save(notificacion);

        // ── PASO 4: consultar preferencias del usuario ──
        Optional<PreferenciaUsuario> preferencia = preferenciaRepository
                .findByUsuarioId(notificacion.getUsuarioId());

        if (preferencia.isEmpty()) {
            log.warn("Sin preferencias para usuarioId={}. Marcando como FALLIDO.",
                    notificacion.getUsuarioId());
            actualizarEstado(notificacion, EstadoNotificacion.FALLIDO);
            return;
        }

        // ── PASO 5: enviar por canal ──
        boolean exitoso = envioService.enviar(notificacion, preferencia.get());

        // ── PASO 6: actualizar estado final ──
        EstadoNotificacion estadoFinal = exitoso
                ? EstadoNotificacion.ENVIADO
                : EstadoNotificacion.FALLIDO;

        actualizarEstado(notificacion, estadoFinal);

        // ── PASO 7: registrar en cache si fue exitoso ──
        if (exitoso) {
            enviadosRedisRepository.registrarEnviado(clave);
            log.info("Notificación enviada exitosamente. clave={} canal={}",
                    clave, notificacion.getCanal());
        } else {
            log.warn("Envío fallido. clave={} canal={}", clave, notificacion.getCanal());
        }
    }

    private void actualizarEstado(Notificacion notificacion, EstadoNotificacion estado) {
        notificacion.setEstado(estado);
        notificacion.setFechaActualizacion(LocalDateTime.now());
        notificacion.setIntentos(notificacion.getIntentos() + 1);
        notificacionRepository.save(notificacion);
    }
}
