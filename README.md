# 🔔 Notification System

Sistema de notificaciones bancarias en tiempo real construido con arquitectura de microservicios, mensajería asíncrona y despliegue en AWS.

---

## 📐 Arquitectura

```
Sistemas externos (Pagos, Tarjetas, transferencia, ventas)
              ↓
         Kafka (MSK)
         topic: transacciones-raw
              ↓
   ┌─────────────────────┐
   │  Micro 1            │
   │  Idempotencia       │  → Verifica duplicados (Redis + MongoDB)
   │                     │  → Crea notificación PENDIENTE
   └─────────────────────┘
              ↓
         Kafka (MSK)
         topic: notificaciones-pendientes
              ↓
   ┌─────────────────────┐
   │  Micro 2            │
   │  Procesador         │  → Consulta preferencias del usuario
   │                     │  → Envía por EMAIL / SMS / PUSH
   └─────────────────────┘
              ↓
     AWS SES / SNS / FCM
```

---

## 🧩 Componentes

| Componente | Tecnología | Responsabilidad |
|---|---|---|
| Micro 1 | Spring Boot + Kafka | Idempotencia y registro de notificaciones |
| Micro 2 | Spring Boot + Kafka | Procesamiento y envío por canal |
| Mensajería | Apache Kafka (AWS MSK) | Comunicación asíncrona entre servicios |
| Base de datos | MongoDB (AWS DocumentDB) | Persistencia de notificaciones y preferencias |
| Cache | Redis (AWS ElastiCache) | Cache de duplicados con TTL de 24h |
| Infraestructura | Terraform + AWS | EKS Fargate, MSK, DocumentDB, ElastiCache |
| Monitoreo | AWS CloudWatch | Dashboard, alertas y logs centralizados |

---

## 🔑 Decisiones de arquitectura

### Idempotencia en dos capas
Para garantizar que ninguna notificación se envíe dos veces, incluso ante reinicios o fallos:

1. **Redis** — primera verificación, rápida e in-memory. TTL de 24 horas.
2. **MongoDB** — segunda verificación con índice único en `transaccionId:canal`. Cubre el caso de reinicio del cache.

La clave de idempotencia es `transaccionId:canal`, no solo `transaccionId`, porque un evento puede generar múltiples notificaciones (EMAIL, SMS, PUSH).

### Entrega garantizada con `acks=all`
El producer de Kafka está configurado con `acks=all` y `enable.idempotence=true`. Esto garantiza que el mensaje no se pierda aunque un broker falle durante la replicación.

### Acknowledgment manual
Ambos microservicios confirman el offset de Kafka manualmente, solo cuando el procesamiento fue exitoso. Si el servicio falla, Kafka reintenta la entrega.

### Estados del ciclo de vida
```
PENDIENTE → PROCESANDO → ENVIADO
                       → FALLIDO
```
El estado `PROCESANDO` actúa como lock optimista para evitar race conditions entre instancias del Micro 2.

### Warm-up on startup
Al arrancar el Micro 2 carga notificaciones en estado `PENDIENTE` y `PROCESANDO` huérfanas desde MongoDB antes de empezar a consumir Kafka. Esto garantiza que ninguna notificación quede sin procesar después de una caída.

### Por qué no Outbox Pattern ni Debezium
En un entorno con múltiples equipos clientes y bases de datos independientes, el Outbox Pattern requiere coordinación organizacional alta, por ende se optó por `acks=all` en kafka y con idempotencia como solución pragmática suficiente para notificaciones con reintentos sin duplicidad.

---

## 🗂 Estructura del repositorio

```
notification-system/
├── .github/
│   └── workflows/
│       ├── micro1.yml          → CI/CD Micro 1
│       └── micro2.yml          → CI/CD Micro 2
├── infra/
│   └── terraform/
│       ├── main.tf             → configuración base y backend S3
│       ├── variables.tf        → variables del proyecto
│       ├── networking.tf       → VPC, subnets, NAT Gateway
│       ├── msk.tf              → Kafka cluster
│       ├── databases.tf        → DocumentDB + ElastiCache Redis
│       ├── eks.tf              → EKS + Fargate + ECR
│       ├── messaging.tf        → SNS, SQS, SES, CloudWatch
│       └── outputs.tf          → endpoints de todos los servicios
├── services/
│   ├── micro1-idempotencia/    → Spring Boot — verificación y registro
│   └── micro2-procesador/      → Spring Boot — envío por canal
├── docker-compose.yml          → entorno local de desarrollo
└── README.md
```

