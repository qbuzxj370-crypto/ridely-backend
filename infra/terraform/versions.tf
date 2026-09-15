terraform {
  required_version = ">= 1.7.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# 인프라(EC2/RDS/S3/VPC)는 서울 — 한국 사용자 대상 서비스라 지연시간 최우선.
provider "aws" {
  region = var.aws_region
}

# Bedrock 호출 전용. Bedrock은 순수 API 호출이라 인프라와 같은 리전일 필요가 없고,
# 서울(ap-northeast-2)은 원하는 Claude 모델 가용성이 제한적일 수 있어 분리한다.
# IAM 정책의 리소스 ARN도 이 리전 기준으로 작성한다 (iam.tf).
provider "aws" {
  alias  = "bedrock"
  region = var.bedrock_region
}
