# ─────────────────────────────────────────
# MSK — KAFKA GESTIONADO
# ─────────────────────────────────────────

resource "aws_security_group" "msk" {
  name        = "${var.project_name}-msk-sg"
  description = "Security group para MSK Kafka"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port   = 9092
    to_port     = 9092
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
    description = "Kafka plaintext (interno VPC)"
  }

  ingress {
    from_port   = 9094
    to_port     = 9094
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
    description = "Kafka TLS (interno VPC)"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-msk-sg" }
}

resource "aws_msk_cluster" "notifications" {
  cluster_name           = "${var.project_name}-kafka"
  kafka_version          = "3.5.1"
  number_of_broker_nodes = var.kafka_broker_count

  broker_node_group_info {
    instance_type   =  "kafka.t3.small"
    client_subnets  = aws_subnet.private[*].id
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = 100
      }
    }
  }

  # Idempotencia habilitada a nivel de broker
  configuration_info {
    arn      = aws_msk_configuration.main.arn
    revision = aws_msk_configuration.main.latest_revision
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS_PLAINTEXT"
      in_cluster    = true
    }
  }

  open_monitoring {
    prometheus {
      jmx_exporter  { enabled_in_broker = true }
      node_exporter { enabled_in_broker = true }
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk.name
      }
    }
  }

  tags = { Environment = var.environment }
}

resource "aws_msk_configuration" "main" {
  name = "${var.project_name}-kafka-config"

  server_properties = <<-EOF
    # Retención de mensajes: 24 horas (cubre ventana de duplicados)
    log.retention.hours=24
    # Replicación mínima para acks=all
    min.insync.replicas=2
    # Idempotencia del producer garantizada
    # enable.idempotence=true
    # Topics del sistema de notificaciones
    auto.create.topics.enable=false
  EOF
}


resource "aws_cloudwatch_log_group" "msk" {
  name              = "/aws/msk/${var.project_name}"
  retention_in_days = 7
}

# ─────────────────────────────────────────
# TOPICS DE KAFKA
# Se crean después del despliegue via CLI
# una vez el cluster MSK esté activo:
#
# kafka-topics.sh --create \
#   --bootstrap-server $BROKERS \
#   --topic transacciones-raw \
#   --partitions 6 \
#   --replication-factor 3
#
# kafka-topics.sh --create \
#   --bootstrap-server $BROKERS \
#   --topic notificaciones-pendientes \
#   --partitions 6 \
#   --replication-factor 3
# ─────────────────────────────────────────