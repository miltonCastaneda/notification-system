output "eks_cluster_endpoint" {
  description = "Endpoint del cluster EKS"
  value       = aws_eks_cluster.main.endpoint
}

output "msk_bootstrap_brokers" {
  description = "Brokers de Kafka para conectar los microservicios"
  value       = aws_msk_cluster.notifications.bootstrap_brokers_tls
  sensitive   = true
}

output "documentdb_endpoint" {
  description = "Endpoint del DocumentDB (escritura - Master)"
  value       = aws_docdb_cluster.main.endpoint
}

output "documentdb_reader_endpoint" {
  description = "Endpoint del DocumentDB (lectura - Slave)"
  value       = aws_docdb_cluster.main.reader_endpoint
}

output "redis_primary_endpoint" {
  description = "Endpoint primario de Redis (escritura - cache duplicados)"
  value       = aws_elasticache_replication_group.main.primary_endpoint_address
}

output "redis_reader_endpoint" {
  description = "Endpoint de lectura de Redis (cache preferencias)"
  value       = aws_elasticache_replication_group.main.reader_endpoint_address
}

# output "ses_domain_identity" {
#   description = "Dominio verificado en SES para envío de emails"
#   value       = aws_ses_domain_identity.main.domain
# }
#
# output "ses_dkim_tokens" {
#   description = "Tokens DKIM para agregar en tu DNS"
#   value       = aws_ses_domain_dkim.main.dkim_tokens
# }

output "sns_sms_topic_arn" {
  description = "ARN del topic SNS para SMS"
  value       = aws_sns_topic.sms_notifications.arn
}

output "sns_push_topic_arn" {
  description = "ARN del topic SNS para Push"
  value       = aws_sns_topic.push_notifications.arn
}

output "ecr_micro1_url" {
  description = "URL del ECR para Micro 1 (idempotencia)"
  value       = aws_ecr_repository.micro1.repository_url
}

output "ecr_micro2_url" {
  description = "URL del ECR para Micro 2 (procesador de envío)"
  value       = aws_ecr_repository.micro2.repository_url
}

output "sqs_email_response_url" {
  description = "URL de la cola SQS de respuesta email"
  value       = aws_sqs_queue.email_response.url
}

output "sqs_sms_response_url" {
  description = "URL de la cola SQS de respuesta SMS"
  value       = aws_sqs_queue.sms_response.url
}

output "sqs_push_response_url" {
  description = "URL de la cola SQS de respuesta Push"
  value       = aws_sqs_queue.push_response.url
}
