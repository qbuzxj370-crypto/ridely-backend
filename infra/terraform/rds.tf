resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet-group"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "main" {
  identifier     = "${var.project_name}-db"
  engine         = "postgres"
  engine_version = var.rds_engine_version

  # postgis, vector(pgvector) 모두 RDS PostgreSQL 16의 허용 확장 목록에 기본 포함되어
  # shared_preload_libraries 조정이 필요 없다 — 커스텀 파라미터 그룹 생략, 마스터 계정으로
  # 앱 기동 시 Flyway V1이 CREATE EXTENSION IF NOT EXISTS postgis/vector를 그대로 실행한다.
  instance_class    = var.rds_instance_class
  allocated_storage = var.db_allocated_storage_gb
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  multi_az               = false # 심사 반려 사항 — 단일 인스턴스만
  publicly_accessible    = false
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  backup_retention_period = 3
  skip_final_snapshot     = true # 데모/개발용 — 운영 전환 시 false로 바꾸고 스냅샷 식별자 지정

  tags = { Name = "${var.project_name}-db" }
}
