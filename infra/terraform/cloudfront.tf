# 도메인·ACM 없이 CloudFront 기본 도메인(*.cloudfront.net)의 기본 인증서로 HTTPS를 해결한다.
# 오리진은 EC2(HTTP만, TLS 없음) — 뷰어<->CloudFront 구간만 HTTPS면 되고 CloudFront<->EC2는
# AWS 내부망이라 충분하다는 판단.

data "aws_cloudfront_cache_policy" "caching_disabled" {
  name = "Managed-CachingDisabled" # 동적 REST API라 캐시 자체를 금지 (JWT 응답을 캐시하면 안 됨)
}

data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  # Authorization 헤더·쿼리스트링은 그대로 전달하되 Host는 오리진(EC2) 것으로 재작성.
  # AllViewer를 쓰면 CloudFront 도메인이 Host로 넘어가 오리진에서 혼동을 일으킬 수 있다.
  name = "Managed-AllViewerExceptHostHeader"
}

resource "aws_cloudfront_distribution" "app" {
  enabled = true

  origin {
    domain_name = aws_eip.app.public_dns
    origin_id   = "${var.project_name}-ec2-origin"

    custom_origin_config {
      origin_protocol_policy = "http-only"
      http_port              = var.app_port
      https_port             = 443
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id       = "${var.project_name}-ec2-origin"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods         = ["GET", "HEAD"]

    cache_policy_id          = data.aws_cloudfront_cache_policy.caching_disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = { Name = "${var.project_name}-cdn" }
}
