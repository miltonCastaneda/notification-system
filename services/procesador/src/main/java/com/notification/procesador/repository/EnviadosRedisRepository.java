package com.notification.procesador.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * Cache Redis de notificaciones ya enviadas.
 *
 * Clave: "enviado:{claveIdempotencia}"
 * Valor: "ENVIADO"
 * TTL:   24 horas
 *
 * Evita procesar dos veces una notificación cuando
 * llega tanto por Kafka como por el warm-up polling.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class EnviadosRedisRepository {

    private final StringRedisTemplate redisTemplate;

    @Value("${envio.ttl-enviados-horas:24}")
    private long ttlHoras;

    private static final String PREFIX = "enviado:";

    public boolean yaEnviado(String claveIdempotencia) {
        Boolean existe = redisTemplate.hasKey(PREFIX + claveIdempotencia);
        return Boolean.TRUE.equals(existe);
    }

    public void registrarEnviado(String claveIdempotencia) {
        redisTemplate.opsForValue()
                .set(PREFIX + claveIdempotencia, "ENVIADO", Duration.ofHours(ttlHoras));
        log.debug("Notificación registrada como enviada en cache: {}", claveIdempotencia);
    }
}
