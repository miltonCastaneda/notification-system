package com.notification.idempotencia.service;

import com.notification.idempotencia.domain.EstadoNotificacion;
import com.notification.idempotencia.domain.Notificacion;
import com.notification.idempotencia.domain.TransaccionEvento;
import com.notification.idempotencia.repository.IdempotenciaRedisRepository;
import com.notification.idempotencia.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Servicio principal del Micro 1.
 *
 * Responsabilidades:
 * 1. Verificar idempotencia (Redis → MongoDB)
 * 2. Crear notificaciones PENDIENTES por canal
 * 3. Publicar al topic de notificaciones-pendientes para Micro 2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotenciaService {

    private final IdempotenciaRedisRepository redisRepository;
    private final NotificacionRepository notificacionRepository;
    private final KafkaTemplate<String, Notificacion> kafkaTemplate;

    // Canales soportados — en el futuro pueden venir de las preferencias del usuario
    private static final List<String> CANALES = List.of("EMAIL", "SMS", "PUSH");

    @Value("${kafka.topics.notificaciones-pendientes}")
    private String topicNotificacionesPendientes;

    /**
     * Punto de entrada principal.
     * Recibe el evento de transacción y orquesta todo el flujo.
     */
    public void procesarEvento(TransaccionEvento evento) {
        log.info("Procesando evento transaccionId={} usuarioId={}",
                evento.getTransaccionId(), evento.getUsuarioId());

        for (String canal : CANALES) {
            procesarPorCanal(evento, canal);
        }
    }

    /**
     * Procesa el evento para un canal específico.
     * Verifica idempotencia antes de crear la notificación.
     */
    private void procesarPorCanal(TransaccionEvento evento, String canal) {
        String transaccionId = evento.getTransaccionId();

        // ── PASO 1: verificar en Redis (rápido, en memoria) ──
        if (redisRepository.existeClave(transaccionId, canal)) {
            log.info("Duplicado detectado en cache. transaccionId={} canal={} → descartado",
                    transaccionId, canal);
            return;
        }

        // ── PASO 2: verificar en MongoDB (segunda línea de defensa) ──
        // Cubre el caso donde Redis se reinició y perdió el cache
        String claveIdempotencia = buildClaveIdempotencia(transaccionId, canal);
        if (notificacionRepository.existsByClaveIdempotencia(claveIdempotencia)) {
            log.info("Duplicado detectado en MongoDB. transaccionId={} canal={} → descartado",
                    transaccionId, canal);
            // Restaurar en cache para evitar consultas futuras a MongoDB
            redisRepository.registrarProcesado(transaccionId, canal);
            return;
        }

        // ── PASO 3: crear notificación PENDIENTE en MongoDB ──
        Notificacion notificacion = crearNotificacion(evento, canal, claveIdempotencia);
        try {
            notificacionRepository.save(notificacion);
            log.info("Notificación guardada. transaccionId={} canal={} id={}",
                    transaccionId, canal, notificacion.getId());
        } catch (Exception e) {
            // Si MongoDB rechaza por clave duplicada (índice único)
            // es otro proceso que llegó al mismo tiempo → descartar
            log.warn("Conflicto al guardar notificación. transaccionId={} canal={} → {}",
                    transaccionId, canal, e.getMessage());
            return;
        }

        // ── PASO 4: registrar en Redis cache ──
        redisRepository.registrarProcesado(transaccionId, canal);

        // ── PASO 5: publicar al topic para Micro 2 ──
        kafkaTemplate.send(topicNotificacionesPendientes, transaccionId, notificacion);
        log.info("Notificación publicada a Kafka. transaccionId={} canal={}",
                transaccionId, canal);
    }

    /**
     * Construye la notificación con estado PENDIENTE.
     */
    private Notificacion crearNotificacion(TransaccionEvento evento,
                                            String canal,
                                            String claveIdempotencia) {
        return Notificacion.builder()
                .claveIdempotencia(claveIdempotencia)
                .transaccionId(evento.getTransaccionId())
                .usuarioId(evento.getUsuarioId())
                .canal(canal)
                .mensaje(buildMensaje(evento))
                .estado(EstadoNotificacion.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .fechaActualizacion(LocalDateTime.now())
                .intentos(0)
                .build();
    }

    /**
     * Construye el mensaje de notificación según el tipo de transacción.
     */
    private String buildMensaje(TransaccionEvento evento) {
        return String.format("Transacción %s por $%.2f realizada exitosamente.",
                evento.getTipoTransaccion(), evento.getMonto());
    }

    /**
     * Clave de idempotencia: transaccionId:canal
     * Igual al formato usado en Redis para consistencia.
     */
    private String buildClaveIdempotencia(String transaccionId, String canal) {
        return transaccionId + ":" + canal;
    }
}
