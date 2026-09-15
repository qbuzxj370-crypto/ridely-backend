resource "aws_security_group" "ec2" {
  name        = "${var.project_name}-ec2-sg"
  description = "CloudFront -> EC2 앱 포트, 배포자 -> SSH"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "CloudFront origin-facing IP만 앱 포트 접근 허용"
    from_port       = var.app_port
    to_port         = var.app_port
    protocol        = "tcp"
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.cloudfront_origin_facing.id]
  }

  ingress {
    description = "배포/운영용 SSH — 특정 CIDR로 좁혀서 사용"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.allowed_ssh_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-ec2-sg" }
}

resource "aws_security_group" "rds" {
  name        = "${var.project_name}-rds-sg"
  description = "EC2에서만 5432 접근"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "EC2 앱 서버에서만"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.project_name}-rds-sg" }
}