---

## 🚀 Inicio rápido (desarrollo local)

### Prerrequisitos
- Java 17
- Docker Desktop
- Gradle

### 1. Levantar infraestructura local

```bash
docker-compose up -d
```

Servicios disponibles:
| Servicio | URL |
|---|---|
| Kafka | localhost:9092 |
| Kafka UI | http://localhost:8090 |
| MongoDB | localhost:27017 |
| Redis | localhost:6379 |

### 2. Crear topic de Kafka

```bash
docker exec -it kafka kafka-topics \
  --create \
  --bootstrap-server localhost:9092 \
  --topic transacciones-raw \
  --partitions 1 \
  --replication-factor 1
```

### 3. Insertar preferencias de usuario de prueba

```bash
docker exec -it mongodb mongosh \
  -u notifadmin -p NotifLocal2024 \
  --authenticationDatabase admin
```

```javascript
use notificaciones
db.preferencias_usuario.insertOne({
  usuarioId: "USER-123",
  emailActivo: true,
  email: "usuario@test.com",
  smsActivo: true,
  telefono: "+573001234567",
  pushActivo: true,
  deviceToken: "token-dispositivo-123"
})
```

### 4. Arrancar los microservicios

```bash
# Terminal 1 — Micro 1
cd services/micro1-idempotencia
./gradlew bootRun

# Terminal 2 — Micro 2
cd services/micro2-procesador
./gradlew bootRun -x test
```

### 5. Enviar evento de prueba

```bash
docker exec -it kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic transacciones-raw
```

```json
{"transaccionId":"TXN-001","usuarioId":"USER-123","tipoTransaccion":"PAGO","monto":50000.00,"producto":"TARJETA","timestamp":1748000000000}
```

---

## ☁️ Despliegue en AWS

### Prerrequisitos
- AWS CLI configurado
- Terraform >= 1.5.0
- kubectl

### 1. Crear bucket de estado

```bash
aws s3 mb s3://notification-system-tfstate-{ACCOUNT_ID} --region us-east-1
```

### 2. Aplicar infraestructura

```bash
cd infra/terraform
terraform init
terraform plan -var="documentdb_password=TuPassword"
terraform apply -var="documentdb_password=TuPassword"
```

### 3. Conectar kubectl al cluster

```bash
aws eks update-kubeconfig \
  --name notification-system-eks \
  --region us-east-1
```

### Estimación de costos (us-east-1)

| Servicio | Instancia | Costo/mes |
|---|---|---|
| EKS + Fargate | — | ~$73 + uso |
| MSK | kafka.t3.small | ~$50 |
| DocumentDB | db.t3.medium | ~$60 |
| ElastiCache | cache.t3.micro | ~$25 |
| **Total dev** | | **~$220/mes** |

> ⚠️ Ejecuta `terraform destroy` cuando no estés trabajando para evitar costos innecesarios.

---

## 🧪 Tests

```bash
# Micro 1
cd services/micro1-idempotencia
./gradlew test

# Micro 2
cd services/micro2-procesador
./gradlew test
```

> Tests unitarios con Mockito — sin dependencias externas reales.

---

## 📋 Pendientes

- [ ] Integrar AWS SES para envío real de EMAIL
- [ ] Integrar AWS SNS para SMS y PUSH
- [ ] Dockerizar ambos microservicios
- [ ] CI/CD con GitHub Actions
- [ ] Tests unitarios con Mockito
- [ ] Configurar dominio y verificación SES

---

## 🛠 Stack tecnológico

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.14-green)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-3.5-black)
![MongoDB](https://img.shields.io/badge/MongoDB-7.0-green)
![Redis](https://img.shields.io/badge/Redis-7.2-red)
![AWS](https://img.shields.io/badge/AWS-EKS%20%7C%20MSK%20%7C%20DocumentDB-yellow)
![Terraform](https://img.shields.io/badge/Terraform-1.5+-purple)

---

## 👤 Autor

Desarrollado como proyecto de modernización de habilidades backend con enfoque en arquitecturas event-driven y despliegue cloud nativo.
