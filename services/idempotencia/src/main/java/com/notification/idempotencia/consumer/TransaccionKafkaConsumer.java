package com.notification.idempotencia.consumer;

import com.notification.idempotencia.domain.TransaccionEvento;
import com.notification.idempotencia.service.IdempotenciaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumidor Kafka del Micro 1.
 *
 * Escucha el topic transacciones-raw y delega al IdempotenciaService.
 * Usa acknowledgment manual para garantizar que el offset solo se confirma
 * cuando el evento fue procesado exitosamente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransaccionKafkaConsumer {

    private final IdempotenciaService idempotenciaService;

    @KafkaListener(
            topics = "${kafka.topics.transacciones-raw}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void escuchar(
            @Payload TransaccionEvento evento,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Evento recibido → topic={} particion={} offset={} transaccionId={}",
                topic, particion, offset, evento.getTransaccionId());

        try {
            idempotenciaService.procesarEvento(evento);

            // Confirmar offset SOLO si el procesamiento fue exitoso
            // Si no se confirma, Kafka reintentará el mensaje
            acknowledgment.acknowledge();
            log.info("Offset confirmado → transaccionId={}", evento.getTransaccionId());

        } catch (Exception e) {
            // No confirmar el offset → Kafka reintentará
            log.error("Error procesando evento transaccionId={} → {}",
                    evento.getTransaccionId(), e.getMessage());
        }
    }
}
