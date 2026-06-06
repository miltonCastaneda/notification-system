package com.notification.idempotencia.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * Repositorio Redis para el cache de duplicados.
 *
 * Clave: "idempotencia:{transaccionId}:{canal}"
 * Valor: "PROCESADO"
 * TTL:   24 horas (configurable en application.yml)
 *
 * Es la primera línea de verificación — más rápida que MongoDB.
 * Si está en cache → duplicado → descartar sin tocar la BD.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class IdempotenciaRedisRepository {

    private final StringRedisTemplate redisTemplate;

    @Value("${idempotencia.ttl-horas:24}")
    private long ttlHoras;

    private static final String PREFIX = "idempotencia:";

    /**
     * Verifica si el evento ya fue procesado.
     */
    public boolean existeClave(String transaccionId, String canal) {
        String clave = buildClave(transaccionId, canal);
        Boolean existe = redisTemplate.hasKey(clave);
        return Boolean.TRUE.equals(existe);
    }

    /**
     * Registra el evento como procesado con TTL de 24 horas.
     * Se llama después de guardar exitosamente en MongoDB.
     */
    public void registrarProcesado(String transaccionId, String canal) {
        String clave = buildClave(transaccionId, canal);
        redisTemplate.opsForValue().set(clave, "PROCESADO", Duration.ofHours(ttlHoras));
        log.debug("Clave registrada en cache: {}", clave);
    }

    /**
     * Construye la clave de Redis en formato: idempotencia:transaccionId:canal
     * Coincide con la claveIdempotencia de MongoDB para consistencia.
     */
    public String buildClave(String transaccionId, String canal) {
        return PREFIX + transaccionId + ":" + canal;
    }
}
