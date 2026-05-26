# ─────────────────────────────────────────
# EKS — CLUSTER KUBERNETES PARA MICROSERVICIOS
# ─────────────────────────────────────────

resource "aws_iam_role" "eks_cluster" {
  name = "${var.project_name}-eks-cluster-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "eks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "eks_cluster_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.eks_cluster.name
}

resource "aws_security_group" "eks" {
  name   = "${var.project_name}-eks-sg"
  vpc_id = aws_vpc.main.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-eks-sg" }
}

resource "aws_eks_cluster" "main" {
  name     = "${var.project_name}-eks"
  role_arn = aws_iam_role.eks_cluster.arn
  version  = "1.29"

  vpc_config {
    subnet_ids              = concat(aws_subnet.private[*].id, aws_subnet.public[*].id)
    security_group_ids      = [aws_security_group.eks.id]
    endpoint_private_access = true
    endpoint_public_access  = true
  }

  depends_on = [aws_iam_role_policy_attachment.eks_cluster_policy]

  tags = { Environment = var.environment }
}

# ─────────────────────────────────────────
# FARGATE — reemplaza EC2 node groups
# AWS administra las máquinas, tú solo defines
# cuánta CPU y memoria necesita cada contenedor
# ─────────────────────────────────────────

resource "aws_iam_role" "fargate" {
  name = "${var.project_name}-fargate-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "eks-fargate-pods.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "fargate_pod_execution" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSFargatePodExecutionRolePolicy"
  role       = aws_iam_role.fargate.name
}

# Fargate profile para los microservicios del sistema de notificaciones
resource "aws_eks_fargate_profile" "microservicios" {
  cluster_name           = aws_eks_cluster.main.name
  fargate_profile_name   = "${var.project_name}-microservicios"
  pod_execution_role_arn = aws_iam_role.fargate.arn

  # Fargate solo funciona en subnets privadas
  subnet_ids = aws_subnet.private[*].id

  # Selector: cualquier pod en el namespace "notificaciones"
  # será ejecutado en Fargate automáticamente
  selector {
    namespace = "notificaciones"
  }

  depends_on = [aws_iam_role_policy_attachment.fargate_pod_execution]

  tags = { Environment = var.environment }
}

# Fargate profile para el sistema kube-system (CoreDNS)
# Necesario para que el DNS interno de Kubernetes funcione en Fargate
resource "aws_eks_fargate_profile" "kube_system" {
  cluster_name           = aws_eks_cluster.main.name
  fargate_profile_name   = "${var.project_name}-kube-system"
  pod_execution_role_arn = aws_iam_role.fargate.arn
  subnet_ids             = aws_subnet.private[*].id

  selector {
    namespace = "kube-system"
  }

  depends_on = [aws_iam_role_policy_attachment.fargate_pod_execution]

  tags = { Environment = var.environment }
}

# ECR — Registry para las imágenes Docker de los microservicios
resource "aws_ecr_repository" "micro1" {
  name                 = "${var.project_name}/micro1-idempotencia"
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration { scan_on_push = true }
}

resource "aws_ecr_repository" "micro2" {
  name                 = "${var.project_name}/micro2-procesador"
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration { scan_on_push = true }
}

resource "aws_ecr_repository" "micro_preferencias" {
  name                 = "${var.project_name}/micro-preferencias"
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration { scan_on_push = true }
}
