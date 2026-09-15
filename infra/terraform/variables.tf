variable "project_name" {
  description = "리소스 이름 접두어"
  type        = string
  default     = "ridely"
}

variable "aws_region" {
  description = "EC2/RDS/S3/VPC가 위치할 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "bedrock_region" {
  description = "Bedrock 모델 호출 리전. 서울은 원하는 Claude 모델 가용성이 제한적일 수 있어 분리"
  type        = string
  default     = "us-east-1"
}

variable "bedrock_model_id" {
  description = "Bedrock 크로스 리전 추론 프로파일 ID (베어 모델 ID는 온디맨드 처리량 미지원)"
  type        = string
  default     = "us.anthropic.claude-sonnet-5"
}

variable "ec2_instance_type" {
  type    = string
  default = "t3.micro"
}

variable "rds_instance_class" {
  type    = string
  default = "db.t3.micro"
}

variable "rds_engine_version" {
  description = "postgis(3.4+)·vector(pgvector) 확장을 지원하는 PostgreSQL 16.x"
  type        = string
  default     = "16.4"
}

variable "db_name" {
  type    = string
  default = "ridely"
}

variable "db_username" {
  type    = string
  default = "ridely"
}

variable "db_password" {
  description = <<-EOT
    Secrets Manager를 안 쓰기로 했으므로(심사 반려) 기본값을 두지 않는다.
    apply 시 -var 또는 TF_VAR_db_password 환경변수로 전달 — .tfvars 파일에 적지 말 것.
    ⚠️ sensitive여도 Terraform state 파일 자체에는 평문으로 남는다. state는 커밋하지 말고
    (.gitignore에 *.tfstate* 필수) 로컬에만 보관하거나 암호화된 원격 백엔드를 쓴다.
  EOT
  type        = string
  sensitive   = true
}

variable "db_allocated_storage_gb" {
  type    = number
  default = 20
}

variable "allowed_ssh_cidr" {
  description = "22번 포트 접근을 허용할 CIDR (배포자 IP로 좁혀서 사용)"
  type        = string
}

variable "key_pair_name" {
  description = "EC2 SSH 접속용 기존 키페어 이름 (콘솔/CLI로 미리 생성)"
  type        = string
}

variable "app_port" {
  type    = number
  default = 8080
}

variable "log_retention_days" {
  type    = number
  default = 14
}
