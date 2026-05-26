package com.notification.procesador.consumer;

import com.notification.procesador.domain.Notificacion;
import com.notification.procesador.service.ProcesadorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumidor Kafka del Micro 2.
 * Escucha notificaciones-pendientes publicadas por el Micro 1.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacionKafkaConsumer {

    private final ProcesadorService procesadorService;

    @KafkaListener(
            topics = "${kafka.topics.notificaciones-pendientes}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void escuchar(
            @Payload Notificacion notificacion,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Notificación recibida → topic={} particion={} offset={} clave={}",
                topic, particion, offset, notificacion.getClaveIdempotencia());

        try {
            procesadorService.procesarDesdeKafka(notificacion);
            acknowledgment.acknowledge();
            log.info("Offset confirmado → clave={}", notificacion.getClaveIdempotencia());
        } catch (Exception e) {
            log.error("Error procesando notificación clave={} → {}",
                    notificacion.getClaveIdempotencia(), e.getMessage());
        }
    }
}
