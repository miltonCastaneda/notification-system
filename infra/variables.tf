variable "aws_region" {
  description = "Región de AWS"
  default     = "us-east-1"
}

variable "environment" {
  description = "Entorno (dev, staging, prod)"
  default     = "dev"
}

variable "project_name" {
  description = "Nombre del proyecto"
  default     = "notification-system"
}

variable "vpc_cidr" {
  description = "CIDR del VPC"
  default     = "10.0.0.0/16"
}

variable "documentdb_password" {
  description = "Password para DocumentDB"
  sensitive   = true
}

variable "kafka_broker_count" {
  description = "Número de brokers Kafka"
  default     = 3
}
