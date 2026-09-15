resource "aws_iam_role" "ec2" {
  name = "${var.project_name}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# Secrets Manager 없이 가기로 했으므로(심사 반려), EC2가 AWS 안에서 필요로 하는 건
# Bedrock 호출 + S3 접근 + CloudWatch 로그 전송 3가지뿐이다. DB 비밀번호·JWT_SECRET·
# 3rd-party API 키는 이 역할과 무관하게 .env 파일로 배포된다.
resource "aws_iam_role_policy" "ec2" {
  name = "${var.project_name}-ec2-policy"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "BedrockInvoke"
        Effect = "Allow"
        Action = [
          "bedrock:InvokeModel",
          "bedrock:InvokeModelWithResponseStream"
        ]
        # 크로스 리전 추론 프로파일 + 밑단 파운데이션 모델 양쪽에 권한을 준다.
        # 프로파일 ARN만으로 AccessDenied가 나면 파운데이션 모델 쪽 리전을 넓혀야 할 수 있다.
        Resource = [
          "arn:aws:bedrock:${var.bedrock_region}:${data.aws_caller_identity.current.account_id}:inference-profile/${var.bedrock_model_id}",
          "arn:aws:bedrock:*::foundation-model/anthropic.claude-sonnet*"
        ]
      },
      {
        Sid      = "S3Access"
        Effect   = "Allow"
        Action   = ["s3:GetObject", "s3:PutObject"]
        Resource = "${aws_s3_bucket.uploads.arn}/*"
      },
      {
        Sid    = "CloudWatchLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "${aws_cloudwatch_log_group.app.arn}:*"
      }
    ]
  })
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${var.project_name}-ec2-profile"
  role = aws_iam_role.ec2.name
}
