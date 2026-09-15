output "cloudfront_domain" {
  description = "CORS_ALLOWED_ORIGINS와 앱/코르도바 클라이언트가 호출할 기본 HTTPS 엔드포인트"
  value       = "https://${aws_cloudfront_distribution.app.domain_name}"
}

output "ec2_public_ip" {
  description = "SSH·deploy.sh 대상 (Elastic IP)"
  value       = aws_eip.app.public_ip
}

output "rds_endpoint" {
  description = "DB_URL 구성용 (비밀번호는 별도 관리)"
  value       = aws_db_instance.main.endpoint
  sensitive   = true
}

output "s3_bucket_name" {
  value = aws_s3_bucket.uploads.bucket
}

output "cloudwatch_log_group" {
  value = aws_cloudwatch_log_group.app.name
}
