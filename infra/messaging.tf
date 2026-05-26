# ─────────────────────────────────────────
# SES — EMAIL
# ─────────────────────────────────────────

# Identidad de dominio para enviar emails
# IMPORTANTE: después de aplicar debes verificar el dominio en la consola SES
# resource "aws_ses_domain_identity" "main" {
#   domain = "notificaciones.tudominio.com"  # Cambia por tu dominio real
# }
#
# # Registro DNS para verificar el dominio (si usas Route53)
# # Si no usas Route53, agrega este registro TXT manualmente en tu DNS
# resource "aws_ses_domain_dkim" "main" {
#   domain = aws_ses_domain_identity.main.domain
# }
#
# # Configuración de envío — cuántos rebotes/quejas tolerar
# resource "aws_ses_configuration_set" "main" {
#   name = "${var.project_name}-ses-config"
#
#   delivery_options {
#     tls_policy = "Require"
#   }
# }
#
# # SNS topic para recibir notificaciones de rebotes de SES
# resource "aws_sns_topic" "ses_bounces" {
#   name = "${var.project_name}-ses-bounces"
# }
#
# resource "aws_ses_identity_notification_topic" "bounce" {
#   topic_arn                = aws_sns_topic.ses_bounces.arn
#   notification_type        = "Bounce"
#   identity                 = aws_ses_domain_identity.main.domain
#   include_original_headers = false
# }
#
# resource "aws_ses_identity_notification_topic" "complaint" {
#   topic_arn                = aws_sns_topic.ses_bounces.arn
#   notification_type        = "Complaint"
#   identity                 = aws_ses_domain_identity.main.domain
#   include_original_headers = false
# }

# ─────────────────────────────────────────
# SNS — SMS Y PUSH
# ─────────────────────────────────────────

# Topic para SMS transaccionales
resource "aws_sns_topic" "sms_notifications" {
  name = "${var.project_name}-sms-notifications"

  tags = { Environment = var.environment }
}

# Topic para Push notifications
resource "aws_sns_topic" "push_notifications" {
  name = "${var.project_name}-push-notifications"

  tags = { Environment = var.environment }
}

# # Configuración de SMS — modo transaccional (máxima confiabilidad)
# resource "aws_sns_sms_preferences" "main" {
#   default_sms_type        = "Transactional"  # vs Promotional
#   default_sender_id       = "Bancolombia"     # Cambia por tu sender id
#   monthly_spend_limit     = 100              # Límite en USD por mes
# }

# # Platform Application para Push — Android (FCM)
# # IMPORTANTE: necesitas las credenciales de Firebase
# resource "aws_sns_platform_application" "android" {
#   name     = "${var.project_name}-android"
#   platform = "GCM"
#   # Obtén esta clave en Firebase Console → Project Settings → Cloud Messaging
#   platform_credential = "TU_FCM_SERVER_KEY"
# }

# # Platform Application para Push — iOS (APNS)
# # IMPORTANTE: necesitas certificado de Apple Developer
# resource "aws_sns_platform_application" "ios" {
#   name     = "${var.project_name}-ios"
#   platform = "APNS"
#   # Obtén estos en Apple Developer Console
#   platform_credential = "TU_APNS_PRIVATE_KEY"
#   platform_principal  = "TU_APNS_CERTIFICATE"
# }

# ─────────────────────────────────────────
# SQS — COLAS DE RESPUESTA POR CANAL
# (Dead Letter Queues para fallos de envío)
# ─────────────────────────────────────────

resource "aws_sqs_queue" "email_dlq" {
  name                      = "${var.project_name}-email-dlq"
  message_retention_seconds = 86400
  tags                      = { Environment = var.environment }
}

resource "aws_sqs_queue" "sms_dlq" {
  name                      = "${var.project_name}-sms-dlq"
  message_retention_seconds = 86400
  tags                      = { Environment = var.environment }
}

resource "aws_sqs_queue" "push_dlq" {
  name                      = "${var.project_name}-push-dlq"
  message_retention_seconds = 86400
  tags                      = { Environment = var.environment }
}

resource "aws_sqs_queue" "email_response" {
  name                       = "${var.project_name}-email-response"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 86400

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.email_dlq.arn
    maxReceiveCount     = 3
  })

  tags = { Environment = var.environment }
}

resource "aws_sqs_queue" "sms_response" {
  name                       = "${var.project_name}-sms-response"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 86400

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.sms_dlq.arn
    maxReceiveCount     = 3
  })

  tags = { Environment = var.environment }
}

