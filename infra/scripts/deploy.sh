#!/bin/bash
# 사용법: infra/scripts/deploy.sh <EC2_PUBLIC_IP> [--with-env]
#   --with-env : .env 파일도 함께 전송 (최초 배포 시 1회, 이후 DB 비번 등이 안 바뀌면 생략)
#
# 전제: terraform apply 완료, .env 파일이 리포 루트에 준비됨(prod용 값으로 채움),
#       ~/.ssh에 key_pair_name에 대응하는 프라이빗 키가 있음.
set -euo pipefail

EC2_IP="${1:?사용법: deploy.sh <EC2_PUBLIC_IP> [--with-env]}"
WITH_ENV="${2:-}"
SSH_USER="ec2-user"
REMOTE_DIR="/opt/ridely"

cd "$(dirname "$0")/../.."

echo "==> 빌드"
./mvnw clean package -DskipTests

JAR_FILE=$(ls target/*.jar | grep -v sources | head -1)
echo "==> $JAR_FILE 전송"
scp "$JAR_FILE" "${SSH_USER}@${EC2_IP}:${REMOTE_DIR}/app.jar.tmp"
ssh "${SSH_USER}@${EC2_IP}" "sudo mv ${REMOTE_DIR}/app.jar.tmp ${REMOTE_DIR}/app.jar && sudo chown ridely:ridely ${REMOTE_DIR}/app.jar"

if [[ "$WITH_ENV" == "--with-env" ]]; then
  echo "==> .env 전송 (chmod 600)"
  scp .env "${SSH_USER}@${EC2_IP}:${REMOTE_DIR}/.env.tmp"
  ssh "${SSH_USER}@${EC2_IP}" "sudo mv ${REMOTE_DIR}/.env.tmp ${REMOTE_DIR}/.env && sudo chown ridely:ridely ${REMOTE_DIR}/.env && sudo chmod 600 ${REMOTE_DIR}/.env"
fi

echo "==> 서비스 재시작"
ssh "${SSH_USER}@${EC2_IP}" "sudo systemctl restart ridely && sleep 3 && sudo systemctl status ridely --no-pager"

echo "==> 헬스체크"
sleep 2
ssh "${SSH_USER}@${EC2_IP}" "curl -sf http://localhost:${APP_PORT:-8080}/api/v1/health && echo" || {
  echo "헬스체크 실패 — journalctl -u ridely -n 100 로 로그 확인"
  exit 1
}
