terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Guarda el estado en S3 (debes crear el bucket antes)
  backend "s3" {
      bucket = "notification-system-tfstate-938627482271"
      key    = "prod/terraform.tfstate"
      region = "us-east-1"
  }
}

provider "aws" {
  region = var.aws_region
}