resource "aws_sqs_queue" "push_response" {
  name                       = "${var.project_name}-push-response"
  visibility_timeout_seconds = 30
  message_retention_seconds  = 86400

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.push_dlq.arn
    maxReceiveCount     = 3
  })

  tags = { Environment = var.environment }
}

# Suscripción SNS → SQS para recibir respuestas de envío
resource "aws_sns_topic_subscription" "sms_to_sqs" {
  topic_arn = aws_sns_topic.sms_notifications.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.sms_response.arn
}

resource "aws_sns_topic_subscription" "push_to_sqs" {
  topic_arn = aws_sns_topic.push_notifications.arn
  protocol  = "sqs"
  endpoint  = aws_sqs_queue.push_response.arn
}

# Permisos para que SNS pueda escribir en SQS
resource "aws_sqs_queue_policy" "sms_response" {
  queue_url = aws_sqs_queue.sms_response.url

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "sns.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.sms_response.arn
      Condition = {
        ArnEquals = {
          "aws:SourceArn" = aws_sns_topic.sms_notifications.arn
        }
      }
    }]
  })
}

resource "aws_sqs_queue_policy" "push_response" {
  queue_url = aws_sqs_queue.push_response.url

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "sns.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.push_response.arn
      Condition = {
        ArnEquals = {
          "aws:SourceArn" = aws_sns_topic.push_notifications.arn
        }
      }
    }]
  })
}

# ─────────────────────────────────────────
# CLOUDWATCH — MONITOREO Y ALERTAS
# ─────────────────────────────────────────

resource "aws_cloudwatch_log_group" "micro1" {
  name              = "/eks/${var.project_name}/micro1-idempotencia"
  retention_in_days = 14
}

resource "aws_cloudwatch_log_group" "micro2" {
  name              = "/eks/${var.project_name}/micro2-procesador"
  retention_in_days = 14
}

# Alerta: mensajes en DLQ por canal
resource "aws_cloudwatch_metric_alarm" "email_dlq_alarm" {
  alarm_name          = "${var.project_name}-email-dlq-mensajes"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Hay mensajes en la DLQ de email — revisar fallos de envío"

  dimensions = {
    QueueName = aws_sqs_queue.email_dlq.name
  }
}

resource "aws_cloudwatch_metric_alarm" "sms_dlq_alarm" {
  alarm_name          = "${var.project_name}-sms-dlq-mensajes"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  alarm_description   = "Hay mensajes en la DLQ de SMS — revisar fallos de envío"

  dimensions = {
    QueueName = aws_sqs_queue.sms_dlq.name
  }
}

# Dashboard de observabilidad completo
resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "${var.project_name}-dashboard"

  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        properties = {
          title   = "Mensajes DLQ por canal"
          region  = var.aws_region
          period  = 300
          view    = "timeSeries"
          stacked = false
          annotations = { horizontal = [] }
          metrics = [
            ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", aws_sqs_queue.email_dlq.name],
            ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", aws_sqs_queue.sms_dlq.name],
            ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", aws_sqs_queue.push_dlq.name]
          ]
        }
      },
      {
        type = "metric"
        properties = {
          title   = "Lag de consumidores Kafka"
          region  = var.aws_region
          period  = 60
          view    = "timeSeries"
          stacked = false
          annotations = { horizontal = [] }
          metrics = [
            ["AWS/Kafka", "EstimatedMaxTimeLag", "Cluster Name", aws_msk_cluster.notifications.cluster_name]
          ]
        }
      },
      {
        type = "metric"
        properties = {
          title   = "SMS enviados vs fallidos"
          region  = var.aws_region
          period  = 300
          view    = "timeSeries"
          stacked = false
          annotations = { horizontal = [] }
          metrics = [
            ["AWS/SNS", "NumberOfMessagesSent", "TopicName", aws_sns_topic.sms_notifications.name],
            ["AWS/SNS", "NumberOfMessagesFailed", "TopicName", aws_sns_topic.sms_notifications.name]
          ]
        }
      },
      {
        type = "metric"
        properties = {
          title   = "Push enviados vs fallidos"
          region  = var.aws_region
          period  = 300
          view    = "timeSeries"
          stacked = false
          annotations = { horizontal = [] }
          metrics = [
            ["AWS/SNS", "NumberOfMessagesSent", "TopicName", aws_sns_topic.push_notifications.name],
            ["AWS/SNS", "NumberOfMessagesFailed", "TopicName", aws_sns_topic.push_notifications.name]
          ]
        }
      }
    ]
  })
}
