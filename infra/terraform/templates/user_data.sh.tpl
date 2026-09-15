#!/bin/bash
# EC2 부팅 시 1회 실행. 시크릿은 여기 없다 — user-data는 인스턴스 메타데이터로 누구나
# (인스턴스 안에서) 조회 가능해서 절대 넣지 않는다. .env/jar는 apply 이후 deploy.sh가
# SCP로 별도 전달한다 (infra/scripts/deploy.sh).
set -euo pipefail

# --- JDK ---
# Corretto 25가 AL2023 리포에 없으면 21로 대체한다 (Spring Boot 3.5는 21도 지원).
if dnf list amazon-corretto-25 >/dev/null 2>&1; then
  dnf install -y amazon-corretto-25
else
  dnf install -y amazon-corretto-21
fi

# --- 앱 실행 계정/디렉터리 ---
useradd -r -m -d /opt/ridely -s /sbin/nologin ridely || true
mkdir -p /opt/ridely/logs
chown -R ridely:ridely /opt/ridely

# --- systemd unit ---
cat > /etc/systemd/system/ridely.service <<'UNIT'
${systemd_unit}
UNIT
systemctl daemon-reload
systemctl enable ridely
# .env/app.jar가 아직 없으므로 여기서 start는 하지 않는다 — deploy.sh가 최초 배포 후 시작한다.

# --- CloudWatch Agent ---
dnf install -y amazon-cloudwatch-agent
cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json <<JSON
{
  "logs": {
    "logs_collected": {
      "files": {
        "collect_list": [
          {
            "file_path": "/opt/ridely/logs/ridely.log",
            "log_group_name": "${log_group_name}",
            "log_stream_name": "{instance_id}"
          }
        ]
      }
    }
  }
}
JSON
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config -m ec2 -s \
  -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
