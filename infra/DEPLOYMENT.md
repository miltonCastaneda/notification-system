# Guía de despliegue — Sistema de Notificaciones

## Herramientas necesarias

```bash
# Instalar Terraform
brew install terraform         # Mac
choco install terraform        # Windows
sudo apt install terraform     # Ubuntu

# Instalar AWS CLI
pip install awscli

# Instalar kubectl
brew install kubectl

# Instalar Cursor (IDE con IA)
# Descargar desde: https://cursor.sh
```

---

## Paso 1 — Configurar AWS CLI

```bash
aws configure
# AWS Access Key ID: [tu key]
# AWS Secret Access Key: [tu secret]
# Default region name: us-east-1
# Default output format: json
```

---

## Paso 2 — Crear bucket S3 para el estado de Terraform

```bash
aws s3 mb s3://notification-system-tfstate --region us-east-1
aws s3api put-bucket-versioning \
  --bucket notification-system-tfstate \
  --versioning-configuration Status=Enabled
```

---

## Paso 3 — Inicializar y aplicar Terraform

```bash
cd notification-infra/

# Inicializar proveedores
terraform init

# Ver qué va a crear (sin crear nada)
terraform plan -var="documentdb_password=TuPassword123!"

# Crear la infraestructura
terraform apply -var="documentdb_password=TuPassword123!"

# Esto tarda ~15-20 minutos (EKS y MSK son lentos en provisionar)
```

---

## Paso 4 — Conectar kubectl al cluster EKS

```bash
aws eks update-kubeconfig \
  --name notification-system-eks \
  --region us-east-1

# Verificar conexión
kubectl get nodes
```

---

## Paso 5 — Construir y publicar imágenes Docker

```bash
# Login al ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  [TU_ACCOUNT_ID].dkr.ecr.us-east-1.amazonaws.com

# Micro 1 — Idempotencia
cd micro1-idempotencia/
docker build -t notification-system/micro1-idempotencia .
docker tag notification-system/micro1-idempotencia:latest \
  [ECR_URL_MICRO1]:latest
docker push [ECR_URL_MICRO1]:latest

# Micro 2 — Procesador de envío
cd ../micro2-procesador/
docker build -t notification-system/micro2-procesador .
docker tag notification-system/micro2-procesador:latest \
  [ECR_URL_MICRO2]:latest
docker push [ECR_URL_MICRO2]:latest
```

---

## Paso 6 — Desplegar microservicios en EKS

```yaml
# micro1-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: micro1-idempotencia
spec:
  replicas: 2
  selector:
    matchLabels:
      app: micro1-idempotencia
  template:
    metadata:
      labels:
        app: micro1-idempotencia
    spec:
      containers:
      - name: micro1
        image: [ECR_URL_MICRO1]:latest
        env:
        - name: KAFKA_BROKERS
          valueFrom:
            secretKeyRef:
              name: kafka-secret
              key: brokers
        - name: REDIS_HOST
          value: "[REDIS_ENDPOINT]"
        - name: MONGODB_URI
          valueFrom:
            secretKeyRef:
              name: docdb-secret
              key: uri
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
```

```bash
kubectl apply -f micro1-deployment.yaml
kubectl apply -f micro2-deployment.yaml
kubectl get pods
```

---

## Paso 7 — Crear los topics de Kafka

```bash
# Obtener brokers
BROKERS=$(terraform output -raw msk_bootstrap_brokers)

# Crear topics
kafka-topics.sh --create \
  --bootstrap-server $BROKERS \
  --topic transacciones-raw \
  --partitions 6 \
  --replication-factor 3

kafka-topics.sh --create \
  --bootstrap-server $BROKERS \
  --topic notificaciones-pendientes \
  --partitions 6 \
  --replication-factor 3
```

---

## Paso 8 — Verificar que todo funciona

```bash
# Ver pods corriendo
kubectl get pods -A

# Ver logs del Micro 1
kubectl logs -f deployment/micro1-idempotencia

# Ver logs del Micro 2
kubectl logs -f deployment/micro2-procesador

# Ver dashboard en CloudWatch
# AWS Console → CloudWatch → Dashboards → notification-system-dashboard
```

---

## Destruir la infraestructura (cuando no la necesites)

```bash
# IMPORTANTE: esto elimina todo y evita costos
terraform destroy -var="documentdb_password=TuPassword123!"
```

---

## Estimación de costos (us-east-1)

| Servicio       | Instancia      | Costo/mes aprox |
|----------------|----------------|-----------------|
| EKS Cluster    | Control plane  | ~$73            |
| EKS Nodes (3)  | t3.medium      | ~$90            |
| MSK (3 brokers)| kafka.m5.large | ~$450           |
| DocumentDB (2) | db.r6g.large   | ~$280           |
| ElastiCache (2)| cache.r6g.large| ~$190           |
| **Total**      |                | **~$1,083/mes** |

> Para desarrollo usa instancias más pequeñas:
> MSK: kafka.t3.small (~$50/mes)
> DocumentDB: db.t3.medium (~$60/mes)
> ElastiCache: cache.t3.micro (~$25/mes)
> **Total dev: ~$320/mes**
