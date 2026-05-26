# ─────────────────────────────────────────
# DOCUMENTDB — BASE DE DATOS NO RELACIONAL
# ─────────────────────────────────────────

resource "aws_security_group" "documentdb" {
  name   = "${var.project_name}-docdb-sg"
  vpc_id = aws_vpc.main.id

  ingress {
    from_port   = 27017
    to_port     = 27017
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
    description = "MongoDB compatible port"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-docdb-sg" }
}

resource "aws_docdb_subnet_group" "main" {
  name       = "${var.project_name}-docdb-subnet"
  subnet_ids = aws_subnet.private[*].id
}

# MASTER — escrituras (Micro 1)
resource "aws_docdb_cluster" "main" {
  cluster_identifier      = "${var.project_name}-docdb"
  engine                  = "docdb"
  master_username         = "notifadmin"
  master_password         = var.documentdb_password
  db_subnet_group_name    = aws_docdb_subnet_group.main.name
  vpc_security_group_ids  = [aws_security_group.documentdb.id]
  skip_final_snapshot     = false
  final_snapshot_identifier = "${var.project_name}-docdb-final"
  storage_encrypted       = true

  tags = { Environment = var.environment }
}

resource "aws_docdb_cluster_instance" "master" {
  identifier         = "${var.project_name}-docdb-master"
  cluster_identifier = aws_docdb_cluster.main.id
  instance_class     = "db.t3.medium"   # "db.r6g.large"
}

# SLAVE — lecturas (Micro 2: consulta preferencias y estado)
resource "aws_docdb_cluster_instance" "slave" {
  identifier         = "${var.project_name}-docdb-slave"
  cluster_identifier = aws_docdb_cluster.main.id
  instance_class     =  "db.t3.medium"      #  "db.r6g.large"
}

# ─────────────────────────────────────────
# ELASTICACHE REDIS — CACHE DE DUPLICADOS Y PREFERENCIAS
# ─────────────────────────────────────────

resource "aws_security_group" "redis" {
  name   = "${var.project_name}-redis-sg"
  vpc_id = aws_vpc.main.id

  ingress {
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-redis-sg" }
}

resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.project_name}-redis-subnet"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_elasticache_replication_group" "main" {
  replication_group_id = "${var.project_name}-redis"
  description          = "Cache de duplicados y preferencias"

  node_type               = "cache.t3.micro"      # "cache.r6g.large"
  num_cache_clusters      = 2          # primary + replica
  automatic_failover_enabled = true

  subnet_group_name    = aws_elasticache_subnet_group.main.name
  security_group_ids   = [aws_security_group.redis.id]

  # TTL manejado desde la aplicación (24h para duplicados)
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true

  tags = { Environment = var.environment }
}
