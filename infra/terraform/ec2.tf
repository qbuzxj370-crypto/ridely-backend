resource "aws_instance" "app" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = var.ec2_instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.ec2.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name
  key_name               = var.key_pair_name

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
  }

  # 시크릿 없음 — JDK/systemd unit/CloudWatch Agent 설치만. .env·app.jar는 apply 이후
  # infra/scripts/deploy.sh가 SCP로 전달한다 (infra/terraform/templates/user_data.sh.tpl).
  user_data = templatefile("${path.module}/templates/user_data.sh.tpl", {
    systemd_unit   = file("${path.module}/../systemd/ridely.service")
    log_group_name = aws_cloudwatch_log_group.app.name
  })

  tags = { Name = "${var.project_name}-app" }
}

resource "aws_eip" "app" {
  domain   = "vpc"
  instance = aws_instance.app.id

  tags = { Name = "${var.project_name}-app-eip" }
}
