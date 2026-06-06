# ─────────────────────────────────────────
# STAGE 1 — BUILD
# Compila el proyecto con Gradle
# ─────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder
LABEL authors="mfcnd"
WORKDIR /app

# Copiar archivos de configuración de Gradle primero
# Esto aprovecha el cache de Docker — si no cambian las dependencias
# no se vuelven a descargar en cada build
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Dar permisos de ejecución al wrapper
RUN chmod +x gradlew

# Descargar dependencias (cacheadas si build.gradle no cambió)
RUN ./gradlew dependencies --no-daemon

# Copiar el código fuente
COPY src src

# Compilar y generar el JAR sin ejecutar tests
RUN ./gradlew bootJar --no-daemon -x test

# ─────────────────────────────────────────
# STAGE 2 — RUNTIME
# Imagen final liviana solo con el JAR
# ─────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Usuario no-root por seguridad — buena práctica en producción
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copiar solo el JAR desde el stage de build
COPY --from=builder /app/build/libs/*.jar app.jar

# Puerto del microservicio
EXPOSE 8080

# Variables de entorno con valores por defecto
# Se sobreescriben en Kubernetes via ConfigMap o Secrets
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms256m -Xmx512m"

# Arrancar el microservicio
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]